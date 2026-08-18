package com.colofabrix.scala.beesight.collections

import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*

private[beesight] type NEW[A] = NonEmptyWindow[A]
private[beesight] val NEW = NonEmptyWindow

/**
 * A non-empty sliding window - guaranteed to have at least one element.
 */
sealed private[beesight] trait NonEmptyWindow[A] {

  // Internal state //

  protected def buffer: Array[Any]
  protected def head: Int
  protected def focusIdx: Int

  // Capacity & Size //

  /** Maximum number of elements this window can hold. */
  def capacity: Int

  /** Current number of elements in the window. */
  def length: Int

  /** True when the window contains exactly `capacity` elements. */
  def isAtCapacity: Boolean

  // Buffer Helpers //

  /** Read a single element from the circular buffer at a logical index. */
  protected inline def readAt(index: Int): A =
    buffer((head + index) % capacity).asInstanceOf[A]

  /** Copy a slice from the circular buffer into a new Array. */
  protected def sliceBuffer(start: Int, count: Int): Array[Any] =
    val newBuffer = new Array[Any](count)
    var i         = 0
    while i < count do
      newBuffer(i) = buffer((head + start + i) % capacity)
      i += 1
    newBuffer

  /** Copy a slice from the circular buffer into a Vector. */
  protected def sliceToVector(start: Int, count: Int): Vector[A] =
    val builder = Vector.newBuilder[A]
    var i       = 0
    while i < count do
      builder += buffer((head + start + i) % capacity).asInstanceOf[A]
      i       += 1
    builder.result()

  // Accessors //

  /** Returns all elements as a Vector, from oldest to newest. */
  def toVector: Vector[A] =
    sliceToVector(0, length)

  /** Returns the oldest (first-inserted) element in the window. */
  def oldest: A =
    readAt(0)

  /** Returns the newest (most recently inserted) element in the window. */
  def newest: A =
    readAt(length - 1)

  // Mutations //

  /** Appends an element, discarding the oldest if at capacity. Returns the updated window. */
  def enqueue(a: A): NonEmptyWindow[A]

  /** Appends an element. Returns the evicted element (if any) and the updated window. */
  def push(a: A): (Option[A], NonEmptyWindow[A])

  /** Removes up to `n` oldest elements, returning them and the remaining filling window. */
  def dequeue(n: Int): (Vector[A], NonEmptyFilling[A]) =
    val maxDequeue  = Math.max(0, Math.min(n, capacity - 1))
    val remaining   = capacity - maxDequeue
    val newCapacity = capacity
    val newBuffer   = new Array[Any](newCapacity)
    val slice       = sliceBuffer(maxDequeue, remaining)
    System.arraycopy(slice, 0, newBuffer, 0, remaining)
    val dequeued    = sliceToVector(0, maxDequeue)
    val newFocusIdx = Math.max(0, Math.min(focusIdx - maxDequeue, remaining - 1))
    (dequeued, new NonEmptyFilling(newBuffer, 0, remaining, newFocusIdx, newCapacity))

  // Subwindows //

  /** Returns a filled subwindow containing the `i` most recently added elements (at least 1). */
  def takeNewest(i: Int): NonEmptyFilled[A] =
    val count     = Math.max(1, Math.min(i, length))
    val start     = length - count
    val newBuffer = sliceBuffer(start, count)
    new NonEmptyFilled(newBuffer, 0, count - 1, count)

  // Focus (Zipper) //

  /** Returns the element at the current focus position. */
  def focus: A =
    readAt(focusIdx)

  /** Returns the current focus index (0-based, relative to oldest). */
  def focusIndex: Int =
    focusIdx

  /** Returns a copy of this window with the focus moved to the given index. */
  def focusAt(index: Int): NonEmptyWindow[A]

  // Transformations //

  /** Transforms every element in the window, preserving structure and focus. */
  def map[B](f: A => B): NonEmptyWindow[B]

  /** Left-associative fold over all elements from oldest to newest. */
  def foldLeft[B](z: B)(f: (B, A) => B): B =
    var acc = z
    var i   = 0
    while i < length do
      acc = f(acc, readAt(i))
      i += 1
    acc

  // Display //

  protected def label: String

  override def toString: String =
    val elements =
      toVector
        .zipWithIndex
        .map { case (e, i) => if i == focusIdx then s"[$e]" else e.toString }
        .mkString(", ")

    s"$label($elements)"

}

object NonEmptyWindow {

  /**
   * A non-empty window that is still being filled (1 ≤ length < capacity).
   */
  final class NonEmptyFilling[A] private[NonEmptyWindow] (
    protected val buffer: Array[Any],
    protected val head: Int,
    private val len: Int,
    protected val focusIdx: Int,
    val capacity: Int,
  ) extends NonEmptyWindow[A] {

    // Capacity & Size //

    def length: Int =
      len

    def isAtCapacity: Boolean =
      len == capacity

    // Accessors //

    def toNonEmptyFilled: NonEmptyFilled[A] =
      val newBuffer   = sliceBuffer(0, len)
      val newFocusIdx = Math.min(focusIdx, len - 1)
      new NonEmptyFilled(newBuffer, 0, newFocusIdx, len)

    // Mutations //

    def enqueue(a: A): NonEmptyWindow[A] =
      push(a)._2

    def push(a: A): (Option[A], NonEmptyWindow[A]) =
      val newLen    = len + 1
      val newBuffer = buffer.clone()
      val tail      = (head + len) % capacity
      newBuffer(tail) = a

      if newLen >= capacity then
        val newFocusIdx = Math.min(focusIdx, capacity - 1)
        (None, new NonEmptyFilled(newBuffer, head, newFocusIdx, capacity))
      else
        (None, new NonEmptyFilling(newBuffer, head, newLen, focusIdx, capacity))

    // Focus (Zipper) //

    def focusAt(index: Int): NonEmptyFilling[A] =
      val clipped = Math.max(0, Math.min(index, len - 1))
      new NonEmptyFilling(buffer, head, len, clipped, capacity)

    // Transformations //

    def map[B](f: A => B): NonEmptyFilling[B] =
      val newBuffer = new Array[Any](capacity)
      var i         = 0
      while i < len do
        newBuffer(i) = f(readAt(i))
        i += 1
      new NonEmptyFilling(newBuffer, 0, len, focusIdx, capacity)

    // Display //

    protected def label: String =
      s"NonEmptyFilling[$len/$capacity]"

  }

  /**
   * A non-empty window that has reached capacity (length == capacity).
   */
  final class NonEmptyFilled[A] private[NonEmptyWindow] (
    protected val buffer: Array[Any],
    protected val head: Int,
    protected val focusIdx: Int,
    val capacity: Int,
  ) extends NonEmptyWindow[A] {

    // Capacity & Size //

    def length: Int =
      capacity

    def isAtCapacity: Boolean =
      true

    // Mutations //

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

    def shrinkReturning(n: Int): (Vector[A], Option[NonEmptyFilled[A]]) =
      val toRemove  = Math.max(0, Math.min(n, capacity))
      val remaining = capacity - toRemove

      val removed = sliceToVector(0, toRemove)

      if remaining <= 0 then
        (removed, None)
      else
        val newBuffer   = sliceBuffer(toRemove, remaining)
        val newFocusIdx = Math.max(0, Math.min(focusIdx - toRemove, remaining - 1))
        (removed, Some(new NonEmptyFilled(newBuffer, 0, newFocusIdx, remaining)))

    // Subwindows //

    def filledSubwindow(start: Int, count: Int): NonEmptyFilled[A] =
      val sliceLen =
        if count >= 0 then Math.min(count, capacity - start)
        else Math.min(-count, start + 1)

      val safeLen    = Math.max(1, sliceLen)
      val sliceStart = if count >= 0 then start else start - safeLen + 1
      val newBuffer  = sliceBuffer(sliceStart, safeLen)

      new NonEmptyFilled(newBuffer, 0, safeLen - 1, safeLen)

    def filledSubwindow(count: Int): NonEmptyFilled[A] =
      filledSubwindow(focusIdx, count)

    def subwindowAt(left: Int, focus: Int, right: Int): NonEmptyFilled[A] =
      val clampedFocus = Math.max(0, Math.min(focus, capacity - 1))

      val actualLeft  = Math.min(Math.max(0, left), clampedFocus)
      val actualRight = Math.min(Math.max(0, right), capacity - 1 - clampedFocus)

      val startIdx = clampedFocus - actualLeft
      val sliceLen = actualLeft + 1 + actualRight

      val newBuffer = sliceBuffer(startIdx, sliceLen)

      new NonEmptyFilled(newBuffer, 0, actualLeft, sliceLen)

    // Focus (Zipper) //

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

    // Transformations //

    def map[B](f: A => B): NonEmptyFilled[B] =
      val newBuffer = new Array[Any](capacity)
      var i         = 0
      while i < capacity do
        newBuffer(i) = f(readAt(i))
        i += 1
      new NonEmptyFilled(newBuffer, 0, focusIdx, capacity)

    // Display //

    protected def label: String =
      s"NonEmptyFilled[$capacity]"

  }

  // Factory Methods //

  def apply[A](capacity: Int, first: A): NonEmptyWindow[A] =
    val cap    = Math.max(1, capacity)
    val buffer = new Array[Any](cap)
    buffer(0) = first

    if cap == 1 then
      new NonEmptyFilled(buffer, 0, 0, cap)
    else
      new NonEmptyFilling(buffer, 0, 1, 0, cap)

}
