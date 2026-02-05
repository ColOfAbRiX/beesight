package com.colofabrix.scala.beesight.collections

import breeze.linalg.DenseVector
import cats.*
import cats.instances.vector.*
import scala.reflect.ClassTag

/**
 * Fixed size queue backed by Vector for O(log32 n) ≈ O(1) operations
 */
final class FSQueue[@specialized(Double) A] private (private val buffer: Vector[A], val size: Int) {

  def enqueue(a: A): FSQueue[A] =
    push(a)._2

  def push(a: A): (Option[A], FSQueue[A]) =
    pushMap(a)(identity)

  def pushMap[B](a: A)(f: A => B): (Option[B], FSQueue[A]) =
    if buffer.length >= size then
      val outgoing  = buffer.head
      val remaining = buffer.tail :+ a
      (Some(f(outgoing)), new FSQueue(remaining, size))
    else
      (None, new FSQueue(buffer :+ a, size))

  def setSize(n: Int): FSQueue[A] =
    if n >= size then new FSQueue(buffer, n)
    else new FSQueue(buffer.takeRight(n), n)

  @inline def length: Int =
    buffer.length

  @inline def isEmpty: Boolean =
    buffer.isEmpty

  @inline def newest: Option[A] =
    buffer.lastOption

  @inline def oldest: Option[A] =
    buffer.headOption

  def oldest(n: Int): Vector[A] =
    buffer.take(n)

  def newest(n: Int): Vector[A] =
    buffer.takeRight(n)

  def toDenseVector[B >: A: ClassTag]: DenseVector[B] =
    DenseVector(buffer.toArray[B])

  def toVector: Vector[A] =
    buffer

  def toList: List[A] =
    buffer.toList

}

object FSQueue {

  def apply[A](size: Int): FSQueue[A] =
    new FSQueue(Vector.empty, size)

  def unapplySeq[A](fsq: FSQueue[A]): Seq[A] =
    fsq.buffer.toSeq

  def empty[A]: FSQueue[A] =
    new FSQueue(Vector.empty, 1)

  given (using M: Monad[Vector]): Monad[FSQueue] with {

    def flatMap[A, B](fa: FSQueue[A])(f: A => FSQueue[B]): FSQueue[B] =
      new FSQueue(M.flatMap(fa.buffer)(a => f(a).buffer), fa.size)

    def pure[A](x: A): FSQueue[A] =
      new FSQueue(M.pure(x), 1)

    def tailRecM[A, B](a: A)(f: A => FSQueue[Either[A, B]]): FSQueue[B] =
      new FSQueue(M.tailRecM(a)(a => f(a).buffer), f(a).size)

  }

}
