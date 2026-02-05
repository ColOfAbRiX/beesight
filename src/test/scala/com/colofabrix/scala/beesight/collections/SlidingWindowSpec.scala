package com.colofabrix.scala.beesight.collections

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SlidingWindowSpec extends AnyFreeSpec with Matchers {

  "SlidingWindow" - {

    "creation" - {

      "should start as FillingWindow" in {
        val window = SlidingWindow[Int](3)
        window shouldBe a[SlidingWindow.FillingWindow[?]]
      }

      "should have specified capacity" in {
        val window = SlidingWindow[Int](5)
        window.capacity shouldBe 5
      }

      "should enforce minimum capacity of 1" in {
        val window = SlidingWindow[Int](0)
        window.capacity shouldBe 1
      }

    }

    "FillingWindow" - {

      "should start empty" in {
        val window = SlidingWindow[Int](3)
        window.length shouldBe 0
        window.toVector shouldBe Vector.empty
      }

      "should grow as elements are added" in {
        val window = SlidingWindow[Int](3)
          .enqueue(1)
          .enqueue(2)

        window.length shouldBe 2
        window.toVector shouldBe Vector(1, 2)
      }

      "push should return None while filling" in {
        val window         = SlidingWindow[Int](3)
        val (popped, next) = window.push(1)

        popped shouldBe None
        next.length shouldBe 1
      }

      "should transition to FilledWindow when capacity reached" in {
        val window = SlidingWindow[Int](3)
          .enqueue(1)
          .enqueue(2)
          .enqueue(3)

        window shouldBe a[SlidingWindow.FilledWindow[?]]
        window.length shouldBe 3
      }

    }

    "FilledWindow" - {

      "should maintain capacity after filling" in {
        val window = filledWindow(1, 2, 3)
        window.capacity shouldBe 3
        window.length shouldBe 3
      }

      "push should return oldest element" in {
        val window         = filledWindow(1, 2, 3)
        val (popped, next) = window.push(4)

        popped shouldBe Some(1)
        next.toVector shouldBe Vector(2, 3, 4)
      }

      "push should maintain capacity" in {
        val window    = filledWindow(1, 2, 3)
        val (_, next) = window.push(4)

        next.length shouldBe 3
      }

      "oldest should return first element" in {
        val window = filledWindow(1, 2, 3)
        window.oldest shouldBe 1
      }

      "newest should return last element" in {
        val window = filledWindow(1, 2, 3)
        window.newest shouldBe 3
      }

      "oldest and newest should be same for capacity 1" in {
        val window = SlidingWindow[Int](1).enqueue(42)
          .asInstanceOf[SlidingWindow.FilledWindow[Int]]

        window.oldest shouldBe 42
        window.newest shouldBe 42
      }

    }

    "focusAt" - {

      "should focus at index 0 (oldest)" in {
        val window  = filledWindow(1, 2, 3, 4, 5)
        val focused = window.focusAt(0)

        focused.focus shouldBe 1
        focused.focusIndex shouldBe 0
      }

      "should focus at index 1 (second oldest)" in {
        val window  = filledWindow(1, 2, 3, 4, 5)
        val focused = window.focusAt(1)

        focused.focus shouldBe 2
        focused.focusIndex shouldBe 1
      }

      "should focus at last index (newest)" in {
        val window  = filledWindow(1, 2, 3, 4, 5)
        val focused = window.focusAt(4)

        focused.focus shouldBe 5
        focused.focusIndex shouldBe 4
      }

      "should clip negative index to 0" in {
        val window  = filledWindow(1, 2, 3)
        val focused = window.focusAt(-1)

        focused.focus shouldBe 1
        focused.focusIndex shouldBe 0
      }

      "should clip excessive index to last" in {
        val window  = filledWindow(1, 2, 3)
        val focused = window.focusAt(10)

        focused.focus shouldBe 3
        focused.focusIndex shouldBe 2
      }

      "should preserve all elements after focus change" in {
        val window  = filledWindow(1, 2, 3, 4, 5)
        val focused = window.focusAt(2)

        focused.toVector shouldBe Vector(1, 2, 3, 4, 5)
      }

    }

    "modifyFocus" - {

      "should modify focused element" in {
        val window   = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        val modified = window.modifyFocus(_ * 10)

        modified.focus shouldBe 30
        modified.toVector shouldBe Vector(1, 2, 30, 4, 5)
      }

      "should modify oldest when focused at 0" in {
        val window   = filledWindow(1, 2, 3).focusAt(0)
        val modified = window.modifyFocus(_ + 100)

        modified.focus shouldBe 101
        modified.toVector shouldBe Vector(101, 2, 3)
        modified.oldest shouldBe 101
      }

      "should modify second oldest when focused at 1" in {
        val window   = filledWindow(10, 20, 30, 40, 50).focusAt(1)
        val modified = window.modifyFocus(_ => 999)

        modified.focus shouldBe 999
        modified.toVector shouldBe Vector(10, 999, 30, 40, 50)
      }

      "should preserve focus index after modification" in {
        val window   = filledWindow(1, 2, 3, 4, 5).focusAt(3)
        val modified = window.modifyFocus(_ * 2)

        modified.focusIndex shouldBe 3
      }

    }

    "despiking scenario simulation" - {

      "focusAt(1) should target second oldest for despiking" in {
        // Window: [oldest, secondOldest, ..., newest]
        // Despiking checks secondOldest at index 1
        val window  = filledWindow("t1", "t2", "t3", "t4", "t5")
        val focused = window.focusAt(1)

        focused.focus shouldBe "t2"
        focused.oldest shouldBe "t1"
        focused.newest shouldBe "t5"
      }

      "modifyFocus after focusAt(1) should modify second oldest" in {
        val window   = filledWindow(10.0, 20.0, 30.0, 40.0, 50.0).focusAt(1)
        val despiked = window.modifyFocus(_ => 15.0) // Interpolated value

        despiked.toVector shouldBe Vector(10.0, 15.0, 30.0, 40.0, 50.0)
      }

      "after despiking, oldest and newest should be unchanged" in {
        val window   = filledWindow(10.0, 100.0, 30.0, 40.0, 50.0)
        val focused  = window.focusAt(1)
        val despiked = focused.modifyFocus(_ => 25.0)

        despiked.oldest shouldBe 10.0
        despiked.newest shouldBe 50.0
      }

      "push after modify should slide window correctly" in {
        val window      = filledWindow(10.0, 100.0, 30.0, 40.0, 50.0)
        val focused     = window.focusAt(1)
        val despiked    = focused.modifyFocus(_ => 25.0)
        val (out, next) = despiked.push(60.0)

        out shouldBe Some(10.0)
        next.toVector shouldBe Vector(25.0, 30.0, 40.0, 50.0, 60.0)
      }

    }

    "oldest and newest after operations" - {

      "oldest should be correct after multiple pushes" in {
        val window  = filledWindow(1, 2, 3)
        val (_, w1) = window.push(4)
        val (_, w2) = w1.push(5)

        w2.asInstanceOf[SlidingWindow.FilledWindow[Int]].oldest shouldBe 3
      }

      "newest should be correct after multiple pushes" in {
        val window  = filledWindow(1, 2, 3)
        val (_, w1) = window.push(4)
        val (_, w2) = w1.push(5)

        w2.asInstanceOf[SlidingWindow.FilledWindow[Int]].newest shouldBe 5
      }

    }

    "toVector and toList" - {

      "should return elements in order for FillingWindow" in {
        val window =
          SlidingWindow[Int](5)
            .enqueue(1)
            .enqueue(2)
            .enqueue(3)

        window.toVector shouldBe Vector(1, 2, 3)
        window.toList shouldBe List(1, 2, 3)
      }

      "should return elements in order for FilledWindow" in {
        val window = filledWindow(1, 2, 3, 4, 5)

        window.toVector shouldBe Vector(1, 2, 3, 4, 5)
        window.toList shouldBe List(1, 2, 3, 4, 5)
      }

    }

  }

  private def filledWindow[A](elements: A*): SlidingWindow.FilledWindow[A] =
    elements
      .foldLeft(SlidingWindow[A](elements.size))((w, e) => w.enqueue(e))
      .asInstanceOf[SlidingWindow.FilledWindow[A]]

}
