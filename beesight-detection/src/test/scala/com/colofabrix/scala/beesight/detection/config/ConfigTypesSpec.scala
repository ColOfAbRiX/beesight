package com.colofabrix.scala.beesight.detection.config

import cats.Show
import cats.syntax.order.*
import io.github.iltotore.iron.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.math.Numeric.Implicits.infixNumericOps

class ConfigTypesSpec extends AnyWordSpec with Matchers {

  "Config opaque types" should {

    "support arithmetic on unconstrained quantities via Numeric" in {
      val a      = Speed(1.5)
      val b      = Speed(2.0)
      val sum    = Speed(3.5)
      val diff   = Speed(0.5)
      val scaled = Speed(3.0)

      a + b shouldBe sum
      b - a shouldBe diff
      a * Speed(2.0) shouldBe scaled
    }

    "support comparisons on unconstrained quantities via Numeric syntax" in {
      val a   = Altitude(100.0)
      val b   = Altitude(200.0)
      val max = Altitude(200.0)
      val min = Altitude(100.0)

      a < b shouldBe true
      a max b shouldBe max
      a min b shouldBe min
    }

    "order quantities with the cats Order instance" in {
      val a        = Percent.zero
      val b        = Percent.one
      val expected = -1

      val actual = a compare b

      actual shouldBe expected
    }

    "order quantities with the Scala Ordering instance" in {
      val a        = WindowSize(1)
      val b        = WindowSize(3)
      val expected = -1

      val actual = summon[Ordering[WindowSize]].compare(a, b)

      actual shouldBe expected
    }

    "expose zero and one constants" in {
      Speed.zero shouldBe Speed(0.0)
      Altitude.zero shouldBe Altitude(0.0)
      Acceleration.zero shouldBe Acceleration(0.0)
      Mass.zero shouldBe Mass(0.0)
      Area.zero shouldBe Area(0.0)
      Angle.zero shouldBe Angle(0.0)
      TurnRate.zero shouldBe TurnRate(0.0)
      KeepPoints.zero shouldBe KeepPoints(0)
      WindowSize.one shouldBe WindowSize(1)
      Percent.zero shouldBe Percent(0.0)
      Percent.one shouldBe Percent(1.0)
    }

    "render quantities with Show" in {
      val angle    = Angle(42.0)
      val turnRate = TurnRate(-1.5)
      val window   = WindowSize(3)

      Show[Angle].show(angle) shouldBe "42.0"
      Show[TurnRate].show(turnRate) shouldBe "-1.5"
      Show[WindowSize].show(window) shouldBe "3"
    }

  }

}
