package com.colofabrix.scala.beesight.detection.math

import com.colofabrix.scala.beesight.detection.model.GeoVector
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HeadingSpec extends AnyWordSpec with Matchers {

  "Heading" should {

    "headingDegrees" should {

      "return 0 deg for a vector pointing North" in {
        Heading.headingDegrees(GeoVector(north = 10.0, east = 0.0, vertical = 0.0)) shouldBe 0.0
      }

      "return 90 deg for a vector pointing East" in {
        Heading.headingDegrees(GeoVector(north = 0.0, east = 10.0, vertical = 0.0)) shouldBe 90.0
      }

      "return 180 deg for a vector pointing South" in {
        Heading.headingDegrees(GeoVector(north = -10.0, east = 0.0, vertical = 0.0)) shouldBe 180.0
      }

      "return 270 deg for a vector pointing West" in {
        Heading.headingDegrees(GeoVector(north = 0.0, east = -10.0, vertical = 0.0)) shouldBe 270.0
      }

      "return 45 deg for a vector pointing NE" in {
        val h = Heading.headingDegrees(GeoVector(north = 10.0, east = 10.0, vertical = 0.0))
        h shouldBe 45.0
      }

      "ignore vertical component" in {
        val h1 = Heading.headingDegrees(GeoVector(north = 10.0, east = 5.0, vertical = 0.0))
        val h2 = Heading.headingDegrees(GeoVector(north = 10.0, east = 5.0, vertical = 100.0))
        h1 shouldBe h2
      }

    }

    "angularDistance" should {

      "return 0 for identical headings" in {
        Heading.angularDistance(90.0, 90.0) shouldBe 0.0
      }

      "return 90 for 90 deg apart" in {
        Heading.angularDistance(0.0, 90.0) shouldBe 90.0
      }

      "return 180 for opposite headings" in {
        Heading.angularDistance(0.0, 180.0) shouldBe 180.0
      }

      "handle wrapping across 0 deg/360 deg" in {
        Heading.angularDistance(350.0, 10.0) shouldBe 20.0
      }

      "handle wrapping in the other direction" in {
        Heading.angularDistance(10.0, 350.0) shouldBe 20.0
      }

      "return 45 for 45 deg apart" in {
        Heading.angularDistance(0.0, 45.0) shouldBe 45.0
      }

      "be symmetric" in {
        Heading.angularDistance(30.0, 120.0) shouldBe Heading.angularDistance(120.0, 30.0)
      }

    }

    "wrapSigned" should {

      "leave deltas already in (-180, 180] unchanged" in {
        Heading.wrapSigned(0.0) shouldBe 0.0
        Heading.wrapSigned(90.0) shouldBe 90.0
        Heading.wrapSigned(-90.0) shouldBe -90.0
        Heading.wrapSigned(180.0) shouldBe 180.0
      }

      "wrap a single turn to the equivalent delta" in {
        Heading.wrapSigned(190.0) shouldBe -170.0
        Heading.wrapSigned(-190.0) shouldBe 170.0
        Heading.wrapSigned(360.0) shouldBe 0.0
        Heading.wrapSigned(-180.0) shouldBe 180.0
      }

      "wrap any number of whole turns to the equivalent delta" in {
        Heading.wrapSigned(720.0) shouldBe 0.0
        Heading.wrapSigned(-720.0) shouldBe 0.0
        Heading.wrapSigned(540.0) shouldBe 180.0
        Heading.wrapSigned(-540.0) shouldBe 180.0
        Heading.wrapSigned(-394.062093) shouldBe -34.062093 +- 1e-9
      }

    }

    "cumulativeHeadingChange" should {

      "return 0 for empty or single-point sequences" in {
        Heading.cumulativeHeadingChange(Vector.empty, 0.0) shouldBe 0.0
        Heading.cumulativeHeadingChange(Vector(GeoVector(10, 10, 0)), 0.0) shouldBe 0.0
      }

      "return 0 for straight-line flight (constant heading)" in {
        val points = Vector.fill(10)(GeoVector(10.0, 0.0, 0.0))
        Heading.cumulativeHeadingChange(points, 0.0) shouldBe 0.0
      }

      "return ~90 deg for a quarter turn" in {
        val points = (0 to 10)
          .map { i =>
            val angle = i.toDouble / 10.0 * Math.PI / 2
            GeoVector(north = 10.0 * Math.cos(angle), east = 10.0 * Math.sin(angle), vertical = 0.0)
          }
          .toVector

        val result = Heading.cumulativeHeadingChange(points, 0.0)
        result shouldBe 90.0 +- 1.0
      }

      "return ~180 deg for a half turn" in {
        val points = (0 to 10)
          .map { i =>
            val angle = i.toDouble / 10.0 * Math.PI
            GeoVector(north = 10.0 * Math.cos(angle), east = 10.0 * Math.sin(angle), vertical = 0.0)
          }
          .toVector

        val result = Heading.cumulativeHeadingChange(points, 0.0)
        result shouldBe 180.0 +- 1.0
      }

      "return ~360 deg for a full turn" in {
        val points = (0 to 20)
          .map { i =>
            val angle = i.toDouble / 20.0 * 2 * Math.PI
            GeoVector(north = 10.0 * Math.cos(angle), east = 10.0 * Math.sin(angle), vertical = 0.0)
          }
          .toVector

        val result = Heading.cumulativeHeadingChange(points, 0.0)
        result shouldBe 360.0 +- 2.0
      }

      "skip points below minHorizontalSpeed" in {
        val points =
          Vector(
            GeoVector(10.0, 0.0, 0.0), // heading 0 deg, horizontal=10
            GeoVector(0.5, 0.0, 0.0),  // heading 0 deg, horizontal=0.5 - below threshold, skipped
            GeoVector(0.0, 10.0, 0.0), // heading 90 deg, horizontal=10
          )

        val result = Heading.cumulativeHeadingChange(points, 2.0)
        result shouldBe 90.0 +- 1.0
      }

      "handle 540 deg turn (1.5 full rotations)" in {
        val points = (0 to 30)
          .map { i =>
            val angle = i.toDouble / 30.0 * 1.5 * 2 * Math.PI
            GeoVector(north = 10.0 * Math.cos(angle), east = 10.0 * Math.sin(angle), vertical = 0.0)
          }
          .toVector

        val result = Heading.cumulativeHeadingChange(points, 0.0)
        result shouldBe 540.0 +- 5.0
      }

    }

  }

}
