package com.colofabrix.scala.beesight.math

import com.colofabrix.scala.beesight.collections.*
import com.colofabrix.scala.beesight.collections.NonEmptyWindow.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class StatsSpec extends AnyFreeSpec with Matchers {

  "Stats" - {

    "stdDevBy" - {

      "should return 0 for identical values" in {
        val window = filledWindow(5.0, 5.0, 5.0, 5.0)
        Stats.stdDevBy(window)(identity) shouldBe 0.0
      }

      "should compute standard deviation for known data" in {
        // values: 2, 4, 4, 4, 5, 5, 7, 9 → mean=5, variance=4, stddev=2
        val window = filledWindow(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        Stats.stdDevBy(window)(identity) shouldBe (2.0 +- 0.01)
      }

      "should return 0 for single element" in {
        val window = filledWindow(42.0)
        Stats.stdDevBy(window)(identity) shouldBe 0.0
      }

      "should handle negative values" in {
        val window = filledWindow(-3.0, -1.0, 1.0, 3.0)
        val result = Stats.stdDevBy(window)(identity)
        result should be > 0.0
      }

    }

    "findInflectionBy" - {

      "decreasing then stable" - {

        "should find transition point with clean data" in {
          //  10, 8, 6, 4, [2], 2, 2, 2, 2
          val window = filledWindow(10.0, 8.0, 6.0, 4.0, 2.0, 2.0, 2.0, 2.0, 2.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 4
        }

        "should find transition point with steep decrease" in {
          // 100, 80, 60, 40, 20, [0], 0, 0, 0
          val window = filledWindow(100.0, 80.0, 60.0, 40.0, 20.0, 0.0, 0.0, 0.0, 0.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 5
        }

      }

      "increasing then stable" - {

        "should find transition point with clean data" in {
          // 2, 4, 6, 8, [10], 10, 10, 10, 10
          val window = filledWindow(2.0, 4.0, 6.0, 8.0, 10.0, 10.0, 10.0, 10.0, 10.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 4
        }

        "should find transition with gradual increase" in {
          // 0, 1, 2, 3, 4, [5], 5, 5, 5, 5, 5
          val window = filledWindow(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 5
        }

      }

      "stable then decreasing" - {

        "should find transition point with clean data" in {
          // 10, 10, 10, 10, [10], 8, 6, 4, 2
          val window = filledWindow(10.0, 10.0, 10.0, 10.0, 10.0, 8.0, 6.0, 4.0, 2.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 4
        }

        "should find transition with steep drop" in {
          // 50, 50, 50, [50], 40, 30, 20, 10, 0
          val window = filledWindow(50.0, 50.0, 50.0, 50.0, 40.0, 30.0, 20.0, 10.0, 0.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 3
        }

      }

      "stable then increasing" - {

        "should find transition point with clean data" in {
          // 0, 0, 0, 0, [0], 2, 4, 6, 8
          val window = filledWindow(0.0, 0.0, 0.0, 0.0, 0.0, 2.0, 4.0, 6.0, 8.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 4
        }

        "should find transition with steep rise" in {
          // 5, 5, 5, [5], 15, 25, 35, 45
          val window = filledWindow(5.0, 5.0, 5.0, 5.0, 15.0, 25.0, 35.0, 45.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 3
        }

      }

      "with noisy data" - {

        "should find inflection in noisy decreasing-then-stable data" in {
          val window = filledWindow(
            10.2, 8.1, 6.3, 3.8, 2.1, 1.9, 2.1, 2.0, 1.8, 2.2,
          )
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 3 and be <= 5)
        }

        "should find inflection in noisy stable-then-increasing data" in {
          val window = filledWindow(
            5.1, 4.9, 5.2, 4.8, 5.0, 7.1, 8.9, 11.2, 12.8, 15.1,
          )
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 3 and be <= 5)
        }

        "should find inflection in noisy increasing-then-stable data" in {
          val window = filledWindow(
            1.1, 2.9, 5.2, 6.8, 9.1, 10.2, 9.8, 10.1, 10.3, 9.9,
          )
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 4 and be <= 6)
        }

        "should find inflection in noisy stable-then-decreasing data" in {
          val window = filledWindow(
            10.1, 9.9, 10.2, 10.0, 9.8, 8.1, 5.9, 4.2, 1.8, 0.1,
          )
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 3 and be <= 5)
        }

      }

      "asymmetric segments" - {

        "should find inflection near the start" in {
          // Short decrease, long stable
          val window = filledWindow(10.0, 5.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 2
        }

        "should find inflection near the end" in {
          // Long stable, short increase
          val window = filledWindow(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 10.0, 15.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 6 and be <= 7)
        }

      }

      "negative values" - {

        "should handle negative decreasing-then-stable" in {
          // -2, -4, -6, -8, [-10], -10, -10, -10
          val window = filledWindow(-2.0, -4.0, -6.0, -8.0, -10.0, -10.0, -10.0, -10.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 4
        }

        "should handle crossing zero" in {
          // 4, 2, 0, -2, [-4], -4, -4, -4
          val window = filledWindow(4.0, 2.0, 0.0, -2.0, -4.0, -4.0, -4.0, -4.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 4
        }

      }

      "edge cases" - {

        "should return None for window of size 3" in {
          val window = filledWindow(1.0, 2.0, 3.0)
          Stats.findInflectionBy(window)(identity) shouldBe None
        }

        "should return Some for minimum window of size 4" in {
          val window = filledWindow(10.0, 5.0, 0.0, 0.0)
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get shouldBe 2
        }

        "should handle perfectly constant data" in {
          val window = filledWindow(5.0, 5.0, 5.0, 5.0, 5.0, 5.0)
          val result = Stats.findInflectionBy(window)(identity)

          // All splits have zero error, should still return a valid index
          result shouldBe defined
        }

        "should handle purely linear data" in {
          val window = filledWindow(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
          val result = Stats.findInflectionBy(window)(identity)

          // No real inflection but should return a valid index
          result shouldBe defined
        }

        "should handle large values" in {
          val window = filledWindow(
            1e6, 1e6, 1e6, 1e6, 1e6, 2e6, 3e6, 4e6, 5e6,
          )
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 4 and be <= 5)
        }

        "should handle very small differences" in {
          val window = filledWindow(
            1.0001, 1.0002, 1.0003, 1.0004, 1.0005, 1.0005, 1.0005, 1.0005,
          )
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 3 and be <= 5)
        }

      }

      "flight data scenarios" - {

        "freefall onset: stable altitude then rapid descent speed" in {
          // Vertical speed: near-zero, then rapidly increasing
          val window = filledWindow(
            0.5, 0.3, 0.8, 0.2, 0.6, 5.0, 15.0, 30.0, 45.0, 55.0,
          )
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 4 and be <= 6)
        }

        "canopy opening: high descent speed then moderate" in {
          // Vertical speed decreasing from freefall to canopy speed
          val window = filledWindow(
            55.0, 54.0, 53.0, 50.0, 40.0, 15.0, 14.0, 13.0, 13.0, 13.0,
          )
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 5 and be <= 7)
        }

        "landing: canopy speed then near-zero" in {
          // Vertical speed decreasing to near-zero
          val window = filledWindow(
            13.0, 12.0, 10.0, 5.0, 1.0, 0.2, 0.1, 0.0, 0.1, 0.0,
          )
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 3 and be <= 5)
        }

        "takeoff: ground then climbing" in {
          // Horizontal speed: zero then increasing
          val window = filledWindow(
            0.0, 0.1, 0.0, 0.1, 0.2, 5.0, 15.0, 25.0, 35.0, 40.0,
          )
          val result = Stats.findInflectionBy(window)(identity)

          result shouldBe defined
          result.get should (be >= 4 and be <= 6)
        }

      }

      "symmetry properties" - {

        "mirrored data should yield symmetric inflection points" in {
          val increasing = filledWindow(0.0, 2.0, 4.0, 6.0, 8.0, 8.0, 8.0, 8.0, 8.0)
          val decreasing = filledWindow(8.0, 8.0, 8.0, 8.0, 8.0, 6.0, 4.0, 2.0, 0.0)

          val incResult = Stats.findInflectionBy(increasing)(identity)
          val decResult = Stats.findInflectionBy(decreasing)(identity)

          incResult shouldBe defined
          decResult shouldBe defined
          // Inflection in increasing should mirror inflection in decreasing
          incResult.get + decResult.get shouldBe (increasing.capacity - 1)
        }

        "vertically shifted data should yield same inflection index" in {
          val base    = filledWindow(0.0, 0.0, 0.0, 0.0, 2.0, 4.0, 6.0, 8.0)
          val shifted = filledWindow(100.0, 100.0, 100.0, 100.0, 102.0, 104.0, 106.0, 108.0)

          val baseResult    = Stats.findInflectionBy(base)(identity)
          val shiftedResult = Stats.findInflectionBy(shifted)(identity)

          baseResult shouldBe shiftedResult
        }

        "scaled data should yield same inflection index" in {
          val base   = filledWindow(0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 3.0, 4.0)
          val scaled = filledWindow(0.0, 0.0, 0.0, 0.0, 10.0, 20.0, 30.0, 40.0)

          val baseResult   = Stats.findInflectionBy(base)(identity)
          val scaledResult = Stats.findInflectionBy(scaled)(identity)

          baseResult shouldBe scaledResult
        }

      }

    }

  }

  private def filledWindow(first: Double, rest: Double*): NonEmptyFilled[Double] =
    val elements = first +: rest
    elements.tail
      .foldLeft(NonEmptyWindow(elements.size, first))((w, e) => w.enqueue(e))
      .asInstanceOf[NonEmptyFilled[Double]]

}
