package com.colofabrix.scala.beesight.output

import java.nio.file.Paths
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class WorkingPathsSpec extends AnyWordSpec, Matchers {

  private val inputFile  = Paths.get("data", "jumps", "jump_1002.csv")
  private val outputFile = Paths.get("out", "jumps", "jump_1002.csv")
  private val paths      = new WorkingPaths(inputFile, outputFile)

  "WorkingPaths" should {

    "place the chart next to the processed CSV" in {
      val expected = Paths.get("out", "jumps", "jump_1002.html")

      val actual = paths.chartHtml

      actual shouldBe expected
    }

    "name the FlySight conversion after the processed CSV" in {
      val expected = Paths.get("out", "jumps", "jump_1002.flysight.csv")

      val actual = paths.flysightCsv

      actual shouldBe expected
    }

    "name the metrics export after the processed CSV" in {
      val expected = Paths.get("out", "jumps", "jump_1002.metrics.csv")

      val actual = paths.metricsCsv

      actual shouldBe expected
    }

    "name the swoop report after the processed CSV" in {
      val expected = Paths.get("out", "jumps", "jump_1002.swoop.json")

      val actual = paths.swoopJson

      actual shouldBe expected
    }

    "place the sidecar next to the input file keeping its full name" in {
      val expected = Paths.get("data", "jumps", "jump_1002.csv.meta")

      val actual = paths.sidecar

      actual shouldBe expected
    }

  }

}
