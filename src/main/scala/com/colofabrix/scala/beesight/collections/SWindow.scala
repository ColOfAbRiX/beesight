package com.colofabrix.scala.beesight.collections

import breeze.linalg.DenseVector
import cats.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.SWindow.*

/**
 * A fixed-capacity sliding window that transitions between filling and filled states.
 */
sealed trait SWindow[A] {

  //  Capacity & Size  //

  def capacity: Int
  def length: Int
  def isAtCapacity: Boolean
  def isEmpty: Boolean

  //  Accessors  //

  def toVector: Vector[A]
  def toDenseVector(f: A => Double): DenseVector[Double]

  //  Mutations  //

  def enqueue(a: A): SWindow[A]
  def push(a: A): (Option[A], SWindow[A])

  //  Subwindows & Conversions  //

  def filledSubwindow(start: Int, count: Int): Option[FilledWindow[A]]
  def filledSubwindow(count: Int): Option[FilledWindow[A]]

  //  Transformations  //

  def map[B](f: A => B): SWindow[B]
  def foldLeft[B](z: B)(f: (B, A) => B): B
  def foldRight[B](z: B)(f: (A, B) => B): B

}

object SWindow {

  /**
   * A window that is still being filled (has not yet reached capacity).
   */
  final class FillingWindow[A] private[SWindow] (
    private val buffer: Array[Any],
    private val head: Int,
    private val len: Int,
    val capacity: Int,
  ) extends SWindow[A] {

    //  Capacity & Size  //

    def length: Int =
      len

    def isEmpty: Boolean =
      len == 0

    def isAtCapacity: Boolean =
      len === capacity

    //  Accessors  //

    def toVector: Vector[A] =
      val builder = Vector.newBuilder[A]

      var i = 0
      while i < len do
        builder += buffer((head + i) % capacity).asInstanceOf[A]
        i       += 1

      builder.result()

    def oldestOption: Option[A] =
      if len > 0 then Some(buffer(head).asInstanceOf[A]) else None

    def newestOption: Option[A] =
      if len > 0 then Some(buffer((head + len - 1) % capacity).asInstanceOf[A]) else None

    def toDenseVector(f: A => Double): DenseVector[Double] =
      val result = new Array[Double](len)

      var i = 0
      while i < len do
        result(i) = f(buffer((head + i) % capacity).asInstanceOf[A])
        i += 1

      DenseVector(result)

    //  Mutations  //

    def enqueue(a: A): SWindow[A] =
      push(a)._2

    def push(a: A): (Option[A], SWindow[A]) =
      val newLen = len + 1
      val tail   = (head + len) % capacity
      buffer(tail) = a

      if newLen >= capacity then
        val focusIdx = capacity - 1
        (None, new FilledWindow(buffer.clone(), head, focusIdx, capacity))
      else
        (None, new FillingWindow(buffer, head, newLen, capacity))

    //  Subwindows & Conversions  //

    def filledSubwindow(start: Int, count: Int): Option[FilledWindow[A]] =
      val sliceLen =
        if count >= 0 then Math.min(count, len - start)
        else Math.min(-count, start + 1)

      if sliceLen <= 0 || start < 0 || start >= len then
        None
      else
        val sliceStart = if count >= 0 then start else start - sliceLen + 1
        val newBuffer  = new Array[Any](sliceLen)

        var i = 0
        while i < sliceLen do
          newBuffer(i) = buffer((head + sliceStart + i) % capacity)
          i += 1

        Some(new FilledWindow(newBuffer, 0, sliceLen - 1, sliceLen))

    def filledSubwindow(count: Int): Option[FilledWindow[A]] =
      None // FillingWindow has no focus

    def asFilledWindow: Option[FilledWindow[A]] =
      Option.when(isAtCapacity) {
        new FilledWindow[A](buffer, head, 0, capacity)
      }

    //  Transformations  //

    def map[B](f: A => B): FillingWindow[B] =
      val newBuffer = new Array[Any](capacity)

      var i = 0
      while i < len do
        newBuffer(i) = f(buffer((head + i) % capacity).asInstanceOf[A])
        i += 1

      new FillingWindow(newBuffer, 0, len, capacity)

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

  }

  /**
   * A window that has reached capacity and supports zipper-like focus operations.
   */
  final class FilledWindow[A] private[SWindow] (
    private val buffer: Array[Any],
    private val head: Int,
    private val focusIdx: Int,
    val capacity: Int,
  ) extends SWindow[A] {

    //  Capacity & Size  //

    def length: Int =
      capacity

    def isEmpty: Boolean =
      false

    def isAtCapacity: Boolean =
      true

    //  Accessors  //

    def toVector: Vector[A] =
      val builder = Vector.newBuilder[A]

      var i = 0
      while i < capacity do
        builder += buffer((head + i) % capacity).asInstanceOf[A]
        i       += 1

      builder.result()

    def oldest: A =
      buffer(head).asInstanceOf[A]

    def newest: A =
      buffer((head + capacity - 1) % capacity).asInstanceOf[A]

    def toDenseVector(f: A => Double): DenseVector[Double] =
      val result = new Array[Double](capacity)

      var i = 0
      while i < capacity do
        result(i) = f(buffer((head + i) % capacity).asInstanceOf[A])
        i += 1

      DenseVector(result)

    //  Mutations  //

    def enqueue(a: A): SWindow[A] =
      push(a)._2

    def push(a: A): (Option[A], SWindow[A]) =
      val outgoing  = buffer(head).asInstanceOf[A]
      val newBuffer = buffer.clone()
      newBuffer(head) = a
      val newHead     = (head + 1) % capacity
      val newFocusIdx = Math.max(0, focusIdx - 1)
      (Some(outgoing), new FilledWindow(newBuffer, newHead, newFocusIdx, capacity))

    def pushReturning(a: A): (A, FilledWindow[A]) =
      val (Some(out), window) = push(a): @unchecked
      (out, window.asInstanceOf[FilledWindow[A]])

    //  Subwindows & Conversions  //

    def filledSubwindow(start: Int, count: Int): Option[FilledWindow[A]] =
      val sliceLen =
        if count >= 0 then Math.min(count, capacity - start)
        else Math.min(-count, start + 1)

      if sliceLen <= 0 || start < 0 || start >= capacity then
        None
      else
        val sliceStart = if count >= 0 then start else start - sliceLen + 1
        val newBuffer  = new Array[Any](sliceLen)

        var i = 0
        while i < sliceLen do
          newBuffer(i) = buffer((head + sliceStart + i) % capacity)
          i += 1

        Some(new FilledWindow(newBuffer, 0, sliceLen - 1, sliceLen))

    def filledSubwindow(count: Int): Option[FilledWindow[A]] =
      filledSubwindow(focusIdx, count)

    //  Focus (Zipper)  //

    def focus: A =
      buffer((head + focusIdx) % capacity).asInstanceOf[A]

    def focusIndex: Int =
      focusIdx

    def focusAt(index: Int): FilledWindow[A] =
      val clipped = Math.max(0, Math.min(index, capacity - 1))
      new FilledWindow(buffer, head, clipped, capacity)

    def modifyFocus(f: A => A): FilledWindow[A] =
      val newBuffer = buffer.clone()
      val idx       = (head + focusIdx) % capacity
      newBuffer(idx) = f(buffer(idx).asInstanceOf[A])
      new FilledWindow(newBuffer, head, focusIdx, capacity)

    def left: Option[FilledWindow[A]] =
      filledSubwindow(0, focusIdx)

    def right: Option[FilledWindow[A]] =
      filledSubwindow(focusIdx + 1, capacity - focusIdx - 1)

    def leftInclusive: FilledWindow[A] =
      filledSubwindow(0, focusIdx + 1).get

    def rightInclusive: FilledWindow[A] =
      filledSubwindow(focusIdx, capacity - focusIdx).get

    //  Transformations  //

    def map[B](f: A => B): FilledWindow[B] =
      val newBuffer = new Array[Any](capacity)

      var i = 0
      while i < capacity do
        newBuffer(i) = f(buffer((head + i) % capacity).asInstanceOf[A])
        i += 1

      new FilledWindow(newBuffer, 0, focusIdx, capacity)

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

  }

  //  Factory Methods  //

  def apply[A](capacity: Int): SWindow[A] =
    val cap = Math.max(1, capacity)
    new FillingWindow(new Array[Any](cap), 0, 0, cap)

  def empty[A]: SWindow[A] =
    new FillingWindow(new Array[Any](1), 0, 0, 1)

  //  Type Class Instances  //

  given Functor[SWindow] with
    def map[A, B](fa: SWindow[A])(f: A => B): SWindow[B] =
      fa.map(f)

  given Traverse[SWindow] with {

    def traverse[G[_]: Applicative, A, B](fa: SWindow[A])(f: A => G[B]): G[SWindow[B]] =
      val G   = Applicative[G]
      var acc = G.pure(Vector.newBuilder[B])

      var i = 0
      while i < fa.length do
        val elem = fa.toVector(i)
        acc = G.map2(acc, f(elem))((builder, b) => builder += b)
        i += 1

      G.map(acc)(builder =>
        fa.map(_ => null.asInstanceOf[B]) match {
          case filling: FillingWindow[B] =>
            val vec       = builder.result()
            val newBuffer = new Array[Any](fa.capacity)
            val _         = vec.copyToArray(newBuffer)
            new FillingWindow[B](newBuffer, 0, vec.length, fa.capacity)
          case filled: FilledWindow[B] =>
            val vec       = builder.result()
            val newBuffer = new Array[Any](fa.capacity)
            val _         = vec.copyToArray(newBuffer)
            new FilledWindow[B](newBuffer, 0, filled.focusIndex, fa.capacity)
        },
      )

    def foldLeft[A, B](fa: SWindow[A], b: B)(f: (B, A) => B): B =
      fa.foldLeft(b)(f)

    def foldRight[A, B](fa: SWindow[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      Eval.defer {
        fa.foldRight(lb)((a, evalB) => f(a, evalB))
      }

  }

}
