package com.colofabrix.scala.beesight.collections

import breeze.linalg.DenseVector
import cats.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*

type NEW[A] = NonEmptyWindow[A]
val NEW = NonEmptyWindow

/**
 * A non-empty sliding window - guaranteed to have at least one element.
 *
 * Starts as `NonEmptyFilling` (partially filled) and transitions to `NonEmptyFilled`
 * once capacity is reached. This is a self-contained implementation without wrappers.
 */
sealed trait NonEmptyWindow[A] {

  // ── Capacity & Size ────────────────────────────────────────────────────────

  def capacity: Int
  def length: Int
  def isAtCapacity: Boolean

  // ── Accessors ──────────────────────────────────────────────────────────────

  def toVector: Vector[A]
  def toDenseVector(f: A => Double): DenseVector[Double]
  def oldest: A
  def newest: A

  // ── Mutations ──────────────────────────────────────────────────────────────

  def enqueue(a: A): NonEmptyWindow[A]
  def push(a: A): (Option[A], NonEmptyWindow[A])

  // ── Subwindows ─────────────────────────────────────────────────────────────

  def takeNewest(i: Int): NonEmptyFilled[A]

  // ── Focus (Zipper) ─────────────────────────────────────────────────────────

  def focus: A
  def focusIndex: Int
  def focusAt(index: Int): NonEmptyWindow[A]

  // ── Transformations ────────────────────────────────────────────────────────

  def map[B](f: A => B): NonEmptyWindow[B]
  def foldLeft[B](z: B)(f: (B, A) => B): B
  def foldRight[B](z: B)(f: (A, B) => B): B

}

object NonEmptyWindow {

  /**
   * A non-empty window that is still being filled (1 ≤ length < capacity).
   */
  final class NonEmptyFilling[A] private[NonEmptyWindow] (
    private val buffer: Array[Any],
    private val head: Int,
    private val len: Int,
    private val focusIdx: Int,
    val capacity: Int,
  ) extends NonEmptyWindow[A] {

    // ── Capacity & Size ──────────────────────────────────────────────────────

    def length: Int =
      len

    def isAtCapacity: Boolean =
      len == capacity

    // ── Accessors ────────────────────────────────────────────────────────────

    def toVector: Vector[A] =
      val builder = Vector.newBuilder[A]
      var i       = 0
      while i < len do
        builder += buffer((head + i) % capacity).asInstanceOf[A]
        i       += 1
      builder.result()

    def toDenseVector(f: A => Double): DenseVector[Double] =
      val result = new Array[Double](len)
      var i      = 0
      while i < len do
        result(i) = f(buffer((head + i) % capacity).asInstanceOf[A])
        i += 1
      DenseVector(result)

    def oldest: A =
      buffer(head).asInstanceOf[A]

    def newest: A =
      buffer((head + len - 1) % capacity).asInstanceOf[A]

    // ── Mutations ────────────────────────────────────────────────────────────

    def enqueue(a: A): NonEmptyWindow[A] =
      push(a)._2

    def push(a: A): (Option[A], NonEmptyWindow[A]) =
      val newLen = len + 1
      val tail   = (head + len) % capacity
      buffer(tail) = a

      if newLen >= capacity then
        val newFocusIdx = Math.min(focusIdx, capacity - 1)
        (None, new NonEmptyFilled(buffer.clone(), head, newFocusIdx, capacity))
      else
        (None, new NonEmptyFilling(buffer, head, newLen, focusIdx, capacity))

    // ── Subwindows ───────────────────────────────────────────────────────────

    def takeNewest(i: Int): NonEmptyFilled[A] =
      val count     = Math.max(1, Math.min(i, len))
      val start     = len - count
      val newBuffer = new Array[Any](count)
      var idx       = 0
      while idx < count do
        newBuffer(idx) = buffer((head + start + idx) % capacity)
        idx += 1
      new NonEmptyFilled(newBuffer, 0, count - 1, count)

    // ── Focus (Zipper) ───────────────────────────────────────────────────────

    def focus: A =
      buffer((head + focusIdx) % capacity).asInstanceOf[A]

    def focusIndex: Int =
      focusIdx

    def focusAt(index: Int): NonEmptyFilling[A] =
      val clipped = Math.max(0, Math.min(index, len - 1))
      new NonEmptyFilling(buffer, head, len, clipped, capacity)

    // ── Transformations ──────────────────────────────────────────────────────

    def map[B](f: A => B): NonEmptyFilling[B] =
      val newBuffer = new Array[Any](capacity)
      var i         = 0
      while i < len do
        newBuffer(i) = f(buffer((head + i) % capacity).asInstanceOf[A])
        i += 1
      new NonEmptyFilling(newBuffer, 0, len, focusIdx, capacity)

    def foldLeft[B](z: B)(f: (B, A) => B): B =
      var acc = z
      var i   = 0
      while i < len do
        acc = f(acc, buffer((head + i) % capacity).asInstanceOf[A])
        i += 1
      acc

    def foldRight[B](z: B)(f: (A, B) => B): B =
      var acc = z
      var i   = len - 1
      while i >= 0 do
        acc = f(buffer((head + i) % capacity).asInstanceOf[A], acc)
        i -= 1
      acc

    override def toString: String =
      val vec      = toVector
      val elements = vec.zipWithIndex.map { case (e, i) => if i == focusIdx then s"[$e]" else e.toString }.mkString(", ")
      s"NonEmptyFilling[$len/$capacity]($elements)"

  }

  /**
   * A non-empty window that has reached capacity (length == capacity).
   */
  final class NonEmptyFilled[A] private[NonEmptyWindow] (
    private val buffer: Array[Any],
    private val head: Int,
    private val focusIdx: Int,
    val capacity: Int,
  ) extends NonEmptyWindow[A] {

    // ── Capacity & Size ──────────────────────────────────────────────────────

    def length: Int =
      capacity

    def isAtCapacity: Boolean =
      true

    // ── Accessors ────────────────────────────────────────────────────────────

    def toVector: Vector[A] =
      val builder = Vector.newBuilder[A]
      var i       = 0
      while i < capacity do
        builder += buffer((head + i) % capacity).asInstanceOf[A]
        i       += 1
      builder.result()

    def toDenseVector(f: A => Double): DenseVector[Double] =
      val result = new Array[Double](capacity)
      var i      = 0
      while i < capacity do
        result(i) = f(buffer((head + i) % capacity).asInstanceOf[A])
        i += 1
      DenseVector(result)

    def oldest: A =
      buffer(head).asInstanceOf[A]

    def newest: A =
      buffer((head + capacity - 1) % capacity).asInstanceOf[A]

    // ── Mutations ────────────────────────────────────────────────────────────

    def enqueue(a: A): NonEmptyFilled[A] =
      pushReturning(a)._2

    def push(a: A): (Option[A], NonEmptyFilled[A]) =
      val (out, window) = pushReturning(a)
      (Some(out), window)

    def pushReturning(a: A): (A, NonEmptyFilled[A]) =
      val outgoing  = buffer(head).asInstanceOf[A]
      val newBuffer = buffer.clone()
      newBuffer(head) = a
      val newHead     = (head + 1) % capacity
      val newFocusIdx = Math.max(0, focusIdx - 1)
      (outgoing, new NonEmptyFilled(newBuffer, newHead, newFocusIdx, capacity))

    // ── Subwindows ───────────────────────────────────────────────────────────

    def takeNewest(i: Int): NonEmptyFilled[A] =
      val count = Math.max(1, Math.min(i, capacity))
      val start = capacity - count
      filledSubwindow(start, count)

    def filledSubwindow(start: Int, count: Int): NonEmptyFilled[A] =
      val sliceLen =
        if count >= 0 then Math.min(count, capacity - start)
        else Math.min(-count, start + 1)

      val safeLen    = Math.max(1, sliceLen)
      val sliceStart = if count >= 0 then start else start - safeLen + 1
      val newBuffer  = new Array[Any](safeLen)
      var i          = 0
      while i < safeLen do
        newBuffer(i) = buffer((head + sliceStart + i) % capacity)
        i += 1
      new NonEmptyFilled(newBuffer, 0, safeLen - 1, safeLen)

    def filledSubwindow(count: Int): NonEmptyFilled[A] =
      filledSubwindow(focusIdx, count)

    // ── Focus (Zipper) ───────────────────────────────────────────────────────

    def focus: A =
      buffer((head + focusIdx) % capacity).asInstanceOf[A]

    def focusIndex: Int =
      focusIdx

    def focusAt(index: Int): NonEmptyFilled[A] =
      val clipped = Math.max(0, Math.min(index, capacity - 1))
      new NonEmptyFilled(buffer, head, clipped, capacity)

    def modifyFocus(f: A => A): NonEmptyFilled[A] =
      val newBuffer = buffer.clone()
      val idx       = (head + focusIdx) % capacity
      newBuffer(idx) = f(buffer(idx).asInstanceOf[A])
      new NonEmptyFilled(newBuffer, head, focusIdx, capacity)

    def left: Option[NonEmptyFilled[A]] =
      if focusIdx > 0 then Some(filledSubwindow(0, focusIdx))
      else None

    def right: Option[NonEmptyFilled[A]] =
      if focusIdx < capacity - 1 then Some(filledSubwindow(focusIdx + 1, capacity - focusIdx - 1))
      else None

    def leftInclusive: NonEmptyFilled[A] =
      filledSubwindow(0, focusIdx + 1)

    def rightInclusive: NonEmptyFilled[A] =
      filledSubwindow(focusIdx, capacity - focusIdx)

    // ── Transformations ──────────────────────────────────────────────────────

    def map[B](f: A => B): NonEmptyFilled[B] =
      val newBuffer = new Array[Any](capacity)
      var i         = 0
      while i < capacity do
        newBuffer(i) = f(buffer((head + i) % capacity).asInstanceOf[A])
        i += 1
      new NonEmptyFilled(newBuffer, 0, focusIdx, capacity)

    def foldLeft[B](z: B)(f: (B, A) => B): B =
      var acc = z
      var i   = 0
      while i < capacity do
        acc = f(acc, buffer((head + i) % capacity).asInstanceOf[A])
        i += 1
      acc

    def foldRight[B](z: B)(f: (A, B) => B): B =
      var acc = z
      var i   = capacity - 1
      while i >= 0 do
        acc = f(buffer((head + i) % capacity).asInstanceOf[A], acc)
        i -= 1
      acc

    override def toString: String =
      val vec      = toVector
      val elements = vec.zipWithIndex.map { case (e, i) => if i == focusIdx then s"[$e]" else e.toString }.mkString(", ")
      s"NonEmptyFilled[$capacity]($elements)"

  }

  // ── Factory Methods ────────────────────────────────────────────────────────

  def apply[A](capacity: Int, first: A): NonEmptyWindow[A] =
    val cap    = Math.max(1, capacity)
    val buffer = new Array[Any](cap)
    buffer(0) = first

    if cap == 1 then new NonEmptyFilled(buffer, 0, 0, cap)
    else new NonEmptyFilling(buffer, 0, 1, 0, cap)

  def fromFilled[A](filled: NonEmptyFilled[A]): NonEmptyFilled[A] =
    filled

  // ── Type Class Instances ───────────────────────────────────────────────────

  given Functor[NonEmptyWindow] with
    def map[A, B](fa: NonEmptyWindow[A])(f: A => B): NonEmptyWindow[B] =
      fa.map(f)

  given Traverse[NonEmptyWindow] with {

    def traverse[G[_]: Applicative, A, B](fa: NonEmptyWindow[A])(f: A => G[B]): G[NonEmptyWindow[B]] =
      val G   = Applicative[G]
      var acc = G.pure(Vector.newBuilder[B])

      var i = 0
      while i < fa.length do
        val elem = fa.toVector(i)
        acc = G.map2(acc, f(elem))((builder, b) => builder += b)
        i += 1

      G.map(acc) { builder =>
        val vec       = builder.result()
        val newBuffer = new Array[Any](fa.capacity)
        val _         = vec.copyToArray(newBuffer)

        fa match {
          case filling: NonEmptyFilling[A] =>
            new NonEmptyFilling[B](newBuffer, 0, vec.length, filling.focusIndex, fa.capacity)
          case filled: NonEmptyFilled[A] =>
            new NonEmptyFilled[B](newBuffer, 0, filled.focusIndex, fa.capacity)
        }
      }

    def foldLeft[A, B](fa: NonEmptyWindow[A], b: B)(f: (B, A) => B): B =
      fa.foldLeft(b)(f)

    def foldRight[A, B](fa: NonEmptyWindow[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      Eval.defer {
        fa.foldRight(lb)((a, evalB) => f(a, evalB))
      }

  }

}
