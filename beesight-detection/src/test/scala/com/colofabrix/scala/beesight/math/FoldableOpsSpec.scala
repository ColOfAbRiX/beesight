package com.colofabrix.scala.beesight.math

import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class FoldableOpsSpec extends AnyFreeSpec with Matchers {

  "toArrayBy" - {

    "should extract values in iteration order" in {
      val input    = Vector(1, 2, 3)
      val expected = Vector(1.0, 2.0, 3.0)

      val result = input.toArrayBy(_.toDouble)

      result.toVector shouldBe expected
    }

    "should apply the extractor to every element" in {
      val input    = List("a", "bb", "ccc")
      val expected = Vector(1, 2, 3)

      val result = input.toArrayBy(_.length)

      result.toVector shouldBe expected
    }

    "should return an empty array for an empty foldable" in {
      val result = Vector.empty[Int].toArrayBy(_.toDouble)

      result.length shouldBe 0
    }

    "should work on non-standard Foldables" in {
      val window   = filledWindow(5.0, 1.0, 3.0)
      val expected = Vector(5.0, 1.0, 3.0)

      val result = window.toArrayBy(identity)

      result.toVector shouldBe expected
    }

  }

  private def filledWindow(first: Double, rest: Double*): NonEmptyFilled[Double] =
    val elements = first +: rest
    elements
      .tail
      .foldLeft(NonEmptyWindow(elements.size, first))((w, e) => w.enqueue(e))
      .asInstanceOf[NonEmptyFilled[Double]]

}
