package com.colofabrix.scala.beesight.detection.metrics

import com.colofabrix.scala.beesight.detection.config.{ Altitude, Area, Mass, MetricsConfig }
import com.colofabrix.scala.beesight.detection.metrics.computations.*
import com.colofabrix.scala.beesight.detection.model.{ EventPoint, GeoVector }
import io.github.iltotore.iron.autoRefine
import java.time.Instant
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class MetricsGroundSpec extends AnyFreeSpec with Matchers {

  private val defaultConfig = MetricsConfig(slopeHalfWindow = 4, mass = Mass(90.0), planformArea = Area(10.0))

  "MetricsGround.detect" - {

    "uses the landing event altitude when landing is provided" in {
      val landing = EventPoint(index = 10L, altitude = 35.0, Instant.EPOCH, GeoVector.zero)

      val ground = MetricsGround.detect(Some(landing), defaultConfig)

      ground shouldBe 35.0
    }

    "falls back to config groundElevation when no landing is provided" in {
      val config = defaultConfig.copy(groundElevation = Some(Altitude(42.0)))

      val ground = MetricsGround.detect(None, config)

      ground shouldBe 42.0
    }

    "falls back to sea level (0.0) when neither landing nor config is set" in {
      val ground = MetricsGround.detect(None, defaultConfig)

      ground shouldBe 0.0
    }

    "landing takes priority over config groundElevation" in {
      val landing = EventPoint(index = 5L, altitude = 35.0, Instant.EPOCH, GeoVector.zero)
      val config  = defaultConfig.copy(groundElevation = Some(Altitude(42.0)))

      val ground = MetricsGround.detect(Some(landing), config)

      ground shouldBe 35.0
    }

    "handles None landing and None groundElevation" in {
      val config = defaultConfig.copy(groundElevation = None)

      val ground = MetricsGround.detect(None, config)

      ground shouldBe 0.0
    }
  }

}
