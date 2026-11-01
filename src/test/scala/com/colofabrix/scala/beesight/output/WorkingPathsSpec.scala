package com.colofabrix.scala.beesight.output

import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.IOConfigValues
import com.colofabrix.scala.beesight.config.{ Config, GlobalOptions }
import java.nio.file.{ Files, Path, Paths }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

final class WorkingPathsSpec extends AnyFreeSpec with Matchers with IOConfigValues {

  private val inputFile  = Paths.get("data", "jumps", "jump_1002.csv")
  private val outputFile = Paths.get("out", "jumps", "jump_1002.csv")
  private val paths      = new WorkingPaths(inputFile, outputFile)

  private def applyFor(input: Path, output: Option[Path]): Path = {
    val config = Config.default.copy(global = GlobalOptions.default.copy(input = input, output = output))
    WorkingPaths(input).result(config).outputFile
  }

  private def inTempWorkspace(name: String): (Path, Path, Path) = {
    val dir      = Files.createTempDirectory(s"working-paths-$name")
    val inputDir = Files.createDirectories(dir.resolve("jumps"))
    (dir, inputDir, inputFile)
  }

  "WorkingPaths extension methods" - {

    "place the chart next to the processed CSV" in {
      val expected = Paths.get("out", "jumps", "jump_1002.html")

      paths.chartHtml shouldBe expected
    }

    "name the FlySight conversion after the processed CSV" in {
      val expected = Paths.get("out", "jumps", "jump_1002.flysight.csv")

      paths.flysightCsv shouldBe expected
    }

    "name the metrics export after the processed CSV" in {
      val expected = Paths.get("out", "jumps", "jump_1002.metrics.csv")

      paths.metricsCsv shouldBe expected
    }

    "name the swoop report after the processed CSV" in {
      val expected = Paths.get("out", "jumps", "jump_1002.swoop.json")

      paths.swoopJson shouldBe expected
    }

    "place the metadata file next to the input file keeping its full name" in {
      val expected = Paths.get("data", "jumps", "jump_1002.csv.meta")

      paths.metadataFile shouldBe expected
    }

  }

  "Computing the output path" - {

    "places the output in the input directory when no output is configured" in {
      val (dir, inputDir, _) = inTempWorkspace("default")
      val input              = Files.writeString(inputDir.resolve("jump_1002.zip"), "data")

      val actual = applyFor(input, None)

      actual.getParent shouldBe inputDir
      actual.getFileName.toString shouldBe "jump_1002.csv"
      dir should not be null
    }

    "places the output under the configured output directory keeping the input structure" in {
      val (dir, inputDir, _) = inTempWorkspace("explicit")
      val outputDir          = Files.createDirectories(dir.resolve("out"))
      val input              = Files.writeString(inputDir.resolve("jump_1002.zip"), "data")

      val actual = applyFor(input, Some(outputDir))

      actual.getParent shouldBe outputDir.resolve("jumps")
      actual.getFileName.toString shouldBe "jump_1002.csv"
    }

    "suffixes the output name when it would overwrite the input file" in {
      val (dir, inputDir, _) = inTempWorkspace("collision")
      val input              = Files.writeString(inputDir.resolve("jump_1002.csv"), "data")

      val actual = applyFor(input, Some(inputDir))

      actual should not be input
      actual.getParent shouldBe inputDir
      actual.getFileName.toString shouldBe "jump_1002_output.csv"
    }

    "suffixes the output name when no output is configured and the input is a CSV" in {
      val (dir, inputDir, _) = inTempWorkspace("default-csv")
      val input              = Files.writeString(inputDir.resolve("jump_1002.csv"), "data")

      val actual = applyFor(input, None)

      actual should not be input
      actual.getParent shouldBe inputDir
      actual.getFileName.toString shouldBe "jump_1002_output.csv"
    }

  }

}
