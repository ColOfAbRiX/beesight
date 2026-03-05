package com.colofabrix.scala.beesight.model

import cats.Order
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import com.colofabrix.scala.beesight.model.GeoVector
import com.colofabrix.scala.beesight.model.GeoVector.*

class GeoVectorSpec extends AnyFreeSpec with Matchers {

  "GeoVector" - {

    "zero" - {

      "should have all components equal to zero" in {
        GeoVector.zero shouldBe GeoVector(0.0, 0.0, 0.0)
      }

      "should have zero magnitude" in {
        GeoVector.zero.magnitude shouldBe 0.0
      }

      "should have zero horizontal component" in {
        GeoVector.zero.horizontal shouldBe 0.0
      }

    }

    "addition" - {

      "should add corresponding components" in {
        val v1 = GeoVector(1.0, 2.0, 3.0)
        val v2 = GeoVector(4.0, 5.0, 6.0)
        (v1 + v2) shouldBe GeoVector(5.0, 7.0, 9.0)
      }

      "should be commutative" in {
        val v1 = GeoVector(1.0, 2.0, 3.0)
        val v2 = GeoVector(4.0, 5.0, 6.0)
        (v1 + v2) shouldBe (v2 + v1)
      }

      "should have zero as identity" in {
        val v = GeoVector(3.0, 4.0, 5.0)
        (v + GeoVector.zero) shouldBe v
      }

      "should handle negative components" in {
        val v1 = GeoVector(-1.0, -2.0, -3.0)
        val v2 = GeoVector(1.0, 2.0, 3.0)
        (v1 + v2) shouldBe GeoVector.zero
      }

    }

    "subtraction" - {

      "should subtract corresponding components" in {
        val v1 = GeoVector(5.0, 7.0, 9.0)
        val v2 = GeoVector(1.0, 2.0, 3.0)
        (v1 - v2) shouldBe GeoVector(4.0, 5.0, 6.0)
      }

      "should yield zero when subtracting self" in {
        val v = GeoVector(3.0, 4.0, 5.0)
        (v - v) shouldBe GeoVector.zero
      }

      "should handle negative results" in {
        val v1 = GeoVector(1.0, 2.0, 3.0)
        val v2 = GeoVector(5.0, 7.0, 9.0)
        (v1 - v2) shouldBe GeoVector(-4.0, -5.0, -6.0)
      }

    }

    "scalar multiplication" - {

      "should multiply all components" in {
        val v = GeoVector(1.0, 2.0, 3.0)
        (v * 2.0) shouldBe GeoVector(2.0, 4.0, 6.0)
      }

      "should handle zero scalar" in {
        val v = GeoVector(3.0, 4.0, 5.0)
        (v * 0.0) shouldBe GeoVector.zero
      }

      "should handle negative scalar" in {
        val v = GeoVector(1.0, 2.0, 3.0)
        (v * -1.0) shouldBe GeoVector(-1.0, -2.0, -3.0)
      }

      "should have 1.0 as identity" in {
        val v = GeoVector(3.0, 4.0, 5.0)
        (v * 1.0) shouldBe v
      }

    }

    "scalar division" - {

      "should divide all components" in {
        val v = GeoVector(4.0, 6.0, 8.0)
        (v / 2.0) shouldBe GeoVector(2.0, 3.0, 4.0)
      }

      "should handle negative divisor" in {
        val v = GeoVector(4.0, 6.0, 8.0)
        (v / -2.0) shouldBe GeoVector(-2.0, -3.0, -4.0)
      }

      "should produce Infinity when dividing by zero" in {
        val v = GeoVector(1.0, 2.0, 3.0)
        val result = v / 0.0
        result.north shouldBe Double.PositiveInfinity
      }

      "should have 1.0 as identity" in {
        val v = GeoVector(3.0, 4.0, 5.0)
        (v / 1.0) shouldBe v
      }

    }

    "magnitude" - {

      "should compute 3D Euclidean norm" in {
        val v = GeoVector(3.0, 4.0, 0.0)
        v.magnitude shouldBe 5.0
      }

      "should be zero for zero vector" in {
        GeoVector.zero.magnitude shouldBe 0.0
      }

      "should handle single non-zero component" in {
        GeoVector(0.0, 0.0, 7.0).magnitude shouldBe 7.0
      }

      "should handle negative components (same as positive)" in {
        val v1 = GeoVector(3.0, 4.0, 0.0)
        val v2 = GeoVector(-3.0, -4.0, 0.0)
        v1.magnitude shouldBe v2.magnitude
      }

      "should compute correctly for known 3D vector" in {
        // sqrt(1^2 + 2^2 + 2^2) = sqrt(9) = 3
        GeoVector(1.0, 2.0, 2.0).magnitude shouldBe 3.0
      }

    }

    "horizontal" - {

      "should compute 2D norm of north and east only" in {
        val v = GeoVector(3.0, 4.0, 100.0)
        v.horizontal shouldBe 5.0
      }

      "should be zero when north and east are zero" in {
        GeoVector(0.0, 0.0, 50.0).horizontal shouldBe 0.0
      }

      "should ignore vertical component" in {
        val v1 = GeoVector(3.0, 4.0, 0.0)
        val v2 = GeoVector(3.0, 4.0, 999.0)
        v1.horizontal shouldBe v2.horizontal
      }

    }

    "magnitude comparisons" - {

      "less than should compare by magnitude" in {
        val small = GeoVector(1.0, 0.0, 0.0)
        (small < 5.0) shouldBe true
        (small < 0.5) shouldBe false
      }

      "less than or equal should compare by magnitude" in {
        val v = GeoVector(3.0, 4.0, 0.0) // magnitude = 5
        (v <= 5.0) shouldBe true
        (v <= 5.1) shouldBe true
        (v <= 4.9) shouldBe false
      }

      "greater than or equal should compare by magnitude" in {
        val v = GeoVector(3.0, 4.0, 0.0) // magnitude = 5
        (v >= 5.0) shouldBe true
        (v >= 4.9) shouldBe true
        (v >= 5.1) shouldBe false
      }

      "greater than should compare by magnitude" in {
        val large = GeoVector(10.0, 0.0, 0.0)
        (large > 5.0) shouldBe true
        (large > 15.0) shouldBe false
      }

    }

    "Order and Ordering instances" - {

      "Order should compare by magnitude" in {
        val small = GeoVector(1.0, 0.0, 0.0)
        val large = GeoVector(3.0, 4.0, 0.0)
        Order[GeoVector].compare(small, large) should be < 0
        Order[GeoVector].compare(large, small) should be > 0
      }

      "Order should return 0 for equal magnitude vectors" in {
        val v1 = GeoVector(3.0, 4.0, 0.0)
        val v2 = GeoVector(0.0, 5.0, 0.0)
        Order[GeoVector].compare(v1, v2) shouldBe 0
      }

      "Ordering should be consistent with Order" in {
        val vectors = List(
          GeoVector(10.0, 0.0, 0.0),
          GeoVector(1.0, 0.0, 0.0),
          GeoVector(5.0, 0.0, 0.0),
        )
        val sorted = vectors.sorted
        sorted.map(_.magnitude) shouldBe List(1.0, 5.0, 10.0)
      }

      "Ordering should sort vectors by magnitude" in {
        val vectors = List(
          GeoVector(0.0, 0.0, 3.0),
          GeoVector(1.0, 0.0, 0.0),
          GeoVector(1.0, 1.0, 1.0),
        )
        val magnitudes = vectors.sorted.map(_.magnitude)
        magnitudes shouldBe magnitudes.sorted
      }

    }

  }

}
