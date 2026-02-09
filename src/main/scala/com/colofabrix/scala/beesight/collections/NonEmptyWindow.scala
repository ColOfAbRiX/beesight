package com.colofabrix.scala.beesight.collections

import breeze.linalg.DenseVector
import cats.*
import cats.syntax.all.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import com.colofabrix.scala.beesight.collections.SWindow.*

/**
 * A non-empty sliding window - guaranteed to have at least one element.
 */
sealed trait NonEmptyWindow[A] {

  //  Capacity & Size  //

  def capacity: Int
  def length: Int
  def isAtCapacity: Boolean
  def isEmpty: Boolean = false

  //  Accessors  //

  def toVector: Vector[A]
  def toDenseVector(f: A => Double): DenseVector[Double]
  def oldest: A
  def newest: A

  //  Mutations  //

  def enqueue(a: A): NonEmptyWindow[A]
  def push(a: A): (Option[A], NonEmptyWindow[A])

  //  Subwindows & Conversions  //

  def filledSubwindow(start: Int, count: Int): Option[FilledWindow[A]]
  def filledSubwindow(count: Int): Option[FilledWindow[A]]
  def asFilledWindow: Option[NonEmptyFilled[A]]

  //  Transformations  //

  def map[B](f: A => B): NonEmptyWindow[B]
  def foldLeft[B](z: B)(f: (B, A) => B): B
  def foldRight[B](z: B)(f: (A, B) => B): B

}

object NonEmptyWindow {

  /**
   * A non-empty window that is still being filled (has not yet reached capacity).
   */
  final class NonEmptyFillingWindow[A] private[NonEmptyWindow] (
    val underlying: FillingWindow[A],
  ) extends NonEmptyWindow[A] {

    //  Capacity & Size  //

    def length: Int =
      underlying.length

    def capacity: Int =
      underlying.capacity

    def isAtCapacity: Boolean =
      underlying.isAtCapacity

    //  Accessors  //

    def toVector: Vector[A] =
      underlying.toVector

    def oldest: A =
      underlying.oldestOption.get

    def newest: A =
      underlying.newestOption.get

    def toDenseVector(f: A => Double): DenseVector[Double] =
      underlying.toDenseVector(f)

    //  Mutations  //

    def enqueue(a: A): NonEmptyWindow[A] =
      underlying.push(a)._2 match {
        case filled: FilledWindow[A]   => new NonEmptyFilled(filled)
        case filling: FillingWindow[A] => new NonEmptyFillingWindow(filling)
      }

    def push(a: A): (Option[A], NonEmptyWindow[A]) =
      underlying.push(a) match {
        case (out, filled: FilledWindow[A])   => (out, new NonEmptyFilled(filled))
        case (out, filling: FillingWindow[A]) => (out, new NonEmptyFillingWindow(filling))
      }

    //  Subwindows & Conversions  //

    def filledSubwindow(start: Int, count: Int): Option[FilledWindow[A]] =
      underlying.filledSubwindow(start, count)

    def filledSubwindow(count: Int): Option[FilledWindow[A]] =
      underlying.filledSubwindow(count)

    def asFilledWindow: Option[NonEmptyFilled[A]] =
      underlying.asFilledWindow.map(new NonEmptyFilled[A](_))

    //  Transformations  //

    def map[B](f: A => B): NonEmptyFillingWindow[B] =
      new NonEmptyFillingWindow(underlying.map(f))

    def foldLeft[B](z: B)(f: (B, A) => B): B =
      underlying.foldLeft(z)(f)

    def foldRight[B](z: B)(f: (A, B) => B): B =
      underlying.foldRight(z)(f)

  }

  /**
   * A non-empty window that has reached capacity and supports zipper-like focus operations.
   */
  final class NonEmptyFilled[A] private[NonEmptyWindow] (
    val underlying: FilledWindow[A],
  ) extends NonEmptyWindow[A] {

    //  Capacity & Size  //

    def length: Int =
      underlying.length

    def capacity: Int =
      underlying.capacity

    def isAtCapacity: Boolean =
      underlying.isAtCapacity

    //  Accessors  //

    def toVector: Vector[A] =
      underlying.toVector

    def oldest: A =
      underlying.oldest

    def newest: A =
      underlying.newest

    def toDenseVector(f: A => Double): DenseVector[Double] =
      underlying.toDenseVector(f)

    //  Mutations  //

    def enqueue(a: A): NonEmptyWindow[A] =
      val (_, newWindow) = underlying.pushReturning(a)
      new NonEmptyFilled(newWindow)

    def push(a: A): (Option[A], NonEmptyWindow[A]) =
      val (out, newWindow) = underlying.pushReturning(a)
      (Some(out), new NonEmptyFilled(newWindow))

    def pushReturning(a: A): (A, NonEmptyFilled[A]) =
      val (Some(out), window) = push(a): @unchecked
      (out, window.asInstanceOf[NonEmptyFilled[A]])

    //  Subwindows & Conversions  //

    def filledSubwindow(start: Int, count: Int): Option[FilledWindow[A]] =
      underlying.filledSubwindow(start, count)

    def filledSubwindow(count: Int): Option[FilledWindow[A]] =
      underlying.filledSubwindow(count)

    def asFilledWindow: Option[NonEmptyFilled[A]] =
      Some(this)

    //  Focus (Zipper)  //

    def focus: A =
      underlying.focus

    def focusAt(index: Int): NonEmptyFilled[A] =
      new NonEmptyFilled(underlying.focusAt(index))

    def left: Option[FilledWindow[A]] =
      underlying.left

    def right: Option[FilledWindow[A]] =
      underlying.right

    def leftInclusive: FilledWindow[A] =
      underlying.leftInclusive

    def rightInclusive: FilledWindow[A] =
      underlying.rightInclusive

    //  Transformations  //

    def map[B](f: A => B): NonEmptyFilled[B] =
      new NonEmptyFilled(underlying.map(f))

    def foldLeft[B](z: B)(f: (B, A) => B): B =
      underlying.foldLeft(z)(f)

    def foldRight[B](z: B)(f: (A, B) => B): B =
      underlying.foldRight(z)(f)

  }

  //  Factory Methods  //

  def apply[A](capacity: Int, first: A): NonEmptyWindow[A] =
    val cap        = Math.max(1, capacity)
    val underlying = SWindow[A](cap).enqueue(first)

    underlying match {
      case filled: FilledWindow[A]   => new NonEmptyFilled(filled)
      case filling: FillingWindow[A] => new NonEmptyFillingWindow(filling)
    }

  //  Type Class Instances  //

  given Functor[NonEmptyWindow] with
    def map[A, B](fa: NonEmptyWindow[A])(f: A => B): NonEmptyWindow[B] =
      fa.map(f)

  given Traverse[NonEmptyWindow] with {

    def traverse[G[_]: Applicative, A, B](fa: NonEmptyWindow[A])(f: A => G[B]): G[NonEmptyWindow[B]] =
      val G = Applicative[G]

      val underlying =
        fa match {
          case filling: NonEmptyFillingWindow[A] => filling.underlying: SWindow[A]
          case filled: NonEmptyFilled[A]         => filled.underlying: SWindow[A]
        }

      Traverse[SWindow]
        .traverse(underlying)(f)
        .map {
          case filled: FilledWindow[B]   => new NonEmptyFilled(filled)
          case filling: FillingWindow[B] => new NonEmptyFillingWindow(filling)
        }

    def foldLeft[A, B](fa: NonEmptyWindow[A], b: B)(f: (B, A) => B): B =
      fa.foldLeft(b)(f)

    def foldRight[A, B](fa: NonEmptyWindow[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      Eval.defer {
        fa.foldRight(lb)((a, evalB) => f(a, evalB))
      }

  }

}
