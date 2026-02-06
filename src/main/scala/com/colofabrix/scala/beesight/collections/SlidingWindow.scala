package com.colofabrix.scala.beesight.collections

import breeze.linalg.DenseVector
import cats.*
import scala.reflect.ClassTag

/**
 * A fixed-capacity sliding window that transitions between filling and filled states.
 */
sealed trait SlidingWindow[A] {
  def capacity: Int
  def length: Int
  def toVector: Vector[A]
  def enqueue(a: A): SlidingWindow[A]
  def push(a: A): (Option[A], SlidingWindow[A])
  def sliceFilled(start: Int, count: Int): Option[SlidingWindow.FilledWindow[A]]

  def toList: List[A] =
    toVector.toList

  def toDenseVector[B >: A: ClassTag]: DenseVector[B] =
    DenseVector(toVector.toArray[B])
}

object SlidingWindow {

  /**
   * A window that is still being filled (has not yet reached capacity).
   */
  final class FillingWindow[A] private[SlidingWindow] (
    private val buffer: Array[Any],
    private val head: Int,
    private val len: Int,
    val capacity: Int,
  ) extends SlidingWindow[A] {

    def enqueue(a: A): SlidingWindow[A] =
      push(a)._2

    def push(a: A): (Option[A], SlidingWindow[A]) =
      val newLen = len + 1
      val tail   = (head + len) % capacity
      buffer(tail) = a

      if newLen >= capacity then
        val focusIdx = capacity - 1
        (None, new FilledWindow(buffer.clone(), head, focusIdx, capacity))
      else
        (None, new FillingWindow(buffer, head, newLen, capacity))

    def sliceFilled(start: Int, count: Int): Option[FilledWindow[A]] =
      val sliceLen =
        if count >= 0 then Math.min(count, len - start)
        else Math.min(-count, start + 1)

      if sliceLen <= 0 || start < 0 || start >= len then
        None
      else
        val sliceStart = if count >= 0 then start else start + count + 1
        val newBuffer  = new Array[Any](sliceLen)

        var i = 0
        while i < sliceLen do
          newBuffer(i) = buffer((head + sliceStart + i) % capacity)
          i += 1

        Some(new FilledWindow(newBuffer, 0, sliceLen - 1, sliceLen))

    def length: Int =
      len

    def isEmpty: Boolean =
      len == 0

    def toVector: Vector[A] =
      val builder = Vector.newBuilder[A]

      var i = 0
      while i < len do
        builder += buffer((head + i) % capacity).asInstanceOf[A]
        i       += 1

      builder.result()

  }

  /**
   * A window that has reached capacity and supports zipper-like focus operations.
   */
  final class FilledWindow[A] private[SlidingWindow] (
    private val buffer: Array[Any],
    private val head: Int,
    private val focusIdx: Int,
    val capacity: Int,
  ) extends SlidingWindow[A] {

    def enqueue(a: A): SlidingWindow[A] =
      push(a)._2

    def push(a: A): (Option[A], SlidingWindow[A]) =
      val outgoing  = buffer(head).asInstanceOf[A]
      val newBuffer = buffer.clone()
      newBuffer(head) = a
      val newHead     = (head + 1) % capacity
      val newFocusIdx = Math.max(0, focusIdx - 1)
      (Some(outgoing), new FilledWindow(newBuffer, newHead, newFocusIdx, capacity))

    def pushReturning(a: A): (A, FilledWindow[A]) =
      val (Some(out), window) = push(a): @unchecked
      (out, window.asInstanceOf[FilledWindow[A]])

    def sliceFilled(start: Int, count: Int): Option[FilledWindow[A]] =
      val sliceLen =
        if count >= 0 then Math.min(count, capacity - start)
        else Math.min(-count, start + 1)

      if sliceLen <= 0 || start < 0 || start >= capacity then
        None
      else
        val sliceStart = if count >= 0 then start else start + count + 1
        val newBuffer  = new Array[Any](sliceLen)

        var i = 0
        while i < sliceLen do
          newBuffer(i) = buffer((head + sliceStart + i) % capacity)
          i += 1

        Some(new FilledWindow(newBuffer, 0, sliceLen - 1, sliceLen))

    def length: Int =
      capacity

    def toVector: Vector[A] = {
      val builder = Vector.newBuilder[A]

      var i = 0
      while i < capacity do
        builder += buffer((head + i) % capacity).asInstanceOf[A]
        i       += 1

      builder.result()
    }

    def oldest: A =
      buffer(head).asInstanceOf[A]

    def newest: A =
      buffer((head + capacity - 1) % capacity).asInstanceOf[A]

    def focus: A =
      buffer((head + focusIdx) % capacity).asInstanceOf[A]

    def left: Vector[A] = {
      val builder = Vector.newBuilder[A]

      var i = 0
      while i < focusIdx do
        builder += buffer((head + i) % capacity).asInstanceOf[A]
        i       += 1

      builder.result()
    }

    def right: Vector[A] = {
      val builder = Vector.newBuilder[A]

      var i = focusIdx + 1
      while i < capacity do
        builder += buffer((head + i) % capacity).asInstanceOf[A]
        i       += 1

      builder.result()
    }

    // Zipper operations

    def moveLeft: Option[FilledWindow[A]] =
      if focusIdx > 0 then
        Some(new FilledWindow(buffer, head, focusIdx - 1, capacity))
      else
        None

    def moveRight: Option[FilledWindow[A]] =
      if focusIdx < capacity - 1 then
        Some(new FilledWindow(buffer, head, focusIdx + 1, capacity))
      else
        None

    def modifyFocus(f: A => A): FilledWindow[A] =
      val newBuffer = buffer.clone()
      val idx       = (head + focusIdx) % capacity
      newBuffer(idx) = f(buffer(idx).asInstanceOf[A])
      new FilledWindow(newBuffer, head, focusIdx, capacity)

    def setFocus(a: A): FilledWindow[A] =
      val newBuffer = buffer.clone()
      val idx       = (head + focusIdx) % capacity
      newBuffer(idx) = a
      new FilledWindow(newBuffer, head, focusIdx, capacity)

    def focusIndex: Int =
      focusIdx

    def focusAt(index: Int): FilledWindow[A] =
      val clipped = Math.max(0, Math.min(index, capacity - 1))
      new FilledWindow(buffer, head, clipped, capacity)

  }

  def apply[A](capacity: Int): SlidingWindow[A] =
    val cap = Math.max(1, capacity)
    new FillingWindow(new Array[Any](cap), 0, 0, cap)

  def empty[A]: SlidingWindow[A] =
    new FillingWindow(new Array[Any](1), 0, 0, 1)

  object Filling {
    def unapply[A](sw: SlidingWindow[A]): Option[FillingWindow[A]] =
      sw match {
        case fw: FillingWindow[A] => Some(fw)
        case _                    => None
      }
  }

  object Filled {
    def unapply[A](sw: SlidingWindow[A]): Option[FilledWindow[A]] =
      sw match {
        case fw: FilledWindow[A] => Some(fw)
        case _                   => None
      }
  }

  given Comonad[FilledWindow] with {

    def extract[A](w: FilledWindow[A]): A =
      w.focus

    def coflatMap[A, B](w: FilledWindow[A])(f: FilledWindow[A] => B): FilledWindow[B] =
      val results = new Array[Any](w.capacity)

      var i = 0
      while i < w.capacity do
        val focused = w.focusAt(i)
        results(i) = f(focused)
        i += 1

      new FilledWindow(results, 0, w.focusIndex, w.capacity)

    def map[A, B](fa: FilledWindow[A])(f: A => B): FilledWindow[B] =
      val vec       = fa.toVector
      val newBuffer = new Array[Any](fa.capacity)

      var i = 0
      while i < fa.capacity do
        newBuffer(i) = f(vec(i))
        i += 1

      new FilledWindow(newBuffer, 0, fa.focusIndex, fa.capacity)

  }

  given Functor[SlidingWindow] with {

    def map[A, B](fa: SlidingWindow[A])(f: A => B): SlidingWindow[B] =
      fa match {
        case fw: FillingWindow[A] =>
          val vec       = fw.toVector
          val newBuffer = new Array[Any](fw.capacity)

          var i = 0
          while i < fw.length do
            newBuffer(i) = f(vec(i))
            i += 1

          new FillingWindow(newBuffer, 0, fw.length, fw.capacity)
        case fw: FilledWindow[A] =>
          Comonad[FilledWindow].map(fw)(f)
      }

  }

}
