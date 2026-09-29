package com.colofabrix.scala.beesight.metrics

import com.colofabrix.scala.beesight.metrics.computations.GpsMetrics
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class GpsMetricsSpec extends AnyFreeSpec with Matchers {

  "GpsMetrics" - {

    "sphericalErrorProbable" - {

      "weights the horizontal accuracy double" in {
        GpsMetrics.sphericalErrorProbable(1.0, 2.0) shouldBe 0.5127 * 4.0 +- 1e-12
      }

      "is 0 for a perfect fix" in {
        GpsMetrics.sphericalErrorProbable(0.0, 0.0) shouldBe 0.0
      }

    }

    "speedScoreAccuracy" - {

      "scales the vertical accuracy by sqrt(2) / 3" in {
        GpsMetrics.speedScoreAccuracy(3.0) shouldBe Math.sqrt(2.0) +- 1e-12
      }

      "is 0 for a perfect fix" in {
        GpsMetrics.speedScoreAccuracy(0.0) shouldBe 0.0
      }

    }

  }

}
