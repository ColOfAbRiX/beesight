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

  final class FillingWindow[A] private[SlidingWindow] (
    private val buffer: Vector[A],
    val capacity: Int,
  ) extends SlidingWindow[A] {

    def enqueue(a: A): SlidingWindow[A] =
      push(a)._2

    def push(a: A): (Option[A], SlidingWindow[A]) =
      val newBuffer = buffer :+ a
      if newBuffer.length >= capacity then
        (None, new FilledWindow(newBuffer.init, newBuffer.last, Vector.empty, capacity))
      else
        (None, new FillingWindow(newBuffer, capacity))

    def sliceFilled(start: Int, count: Int): Option[FilledWindow[A]] =
      val sliced =
        if count >= 0 then buffer.slice(start, start + count)
        else buffer.slice(start + count + 1, start + 1)
      Option.when(sliced.nonEmpty) {
        fromVectorAt(sliced, sliced.length - 1, sliced.length)
      }

    def length: Int =
      buffer.length

    def isEmpty: Boolean =
      buffer.isEmpty

    def toVector: Vector[A] =
      buffer

  }

  final class FilledWindow[A] private[SlidingWindow] (
    val left: Vector[A],
    val focus: A,
    val right: Vector[A],
    val capacity: Int,
  ) extends SlidingWindow[A] {

    def enqueue(a: A): SlidingWindow[A] =
      push(a)._2

    def push(a: A): (Option[A], SlidingWindow[A]) =
      val allElements   = toVector
      val outgoing      = allElements.head
      val remaining     = allElements.tail :+ a
      val focusFromEnd  = right.length
      val newFocusIndex = remaining.length - 1 - focusFromEnd
      val clampedIndex  = Math.max(0, Math.min(newFocusIndex, remaining.length - 1))
      (Some(outgoing), fromVectorAt(remaining, clampedIndex, capacity))

    def pushReturning(a: A): (A, FilledWindow[A]) =
      val (Some(out), window) = push(a): @unchecked
      (out, window.asInstanceOf[FilledWindow[A]])

    def sliceFilled(start: Int, count: Int): Option[FilledWindow[A]] =
      val all    = toVector
      val sliced =
        if count >= 0 then all.slice(start, start + count)
        else all.slice(start + count + 1, start + 1)
      Option.when(sliced.nonEmpty) {
        fromVectorAt(sliced, sliced.length - 1, sliced.length)
      }

    def length: Int =
      left.length + 1 + right.length

    def toVector: Vector[A] =
      left ++ Vector(focus) ++ right

    def oldest: A =
      left.headOption.getOrElse(focus)

    def newest: A =
      right.lastOption.getOrElse(focus)

    // Zipper operations

    def moveLeft: Option[FilledWindow[A]] =
      left.lastOption.map { newFocus =>
        new FilledWindow(left.init, newFocus, focus +: right, capacity)
      }

    def moveRight: Option[FilledWindow[A]] =
      right.headOption.map { newFocus =>
        new FilledWindow(left :+ focus, newFocus, right.tail, capacity)
      }

    def modifyFocus(f: A => A): FilledWindow[A] =
      new FilledWindow(left, f(focus), right, capacity)

    def setFocus(a: A): FilledWindow[A] =
      new FilledWindow(left, a, right, capacity)

    def focusIndex: Int =
      left.length

    def focusAt(index: Int): FilledWindow[A] =
      val clipped = Math.max(0, Math.min(index, length - 1))
      fromVectorAt(toVector, clipped, capacity)
  }

  def apply[A](capacity: Int): SlidingWindow[A] =
    new FillingWindow(Vector.empty, Math.max(1, capacity))

  def empty[A]: SlidingWindow[A] =
    new FillingWindow(Vector.empty, 1)

  private def fromVectorAt[A](elements: Vector[A], focusIndex: Int, capacity: Int): FilledWindow[A] =
    val idx = Math.max(0, Math.min(focusIndex, elements.length - 1))
    new FilledWindow(
      left = elements.take(idx),
      focus = elements(idx),
      right = elements.drop(idx + 1),
      capacity = capacity,
    )

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
      val elements = w.toVector

      val results =
        elements
          .indices
          .map { i =>
            val focused = fromVectorAt(elements, i, w.capacity)
            f(focused)
          }
          .toVector

      fromVectorAt(results, w.focusIndex, w.capacity)

    def map[A, B](fa: FilledWindow[A])(f: A => B): FilledWindow[B] =
      new FilledWindow(
        left = fa.left.map(f),
        focus = f(fa.focus),
        right = fa.right.map(f),
        capacity = fa.capacity,
      )

  }

  given Functor[SlidingWindow] with {

    def map[A, B](fa: SlidingWindow[A])(f: A => B): SlidingWindow[B] =
      fa match {
        case fw: FillingWindow[A] =>
          new FillingWindow(fw.toVector.map(f), fw.capacity)
        case fw: FilledWindow[A] =>
          Comonad[FilledWindow].map(fw)(f)
      }

  }

}
