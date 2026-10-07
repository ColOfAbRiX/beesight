package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.detection.model.GeoVector
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class MedianSpec extends AnyFreeSpec with Matchers {

  "Median" - {

    "of Double values" - {

      "should return the middle element for odd-sized arrays" in {
        val values   = Array(3.0, 1.0, 2.0)
        val expected = 2.0

        val result = values.median

        result shouldBe expected
      }

      "should average the two middle elements for even-sized arrays" in {
        val values   = Array(4.0, 1.0, 3.0, 2.0)
        val expected = 2.5

        val result = values.median

        result shouldBe expected
      }

      "should use the sorted path for arrays larger than 8" in {
        val values   = Array(9.0, 5.0, 1.0, 8.0, 2.0, 6.0, 3.0, 7.0, 4.0)
        val expected = 5.0

        val result = values.median

        result shouldBe expected
      }

      "should handle negative values" in {
        val values   = Array(-1.0, -3.0, -2.0)
        val expected = -2.0

        val result = values.median

        result shouldBe expected
      }

      "should return the single element" in {
        val result = Array(42.0).median

        result shouldBe 42.0
      }

    }

    "of GeoVector values" - {

      "should compute the component-wise median" in {
        val values = Array(
          GeoVector(north = 1.0, east = 10.0, vertical = 100.0),
          GeoVector(north = 3.0, east = 30.0, vertical = 300.0),
          GeoVector(north = 2.0, east = 20.0, vertical = 200.0),
        )
        val expected = GeoVector(north = 2.0, east = 20.0, vertical = 200.0)

        val result = Median[GeoVector].median(values)

        result shouldBe expected
      }

      "should average each component for even-sized inputs" in {
        val values = Array(
          GeoVector(north = 1.0, east = 10.0, vertical = 100.0),
          GeoVector(north = 3.0, east = 30.0, vertical = 300.0),
        )
        val expected = GeoVector(north = 2.0, east = 20.0, vertical = 200.0)

        val result = Median[GeoVector].median(values)

        result shouldBe expected
      }

    }

    "of Fractional values via the foldable extension" - {

      "should compute the median of a Vector of Doubles" in {
        val result = Vector(3.0, 1.0, 2.0).median

        result shouldBe 2.0
      }

      "should average the middle elements for even-sized Vectors" in {
        val result = Vector(1.0, 2.0, 3.0, 4.0).median

        result shouldBe 2.5
      }

      "should compute the median of Floats" in {
        val result = Vector(3.0f, 1.0f, 2.0f).median

        result shouldBe 2.0f
      }

      "should compute the median of BigDecimals" in {
        val result = Vector(BigDecimal(1), BigDecimal(2)).median

        result shouldBe BigDecimal(1.5)
      }

    }

    "medianBy over a foldable window" - {

      "should extract values and compute their median" in {
        val window   = Vector((1, 10.0), (2, 30.0), (3, 20.0))
        val expected = 20.0

        val result = window.medianBy(_._2)

        result shouldBe expected
      }

    }

  }

}
