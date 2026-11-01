package com.colofabrix.scala.beesight.config

import com.colofabrix.scala.beesight.detection.config.BeesightConfig
import com.monovore.decline.Command as DeclineCommand
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

final class CliParsingSpec extends AnyFreeSpec with Matchers {

  private val cli = DeclineCommand("beesight", "spec")(Config.declineOpts)

  private def parse(args: String*): Either[String, Config] =
    cli.parse(args).left.map(_.toString)

  "Parsing the command line" - {

    "applies the defaults when no command is given" in {
      val config = parse().toOption.get

      config.command shouldBe Command.JumpEvents
      config.detectionConfig shouldBe BeesightConfig.default
      config.dataCutterConfig shouldBe DataCutterConfig.default
    }

    "parses global options before the command" in {
      val config = parse("--input", "data/in", "--dry-run", "swoop").toOption.get

      config.global.input.toString shouldBe "data/in"
      config.global.dryRun shouldBe true
      config.command shouldBe Command.Swoop
    }

    "parses command options after the command" in {
      val config = parse(
        "--input",
        "data/in",
        "analyse",
        "--keep-before-takeoff",
        "10",
        "--keep-after-landing",
        "20",
        "--min-retain-points",
        "0.5",
      ).toOption.get

      config.command shouldBe Command.Analyse
      config.dataCutterConfig.keepBeforeTakeoff.value shouldBe 10
      config.dataCutterConfig.keepAfterLanding.value shouldBe 20
      config.dataCutterConfig.minRetainedPoints.value shouldBe 0.5
    }

    "parses detection overrides on the commands that accept them" in {
      val metrics = parse("metrics", "--detection-config-string", "metrics.slope-half-window = 5").toOption.get

      metrics.command shouldBe Command.Metrics
      metrics.detectionConfig.metrics.slopeHalfWindow shouldBe 5

      val jumpevents = parse("jumpevents", "--detection-config-string", "metrics.slope-half-window = 6").toOption.get

      jumpevents.command shouldBe Command.JumpEvents
      jumpevents.detectionConfig.metrics.slopeHalfWindow shouldBe 6
    }

    "rejects cut options on a command that does not take them" in {
      parse("swoop", "--keep-before-takeoff", "10").isLeft shouldBe true
    }

    "rejects detection options on a command that does not take them" in {
      parse("charts", "--detection-config-string", "metrics.slope-half-window = 5").isLeft shouldBe true
    }

    "rejects global options after the command" in {
      parse("swoop", "--dry-run").isLeft shouldBe true
    }

  }

}
