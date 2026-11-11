package com.colofabrix.scala.beesight.model

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

  "Detecting in-place output" - {

    "is in place when no output is configured" in {
      val (dir, inputDir, _) = inTempWorkspace("in-place-default")
      val config             = Config.default.copy(global = GlobalOptions.default.copy(input = inputDir, output = None))

      val actual = WorkingPaths.writesInPlace(config)

      actual shouldBe true
      dir should not be null
    }

    "is in place when the output is the input directory" in {
      val (dir, inputDir, _) = inTempWorkspace("in-place-same")
      val config             = Config.default.copy(global = GlobalOptions.default.copy(input = inputDir, output = Some(inputDir)))

      val actual = WorkingPaths.writesInPlace(config)

      actual shouldBe true
      dir should not be null
    }

    "is in place when the output is the parent of the input directory" in {
      val (dir, inputDir, _) = inTempWorkspace("in-place-parent")
      val config             = Config.default.copy(global = GlobalOptions.default.copy(input = inputDir, output = Some(dir)))

      val actual = WorkingPaths.writesInPlace(config)

      actual shouldBe true
      dir should not be null
    }

    "is not in place when the output goes to another directory" in {
      val (dir, inputDir, _) = inTempWorkspace("in-place-other")
      val outputDir          = Files.createDirectories(dir.resolve("out"))
      val config             = Config.default.copy(global = GlobalOptions.default.copy(input = inputDir, output = Some(outputDir)))

      val actual = WorkingPaths.writesInPlace(config)

      actual shouldBe false
      dir should not be null
    }

  }

  "Recognising the tool's own outputs" - {

    "flags the metrics and conversion exports" in {
      val dir       = Files.createTempDirectory("known-exports")
      val metrics   = Files.writeString(dir.resolve("jump_1002.metrics.csv"), "data")
      val converted = Files.writeString(dir.resolve("jump_1002.flysight.csv"), "data")

      val actual = List(WorkingPaths.isKnownOutput(metrics), WorkingPaths.isKnownOutput(converted))

      actual shouldBe List(true, true)
    }

    "flags the suffixed in-place conversions" in {
      val dir = Files.createTempDirectory("known-suffixed")
      val out = Files.writeString(dir.resolve("jump_1002_output.CSV"), "data")

      val actual = WorkingPaths.isKnownOutput(out)

      actual shouldBe true
    }

    "flags the CSV extracted from an archive" in {
      val dir = Files.createTempDirectory("known-extracted")
      Files.writeString(dir.resolve("jump_1002.zip"), "data")
      val extracted = Files.writeString(dir.resolve("jump_1002.csv"), "data")

      val actual = WorkingPaths.isKnownOutput(extracted)

      actual shouldBe true
    }

    "keeps plain inputs and archives" in {
      val dir = Files.createTempDirectory("known-inputs")
      val csv = Files.writeString(dir.resolve("jump_1002.csv"), "data")

      val csvIsOutput = WorkingPaths.isKnownOutput(csv)
      val zip          = Files.writeString(dir.resolve("jump_1002.zip"), "data")
      val zipIsOutput  = WorkingPaths.isKnownOutput(zip)

      (csvIsOutput, zipIsOutput) shouldBe (false, false)
    }

  }

}
