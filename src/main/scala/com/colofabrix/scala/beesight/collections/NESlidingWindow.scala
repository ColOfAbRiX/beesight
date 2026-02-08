package com.colofabrix.scala.beesight.collections

sealed trait NESlidingWindow[A] {
  def head: A
  def oldest: A
  def newest: A
  def length: Int
  def enqueue(a: A): NESlidingWindow[A]
  def push(a: A): (Option[A], NESlidingWindow[A])
  def toVector: Vector[A]
  def capacity: Int
  def isAtCapacity: Boolean
  def asFilledWindow: Option[NESlidingWindow.NEFilledWindow[A]]
}

object NESlidingWindow {

  final class NEFillingWindow[A] private[NESlidingWindow] (
    val underlying: SlidingWindow.FillingWindow[A],
  ) extends NESlidingWindow[A] {

    def head: A =
      underlying.toVector.head

    def oldest: A =
      underlying.toVector.head

    def newest: A =
      underlying.toVector.last

    def length: Int =
      underlying.length

    def capacity: Int =
      underlying.capacity

    def isAtCapacity: Boolean =
      underlying.isAtCapacity

    def toVector: Vector[A] =
      underlying.toVector

    def enqueue(a: A): NESlidingWindow[A] =
      underlying.push(a)._2 match {
        case filled: SlidingWindow.FilledWindow[A]   => new NEFilledWindow(filled)
        case filling: SlidingWindow.FillingWindow[A] => new NEFillingWindow(filling)
      }

    def push(a: A): (Option[A], NESlidingWindow[A]) =
      underlying.push(a) match {
        case (out, filled: SlidingWindow.FilledWindow[A])   => (out, new NEFilledWindow(filled))
        case (out, filling: SlidingWindow.FillingWindow[A]) => (out, new NEFillingWindow(filling))
      }

    def asFilledWindow: Option[NEFilledWindow[A]] =
      underlying.asFilledWindow.map { filledWindow =>
        new NEFilledWindow[A](filledWindow)
      }

  }

  final class NEFilledWindow[A] private[NESlidingWindow] (
    val underlying: SlidingWindow.FilledWindow[A],
  ) extends NESlidingWindow[A] {

    def head: A =
      underlying.oldest

    def oldest: A =
      underlying.oldest

    def newest: A =
      underlying.newest

    def length: Int =
      underlying.length

    def capacity: Int =
      underlying.capacity

    def isAtCapacity: Boolean =
      underlying.isAtCapacity

    def toVector: Vector[A] =
      underlying.toVector

    def enqueue(a: A): NESlidingWindow[A] =
      val (_, newWindow) = underlying.pushReturning(a)
      new NEFilledWindow(newWindow)

    def push(a: A): (Option[A], NESlidingWindow[A]) =
      val (out, newWindow) = underlying.pushReturning(a)
      (Some(out), new NEFilledWindow(newWindow))

    def pushReturning(a: A): (A, NEFilledWindow[A]) =
      val (Some(out), window) = push(a): @unchecked
      (out, window.asInstanceOf[NEFilledWindow[A]])

    def focus: A =
      underlying.focus

    def left: Vector[A] =
      underlying.left

    def right: Vector[A] =
      underlying.right

    def asFilledWindow: Option[NESlidingWindow.NEFilledWindow[A]] =
      Some(this)

  }

  def apply[A](capacity: Int, first: A): NESlidingWindow[A] =
    val cap        = Math.max(1, capacity)
    val underlying = SlidingWindow[A](cap).enqueue(first)

    underlying match {
      case filled: SlidingWindow.FilledWindow[A]   => new NEFilledWindow(filled)
      case filling: SlidingWindow.FillingWindow[A] => new NEFillingWindow(filling)
    }

  object Filling {
    def unapply[A](nesw: NESlidingWindow[A]): Option[NEFillingWindow[A]] =
      nesw match {
        case nef: NEFillingWindow[A] => Some(nef)
        case _                       => None
      }
  }

  object Filled {
    def unapply[A](nesw: NESlidingWindow[A]): Option[NEFilledWindow[A]] =
      nesw match {
        case nef: NEFilledWindow[A] => Some(nef)
        case _                      => None
      }
  }

}
