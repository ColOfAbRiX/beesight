package com.colofabrix.scala.beesight.detection.collections

import com.colofabrix.scala.beesight.detection.collections.NonEmptyWindow.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class NonEmptyWindowSpec extends AnyFreeSpec with Matchers {

  "NonEmptyWindow" - {

    "creation" - {

      "should require at least one element" in {
        val window = NonEmptyWindow(5, "first")
        window.length shouldBe 1
        window.oldest shouldBe "first"
        window.newest shouldBe "first"
      }

      "should have specified capacity" in {
        val window = NonEmptyWindow(5, 42)
        window.capacity shouldBe 5
      }

      "should enforce minimum capacity of 1" in {
        val window = NonEmptyWindow(0, "x")
        window.capacity shouldBe 1
      }

      "with capacity 1 should be immediately filled" in {
        val window = NonEmptyWindow(1, "only")
        window shouldBe a[NonEmptyFilled[?]]
        window.isAtCapacity shouldBe true
      }

      "with capacity > 1 should start as filling" in {
        val window = NonEmptyWindow(5, "first")
        window shouldBe a[NonEmptyFilling[?]]
        window.isAtCapacity shouldBe false
      }

    }

    "size properties" - {

      "length should return current element count" in {
        val window = NonEmptyWindow(5, 1)
          .enqueue(2)
          .enqueue(3)

        window.length shouldBe 3
      }

      "isAtCapacity should be false while filling" in {
        val window = NonEmptyWindow(5, 1)
          .enqueue(2)
          .enqueue(3)

        window.isAtCapacity shouldBe false
      }

      "isAtCapacity should be true when full" in {
        val window = filledWindow(1, 2, 3, 4, 5)
        window.isAtCapacity shouldBe true
      }

    }

    "accessors" - {

      "oldest should return first element (FIFO)" in {
        val window = NonEmptyWindow(5, 1)
          .enqueue(2)
          .enqueue(3)

        window.oldest shouldBe 1
      }

      "newest should return last element added" in {
        val window = NonEmptyWindow(5, 1)
          .enqueue(2)
          .enqueue(3)

        window.newest shouldBe 3
      }

      "oldest and newest should be same for single element" in {
        val window = NonEmptyWindow(5, "only")
        window.oldest shouldBe "only"
        window.newest shouldBe "only"
      }

      "toVector should return elements in FIFO order" in {
        val window = NonEmptyWindow(5, 1)
          .enqueue(2)
          .enqueue(3)

        window.toVector shouldBe Vector(1, 2, 3)
      }

    }

    "mutations" - {

      "enqueue should add element to end" in {
        val window = NonEmptyWindow(5, 1)
          .enqueue(2)
          .enqueue(3)

        window.toVector shouldBe Vector(1, 2, 3)
      }

      "push should return None while filling" in {
        val window          = NonEmptyWindow[Int](5, 1)
        val (evicted, next) = window.push(2)

        evicted shouldBe None
        next.toVector shouldBe Vector(1, 2)
      }

      "push should return oldest element when at capacity" in {
        val window          = filledWindow(1, 2, 3)
        val (evicted, next) = window.push(4)

        evicted shouldBe Some(1)
        next.toVector shouldBe Vector(2, 3, 4)
      }

      "enqueue should evict oldest when at capacity" in {
        val window = filledWindow(1, 2, 3)
          .enqueue(4)
          .enqueue(5)

        window.toVector shouldBe Vector(3, 4, 5)
        window.oldest shouldBe 3
        window.newest shouldBe 5
      }

      "capacity should remain constant after enqueue" in {
        val window = filledWindow(1, 2, 3)
          .enqueue(4)
          .enqueue(5)

        window.capacity shouldBe 3
        window.length shouldBe 3
      }

    }

    "state transitions" - {

      "NonEmptyFilling should transition to NonEmptyFilled at capacity" in {
        val window = NonEmptyWindow(3, 1)
        window shouldBe a[NonEmptyFilling[?]]

        val next = window.enqueue(2).enqueue(3)
        next shouldBe a[NonEmptyFilled[?]]
      }

      "NonEmptyFilled should stay NonEmptyFilled after enqueue" in {
        val window = filledWindow(1, 2, 3)
        val next   = window.enqueue(4)

        next shouldBe a[NonEmptyFilled[?]]
      }

      "transition should occur at exactly capacity" in {
        val cap3 = NonEmptyWindow(3, 1)
        cap3 shouldBe a[NonEmptyFilling[?]]

        val after1 = cap3.enqueue(2)
        after1 shouldBe a[NonEmptyFilling[?]]

        val after2 = after1.enqueue(3)
        after2 shouldBe a[NonEmptyFilled[?]]
      }

    }

    "focus (zipper)" - {

      "default focus should be at index 0" in {
        val window = NonEmptyWindow(5, 1)
          .enqueue(2)
          .enqueue(3)

        window.focusIndex shouldBe 0
        window.focus shouldBe 1
      }

      "focusAt should change focus position" in {
        val window = NonEmptyWindow(5, 1)
          .enqueue(2)
          .enqueue(3)

        val focused = window.asInstanceOf[NonEmptyFilling[Int]].focusAt(2)
        focused.focusIndex shouldBe 2
        focused.focus shouldBe 3
      }

      "focusAt should clip negative index to 0" in {
        val window  = filledWindow(1, 2, 3, 4, 5)
        val focused = window.focusAt(-5)

        focused.focusIndex shouldBe 0
        focused.focus shouldBe 1
      }

      "focusAt should clip excessive index to last" in {
        val window  = filledWindow(1, 2, 3, 4, 5)
        val focused = window.focusAt(100)

        focused.focusIndex shouldBe 4
        focused.focus shouldBe 5
      }

      "focusAt should preserve all elements" in {
        val window  = filledWindow(1, 2, 3, 4, 5)
        val focused = window.focusAt(2)

        focused.toVector shouldBe Vector(1, 2, 3, 4, 5)
      }

    }

    "takeNewest" - {

      "should return newest n elements" in {
        val window  = filledWindow(1, 2, 3, 4, 5)
        val newest3 = window.takeNewest(3)

        newest3.toVector shouldBe Vector(3, 4, 5)
      }

      "should return all elements when n >= length" in {
        val window = filledWindow(1, 2, 3)
        val all    = window.takeNewest(10)

        all.toVector shouldBe Vector(1, 2, 3)
      }

      "should return at least 1 element when n <= 0" in {
        val window = filledWindow(1, 2, 3, 4, 5)
        val result = window.takeNewest(0)

        result.length should be >= 1
        result.newest shouldBe 5
      }

      "should work on NonEmptyFilling" in {
        val window = NonEmptyWindow(10, 1)
          .enqueue(2)
          .enqueue(3)
          .enqueue(4)
          .enqueue(5)

        val newest3 = window.takeNewest(3)
        newest3.toVector shouldBe Vector(3, 4, 5)
      }

    }

    "zipper operations (NonEmptyFilled)" - {

      "left should return elements before focus" in {
        val window = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        val left   = window.left

        left.isDefined shouldBe true
        left.get.toVector shouldBe Vector(1, 2)
      }

      "left should be None when focus at 0" in {
        val window = filledWindow(1, 2, 3, 4, 5).focusAt(0)
        window.left shouldBe None
      }

      "right should return elements after focus" in {
        val window = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        val right  = window.right

        right.isDefined shouldBe true
        right.get.toVector shouldBe Vector(4, 5)
      }

      "right should be None when focus at last" in {
        val window = filledWindow(1, 2, 3, 4, 5).focusAt(4)
        window.right shouldBe None
      }

      "leftInclusive should include focus" in {
        val window  = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        val leftInc = window.leftInclusive

        leftInc.toVector shouldBe Vector(1, 2, 3)
      }

      "rightInclusive should include focus" in {
        val window   = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        val rightInc = window.rightInclusive

        rightInc.toVector shouldBe Vector(3, 4, 5)
      }

      "modifyFocus should change focused element" in {
        val window   = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        val modified = window.modifyFocus(_ * 10)

        modified.focus shouldBe 30
        modified.toVector shouldBe Vector(1, 2, 30, 4, 5)
      }

    }

    "transformations" - {

      "map should transform all elements" in {
        val window = filledWindow(1, 2, 3)
        val mapped = window.map(_ * 2)

        mapped.toVector shouldBe Vector(2, 4, 6)
      }

      "map should preserve structure" in {
        val filling       = NonEmptyWindow(5, 1).enqueue(2)
        val mappedFilling = filling.map(_.toString)
        mappedFilling shouldBe a[NonEmptyFilling[?]]

        val filled       = filledWindow(1, 2, 3)
        val mappedFilled = filled.map(_.toString)
        mappedFilled shouldBe a[NonEmptyFilled[?]]
      }

      "foldLeft should fold from oldest to newest" in {
        val window = filledWindow(1, 2, 3, 4, 5)
        val result = window.foldLeft("")((acc, a) => acc + a.toString)

        result shouldBe "12345"
      }

    }

    "edge cases" - {

      "capacity 1 window should work correctly" in {
        val window = NonEmptyWindow(1, "first")
        window.length shouldBe 1
        window.isAtCapacity shouldBe true

        val next = window.enqueue("second")
        next.length shouldBe 1
        next.oldest shouldBe "second"
        next.newest shouldBe "second"
      }

      "large number of enqueues should maintain invariants" in {
        var window: NonEmptyWindow[Int] = NonEmptyWindow(5, 0)
        for i <- 1 to 100 do
          window = window.enqueue(i)

        window.length shouldBe 5
        window.isAtCapacity shouldBe true
        window.toVector shouldBe Vector(96, 97, 98, 99, 100)
        window.oldest shouldBe 96
        window.newest shouldBe 100
      }

      "focus should shift when element is evicted" in {
        val window = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        window.focus shouldBe 3

        val next = window.enqueue(6)
        // After eviction, focus should shift left
        next.focusIndex shouldBe 1
        next.focus shouldBe 3
      }

    }

    "dequeue" - {

      "should return oldest n elements and remaining window" in {
        val window                = filledWindow(1, 2, 3, 4, 5)
        val (dequeued, remaining) = window.dequeue(2)

        dequeued shouldBe Vector(1, 2)
        remaining.toVector shouldBe Vector(3, 4, 5)
      }

      "should return NonEmptyFilling" in {
        val window         = filledWindow(1, 2, 3, 4, 5)
        val (_, remaining) = window.dequeue(2)

        remaining shouldBe a[NonEmptyFilling[?]]
      }

      "should preserve original capacity for refilling" in {
        val window         = filledWindow(1, 2, 3, 4, 5)
        val (_, remaining) = window.dequeue(2)

        remaining.capacity shouldBe 5
        remaining.length shouldBe 3
      }

      "should handle dequeue(0) by returning empty vector and all elements" in {
        val window                = filledWindow(1, 2, 3, 4, 5)
        val (dequeued, remaining) = window.dequeue(0)

        dequeued shouldBe empty
        remaining.toVector shouldBe Vector(1, 2, 3, 4, 5)
      }

      "should handle negative n as dequeue(0)" in {
        val window                = filledWindow(1, 2, 3, 4, 5)
        val (dequeued, remaining) = window.dequeue(-5)

        dequeued shouldBe empty
        remaining.toVector shouldBe Vector(1, 2, 3, 4, 5)
      }

      "should keep at least one element when n >= capacity" in {
        val window                = filledWindow(1, 2, 3, 4, 5)
        val (dequeued, remaining) = window.dequeue(10)

        dequeued shouldBe Vector(1, 2, 3, 4)
        remaining.toVector shouldBe Vector(5)
        remaining.length shouldBe 1
      }

      "should keep at least one element when n == capacity" in {
        val window                = filledWindow(1, 2, 3, 4, 5)
        val (dequeued, remaining) = window.dequeue(5)

        dequeued shouldBe Vector(1, 2, 3, 4)
        remaining.toVector shouldBe Vector(5)
      }

      "should work with capacity 2 window" in {
        val window                = filledWindow(1, 2)
        val (dequeued, remaining) = window.dequeue(1)

        dequeued shouldBe Vector(1)
        remaining.toVector shouldBe Vector(2)
        remaining.capacity shouldBe 2
      }

      "should adjust focus index when dequeuing" in {
        val window = filledWindow(1, 2, 3, 4, 5).focusAt(3)
        window.focus shouldBe 4

        val (_, remaining) = window.dequeue(2)
        // Focus was at index 3, after removing 2 elements, it should shift
        remaining.focusIndex shouldBe 1
        remaining.focus shouldBe 4
      }

      "should clip focus to last element if focus becomes out of bounds" in {
        val window = filledWindow(1, 2, 3, 4, 5).focusAt(4)
        window.focus shouldBe 5

        val (_, remaining) = window.dequeue(3)
        // Only 2 elements remain (indices 0, 1), focus should be clipped
        remaining.focusIndex shouldBe 1
        remaining.focus shouldBe 5
      }

      "should support refilling after dequeue" in {
        val window         = filledWindow(1, 2, 3, 4, 5)
        val (_, remaining) = window.dequeue(3)

        remaining.length shouldBe 2
        remaining.capacity shouldBe 5

        val refilled = remaining.enqueue(6).enqueue(7).enqueue(8)
        refilled.length shouldBe 5
        refilled shouldBe a[NonEmptyFilled[?]]
        refilled.toVector shouldBe Vector(4, 5, 6, 7, 8)
      }

      "dequeued elements should be in FIFO order" in {
        val window        = filledWindow(10, 20, 30, 40, 50)
        val (dequeued, _) = window.dequeue(3)

        dequeued shouldBe Vector(10, 20, 30)
        dequeued.head shouldBe 10
        dequeued.last shouldBe 30
      }

    }

    "subwindowAt" - {

      "should extract sub-window centered on focus with left and right counts" in {
        val window = filledWindow(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        val sub    = window.subwindowAt(2, 5, 3)

        sub.toVector shouldBe Vector(40, 50, 60, 70, 80, 90)
        sub.focusIndex shouldBe 2
        sub.focus shouldBe 60
      }

      "should clip left when it exceeds available elements before focus" in {
        val window = filledWindow(10, 20, 30, 40, 50)
        val sub    = window.subwindowAt(10, 1, 2)

        sub.toVector shouldBe Vector(10, 20, 30, 40)
        sub.focusIndex shouldBe 1
        sub.focus shouldBe 20
      }

      "should clip right when it exceeds available elements after focus" in {
        val window = filledWindow(10, 20, 30, 40, 50)
        val sub    = window.subwindowAt(1, 3, 10)

        sub.toVector shouldBe Vector(30, 40, 50)
        sub.focusIndex shouldBe 1
        sub.focus shouldBe 40
      }

      "should handle focus at first position" in {
        val window = filledWindow(10, 20, 30, 40, 50)
        val sub    = window.subwindowAt(2, 0, 3)

        sub.toVector shouldBe Vector(10, 20, 30, 40)
        sub.focusIndex shouldBe 0
        sub.focus shouldBe 10
      }

      "should handle focus at last position" in {
        val window = filledWindow(10, 20, 30, 40, 50)
        val sub    = window.subwindowAt(2, 4, 2)

        sub.toVector shouldBe Vector(30, 40, 50)
        sub.focusIndex shouldBe 2
        sub.focus shouldBe 50
      }

      "should preserve element ordering" in {
        val window = filledWindow(1, 2, 3, 4, 5, 6, 7, 8, 9)
        val sub    = window.subwindowAt(3, 4, 3)

        sub.toVector shouldBe Vector(2, 3, 4, 5, 6, 7, 8)
        sub.oldest shouldBe 2
        sub.newest shouldBe 8
      }

    }

    "shrinkReturning" - {

      "should return oldest elements and remaining filled window" in {
        val window               = filledWindow(1, 2, 3, 4, 5)
        val (removed, remaining) = window.shrinkReturning(2)

        removed shouldBe Vector(1, 2)
        remaining shouldBe defined
        remaining.get.toVector shouldBe Vector(3, 4, 5)
      }

      "should return None when shrinking removes all elements" in {
        val window               = filledWindow(1, 2, 3)
        val (removed, remaining) = window.shrinkReturning(3)

        removed shouldBe Vector(1, 2, 3)
        remaining shouldBe None
      }

      "should return None when shrinking exceeds capacity" in {
        val window               = filledWindow(1, 2, 3)
        val (removed, remaining) = window.shrinkReturning(10)

        removed shouldBe Vector(1, 2, 3)
        remaining shouldBe None
      }

      "should reduce capacity of remaining window" in {
        val window         = filledWindow(1, 2, 3, 4, 5)
        val (_, remaining) = window.shrinkReturning(2)

        remaining.get.capacity shouldBe 3
        remaining.get.isAtCapacity shouldBe true
      }

      "should handle shrinking by 0" in {
        val window               = filledWindow(1, 2, 3, 4, 5)
        val (removed, remaining) = window.shrinkReturning(0)

        removed shouldBe empty
        remaining shouldBe defined
        remaining.get.toVector shouldBe Vector(1, 2, 3, 4, 5)
      }

    }

    "pushReturning (NonEmptyFilled)" - {

      "should always return the evicted element" in {
        val window       = filledWindow(1, 2, 3)
        val (evicted, _) = window.pushReturning(4)

        evicted shouldBe 1
      }

      "should return a NonEmptyFilled after push" in {
        val window    = filledWindow(1, 2, 3)
        val (_, next) = window.pushReturning(4)

        next shouldBe a[NonEmptyFilled[?]]
        next.toVector shouldBe Vector(2, 3, 4)
      }

      "should maintain capacity after push" in {
        val window    = filledWindow(1, 2, 3, 4, 5)
        val (_, next) = window.pushReturning(6)

        next.capacity shouldBe 5
        next.length shouldBe 5
      }

      "should evict elements in FIFO order across multiple pushes" in {
        val window   = filledWindow(1, 2, 3)
        val (e1, w1) = window.pushReturning(4)
        val (e2, w2) = w1.pushReturning(5)
        val (e3, _)  = w2.pushReturning(6)

        e1 shouldBe 1
        e2 shouldBe 2
        e3 shouldBe 3
      }

    }

    "toNonEmptyFilled (NonEmptyFilling)" - {

      "should create a filled window from partially filled data" in {
        val filling = NonEmptyWindow(5, 1).enqueue(2).enqueue(3)
        filling shouldBe a[NonEmptyFilling[?]]

        val filled = filling.asInstanceOf[NonEmptyFilling[Int]].toNonEmptyFilled
        filled shouldBe a[NonEmptyFilled[?]]
      }

      "should preserve existing elements" in {
        val filling = NonEmptyWindow(5, 10).enqueue(20).enqueue(30)
        val filled  = filling.asInstanceOf[NonEmptyFilling[Int]].toNonEmptyFilled

        filled.toVector shouldBe Vector(10, 20, 30)
      }

      "should set capacity to current length" in {
        val filling = NonEmptyWindow(10, 1).enqueue(2).enqueue(3)
        val filled  = filling.asInstanceOf[NonEmptyFilling[Int]].toNonEmptyFilled

        filled.capacity shouldBe 3
        filled.isAtCapacity shouldBe true
      }

    }

    "detection pipeline scenario" - {

      case class DataPoint(time: Int, value: Double)

      "should accumulate points during filling phase" in {
        val window = NonEmptyWindow(51, DataPoint(1, 100.0))
          .enqueue(DataPoint(2, 101.0))
          .enqueue(DataPoint(3, 102.0))

        window.length shouldBe 3
        window shouldBe a[NonEmptyFilling[?]]
      }

      "should transition to detection phase when full" in {
        var window: NonEmptyWindow[Int] = NonEmptyWindow(51, 0)
        for i <- 1 to 50 do
          window = window.enqueue(i)

        window.length shouldBe 51
        window shouldBe a[NonEmptyFilled[?]]
      }

      "takeNewest should support smoothing window extraction" in {
        val window          = filledWindow(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val smoothingWindow = window.takeNewest(5)

        smoothingWindow.toVector shouldBe Vector(6, 7, 8, 9, 10)
      }

    }

    "Foldable instance" - {

      "foldLeft should accumulate all elements" in {
        val window = filledWindow(1, 2, 3, 4, 5)
        val result = summon[cats.Foldable[NonEmptyFilled]].foldLeft(window, 0)(_ + _)
        result shouldBe 15
      }

      "foldRight should accumulate all elements" in {
        val window = filledWindow(1, 2, 3)
        val result =
          summon[cats.Foldable[NonEmptyFilled]].foldRight(window, cats.Eval.now(0))((a, acc) => acc.map(_ + a)).value
        result shouldBe 6
      }

      "size should return the window length" in {
        val window = filledWindow("a", "b", "c")
        summon[cats.Foldable[NonEmptyFilled]].size(window) shouldBe 3L
      }

      "foldLeft should work with single element" in {
        val window = filledWindow(42)
        summon[cats.Foldable[NonEmptyFilled]].foldLeft(window, 0)(_ + _) shouldBe 42
      }

    }

  }

  private def filledWindow[A](first: A, rest: A*): NonEmptyFilled[A] =
    val elements = first +: rest
    elements.tail
      .foldLeft(NonEmptyWindow(elements.size, first))((w, e) => w.enqueue(e))
      .asInstanceOf[NonEmptyFilled[A]]

}
