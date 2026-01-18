package com.colofabrix.scala.beesight.detection

import scala.collection.immutable.Queue

private[detection] final class FixedSizeQueue[+A] private (queue: Queue[A], size: Int) {

  def enqueue[B >: A](a: B): FixedSizeQueue[B] =
    val updated = queue.enqueue(a)
    if updated.size > size then new FixedSizeQueue(updated.dequeue._2, size)
    else new FixedSizeQueue(updated, size)

  def setSize(size: Int): FixedSizeQueue[A] =
    val updated = queue.drop(Math.max(0, queue.size - size))
    new FixedSizeQueue[A](updated, Math.max(0, size))

  def toSeq: Seq[A] =
    queue.toSeq

  def isEmpty: Boolean =
    queue.isEmpty

}

object FixedSizeQueue {

  def apply[A](size: Int): FixedSizeQueue[A] =
    new FixedSizeQueue(Queue.empty[A], size)

  def empty[A]: FixedSizeQueue[A] =
    new FixedSizeQueue(Queue.empty[A], 1)

}
