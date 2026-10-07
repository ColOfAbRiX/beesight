package com.colofabrix.scala.beesight.detection.model

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class TurnDirectionSpec extends AnyFreeSpec with Matchers {

  "TurnDirection" - {

    "fromRotation" - {

      "should resolve negative rotations as left turns" in {
        TurnDirection.fromRotation(-95.0) shouldBe TurnDirection.Left
        TurnDirection.fromRotation(-0.5) shouldBe TurnDirection.Left
      }

      "should resolve positive rotations as right turns" in {
        TurnDirection.fromRotation(95.0) shouldBe TurnDirection.Right
        TurnDirection.fromRotation(0.5) shouldBe TurnDirection.Right
      }

      "should resolve a zero rotation as a right turn" in {
        TurnDirection.fromRotation(0.0) shouldBe TurnDirection.Right
      }

    }

  }

}
