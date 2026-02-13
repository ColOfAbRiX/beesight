package com.colofabrix.scala.beesight.collections

import cats.syntax.all.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SlidingWindowSpec extends AnyFreeSpec with Matchers {

  "SWindow" - {

    "creation" - {

      "should start as FillingWindow" in {
        val window =
          SWindow[Int](3)
        window shouldBe a[SWindow.FillingWindow[?]]
      }

      "should have specified capacity" in {
        val window = SWindow[Int](5)
        window.capacity shouldBe 5
      }

      "should enforce minimum capacity of 1" in {
        val window = SWindow[Int](0)
        window.capacity shouldBe 1
      }

    }

    "Filling" - {

      "should start empty" in {
        val window =
          SWindow[Int](3)
        window.length shouldBe 0
        window.toVector shouldBe Vector.empty
      }

      "should grow as elements are added" in {
        val window =
          SWindow[Int](3)
            .enqueue(1)
            .enqueue(2)

        window.length shouldBe 2
        window.toVector shouldBe Vector(1, 2)
      }

      "push should return None while filling" in {
        val window         = SWindow[Int](3)
        val (popped, next) = window.push(1)

        popped shouldBe None
        next.length shouldBe 1
      }

      "push should return None when reached capacity" in {
        val window           = SWindow[Int](2)
        val (popped1, next1) = window.push(1)
        val (popped2, next2) = next1.push(2)

        popped2 shouldBe None
        next2.length shouldBe 2
      }

      "should transition to FilledWindow when capacity reached" in {
        val window =
          SWindow[Int](3)
            .enqueue(1)
            .enqueue(2)
            .enqueue(3)

        window shouldBe a[SWindow.FilledWindow[?]]
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
        val window =
          SWindow[Int](1)
            .enqueue(42)
            .asInstanceOf[SWindow.FilledWindow[Int]]

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

        w2.asInstanceOf[SWindow.FilledWindow[Int]].oldest shouldBe 3
      }

      "newest should be correct after multiple pushes" in {
        val window  = filledWindow(1, 2, 3)
        val (_, w1) = window.push(4)
        val (_, w2) = w1.push(5)

        w2.asInstanceOf[SWindow.FilledWindow[Int]].newest shouldBe 5
      }

    }

    "filledSubwindow(start, count)" - {

      "should return elements from start to the right with positive count" in {
        val window    = filledWindow(1, 2, 3, 4, 5)
        val subwindow = window.filledSubwindow(1, 3) // start=1, count=3

        subwindow.isDefined shouldBe true
        subwindow.get.toVector shouldBe Vector(2, 3, 4)
      }

      "should return elements from start to the left with negative count" in {
        val window    = filledWindow(1, 2, 3, 4, 5)
        val subwindow = window.filledSubwindow(3, -3) // start=3, count=-3

        subwindow.isDefined shouldBe true
        subwindow.get.toVector shouldBe Vector(2, 3, 4)
      }

      "should return None for out of bounds start" in {
        val window = filledWindow(1, 2, 3, 4, 5)

        window.filledSubwindow(10, 3).isDefined shouldBe false
        window.filledSubwindow(-1, 3).isDefined shouldBe false
      }

      "should return None for zero count" in {
        val window = filledWindow(1, 2, 3, 4, 5)
        window.filledSubwindow(2, 0).isDefined shouldBe false
      }

      "should clip count at window boundary (right)" in {
        val window    = filledWindow(1, 2, 3, 4, 5)
        val subwindow = window.filledSubwindow(3, 10) // start=3, asks for 10 but only 2 available

        subwindow.isDefined shouldBe true
        subwindow.get.toVector shouldBe Vector(4, 5)
      }

      "should clip count at window boundary (left)" in {
        val window    = filledWindow(1, 2, 3, 4, 5)
        val subwindow = window.filledSubwindow(1, -10) // start=1, asks for -10 but only 2 available

        subwindow.isDefined shouldBe true
        subwindow.get.toVector shouldBe Vector(1, 2)
      }

    }

    "filledSubwindow(count) from focus" - {

      "should return focus and elements to the right with positive count" in {
        val window    = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        val subwindow = window.filledSubwindow(3) // count=3: focus(3) + 2 to right

        subwindow.isDefined shouldBe true
        subwindow.get.toVector shouldBe Vector(3, 4, 5)
      }

      "should return focus and elements to the left with negative count" in {
        val window    = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        val subwindow = window.filledSubwindow(-3) // count=-3: 2 to left + focus(3)

        subwindow.isDefined shouldBe true
        subwindow.get.toVector shouldBe Vector(1, 2, 3)
      }

      "should return just focus with count=1" in {
        val window    = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        val subwindow = window.filledSubwindow(1)

        subwindow.isDefined shouldBe true
        subwindow.get.toVector shouldBe Vector(3)
      }

      "should return just focus with count=-1" in {
        val window    = filledWindow(1, 2, 3, 4, 5).focusAt(2)
        val subwindow = window.filledSubwindow(-1)

        subwindow.isDefined shouldBe true
        subwindow.get.toVector shouldBe Vector(3)
      }

      "should clip at right boundary" in {
        val window    = filledWindow(1, 2, 3, 4, 5).focusAt(4) // focus on 5
        val subwindow = window.filledSubwindow(5)              // asks for 5, only 1 available

        subwindow.isDefined shouldBe true
        subwindow.get.toVector shouldBe Vector(5)
      }

      "should clip at left boundary" in {
        val window    = filledWindow(1, 2, 3, 4, 5).focusAt(0) // focus on 1
        val subwindow = window.filledSubwindow(-5)             // asks for -5, only 1 available

        subwindow.isDefined shouldBe true
        subwindow.get.toVector shouldBe Vector(1)
      }

      "should return None for FillingWindow (no focus)" in {
        val window = SWindow[Int](5).enqueue(1).enqueue(2).enqueue(3)
        window.filledSubwindow(2) shouldBe None
      }

    }

    "toVector" - {

      "should return elements in order for FillingWindow" in {
        val window =
          SWindow[Int](5)
            .enqueue(1)
            .enqueue(2)
            .enqueue(3)

        window.toVector shouldBe Vector(1, 2, 3)
      }

      "should return elements in order for FilledWindow" in {
        val window = filledWindow(1, 2, 3, 4, 5)

        window.toVector shouldBe Vector(1, 2, 3, 4, 5)
      }

    }

    "takeNewest" - {

      "FilledWindow" - {

        "should return the newest i elements" in {
          val window = filledWindow(1, 2, 3, 4, 5)
          val result = window.takeNewest(3)

          result.isDefined shouldBe true
          result.get.toVector shouldBe Vector(3, 4, 5)
        }

        "should return all elements when i >= capacity" in {
          val window = filledWindow(1, 2, 3, 4, 5)
          val result = window.takeNewest(10)

          result.isDefined shouldBe true
          result.get.toVector shouldBe Vector(1, 2, 3, 4, 5)
        }

        "should return at least 1 element when i <= 0" in {
          val window = filledWindow(1, 2, 3, 4, 5)
          val result = window.takeNewest(0)

          result.isDefined shouldBe true
          result.get.toVector shouldBe Vector(5)
        }

        "should return single newest element when i = 1" in {
          val window = filledWindow(1, 2, 3, 4, 5)
          val result = window.takeNewest(1)

          result.isDefined shouldBe true
          result.get.toVector shouldBe Vector(5)
        }

        "should set focus to last element (newest)" in {
          val window = filledWindow(1, 2, 3, 4, 5)
          val result = window.takeNewest(3).get

          result.focusIndex shouldBe 2
          result.focus shouldBe 5
        }

      }

      "FillingWindow" - {

        "should return newest elements when window has elements" in {
          val window = SWindow[Int](5).enqueue(1).enqueue(2).enqueue(3)
          val result = window.takeNewest(2)

          result.isDefined shouldBe true
          result.get.toVector shouldBe Vector(2, 3)
        }

        "should return None when window is empty" in {
          val window = SWindow[Int](5)
          val result = window.takeNewest(3)

          result.isDefined shouldBe false
        }

        "should return at least 1 element when i <= 0 and window not empty" in {
          val window = SWindow[Int](5).enqueue(1).enqueue(2).enqueue(3)
          val result = window.takeNewest(0)

          result.isDefined shouldBe true
          result.get.toVector shouldBe Vector(3)
        }

        "should return all elements when i > length" in {
          val window = SWindow[Int](5).enqueue(1).enqueue(2).enqueue(3)
          val result = window.takeNewest(10)

          result.isDefined shouldBe true
          result.get.toVector shouldBe Vector(1, 2, 3)
        }

      }

    }

  }

  private def filledWindow[A](elements: A*): SWindow.FilledWindow[A] =
    elements
      .foldLeft(SWindow[A](elements.size))((w, e) => w.enqueue(e))
      .asInstanceOf[SWindow.FilledWindow[A]]

}
