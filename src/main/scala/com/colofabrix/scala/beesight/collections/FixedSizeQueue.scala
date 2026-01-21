package com.colofabrix.scala.beesight.collections

import scala.collection.immutable.Queue
import scala.reflect.ClassTag

final class FixedSizeQueue[+A: ClassTag] private (queue: Queue[A], size: Int) {

  def enqueue[B >: A: ClassTag](a: B): FixedSizeQueue[B] =
    val updated = queue.enqueue(a)
    if updated.size > size then new FixedSizeQueue(updated.dequeue._2, size)
    else new FixedSizeQueue(updated, size)

  def setSize(size: Int): FixedSizeQueue[A] =
    val updated = queue.drop(Math.max(0, queue.size - size))
    new FixedSizeQueue[A](updated, Math.max(0, size))

  def toArray[B >: A: ClassTag]: Array[B] =
    queue.toArray[B]

  def toVector: Vector[A] =
    queue.toVector

  def isEmpty: Boolean =
    queue.isEmpty

  def isFull: Boolean =
    queue.sizeIs == size

}

object FixedSizeQueue {

  def apply[A: ClassTag](size: Int): FixedSizeQueue[A] =
    new FixedSizeQueue(Queue.empty[A], size)

  def empty[A: ClassTag]: FixedSizeQueue[A] =
    new FixedSizeQueue(Queue.empty[A], 1)

}
