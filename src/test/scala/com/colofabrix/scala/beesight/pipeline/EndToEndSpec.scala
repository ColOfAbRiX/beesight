package com.colofabrix.scala.beesight.pipeline

import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.FileProcessor
import com.colofabrix.scala.beesight.config.{ Command, Config, GlobalOptions }
import com.colofabrix.scala.beesight.detection.config.Speed
import com.colofabrix.scala.beesight.metadata.{ MetadataFile, MetadataFileJson }
import io.circe.parser
import java.nio.file.{ Files, Path, Paths }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.jdk.CollectionConverters.*

class EndToEndSpec extends AnyFreeSpec with Matchers {

  private val sampleZip = Paths.get("beesight-formats", "src", "test", "resources", "flysight_sample.zip")

  private def withWorkspace(name: String)(f: (Path, Path, Path) => Unit): Unit = {
    val dir       = Files.createTempDirectory(s"beesight-e2e-$name")
    val inputDir  = Files.createDirectories(dir.resolve("in"))
    val outputDir = dir.resolve("out")
    val inputFile = Files.copy(sampleZip, inputDir.resolve(sampleZip.getFileName))
    try f(inputFile, inputDir, outputDir)
    finally {
      Files.walk(dir).iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
      ()
    }
  }

  private def configFor(inputDir: Path, outputDir: Path, command: Command, config: Config = Config.default): Config =
    config.copy(
      global = config.global.copy(input = inputDir, output = Some(outputDir)),
      command = command,
    )

  private def run(inputFile: Path, inputDir: Path, outputDir: Path, command: Command, config: Config = Config.default): Unit =
    FileProcessor.processFile(inputFile).run(configFor(inputDir, outputDir, command, config)).unsafeRunSync()

  private def metadataFileOf(inputFile: Path): Path =
    inputFile.resolveSibling(s"${inputFile.getFileName}.meta")

  private def readMetadataFile(inputFile: Path): Option[MetadataFile] =
    for {
      content <- Option(Files.readString(metadataFileOf(inputFile))).filter(_.nonEmpty)
      metadataFile <- MetadataFileJson.decode(content).toOption
    } yield metadataFile

  "running analyse" - {

    "produces every output file and the metadata file" in withWorkspace("analyse") { (inputFile, inputDir, outputDir) =>
      val expectedMetadataFile = metadataFileOf(inputFile)

      run(inputFile, inputDir, outputDir, Command.Analyse)

      Files.isRegularFile(outputDir.resolve("in").resolve("flysight_sample.csv")) shouldBe true
      Files.isRegularFile(outputDir.resolve("in").resolve("flysight_sample.metrics.csv")) shouldBe true
      Files.isRegularFile(outputDir.resolve("in").resolve("flysight_sample.swoop.json")) shouldBe true
      Files.isRegularFile(outputDir.resolve("in").resolve("flysight_sample.html")) shouldBe true
      Files.isRegularFile(expectedMetadataFile) shouldBe true

      val html = Files.readString(outputDir.resolve("in").resolve("flysight_sample.html"))
      html should include("Elapsed time (s)")

      val metrics = Files.readString(outputDir.resolve("in").resolve("flysight_sample.metrics.csv"))
      metrics.linesIterator.next() should startWith("time,altitude,vertical_speed,")

      val swoop = parser.decode[io.circe.Json](Files.readString(outputDir.resolve("in").resolve("flysight_sample.swoop.json")))
      swoop.isRight shouldBe true

      val metadataFile = readMetadataFile(inputFile)
      metadataFile.map(_.jumpevents.isDefined) shouldBe Some(true)
      metadataFile.map(_.cut.isDefined) shouldBe Some(true)
    }

    "writes nothing on a dry run" in withWorkspace("dryrun") { (inputFile, inputDir, outputDir) =>
      val dryRun = Config.default.copy(global = Config.default.global.copy(dryRun = true))

      run(inputFile, inputDir, outputDir, Command.Analyse, dryRun)

      val written = Option.when(Files.isDirectory(outputDir))(Files.walk(outputDir).iterator().asScala.toList).getOrElse(Nil)
      written.filter(p => Files.isRegularFile(p)) shouldBe empty
      Files.exists(metadataFileOf(inputFile)) shouldBe false
    }

  }

  "running commands in a chain" - {

    "reuses the cached analysis when nothing changed" in withWorkspace("reuse") { (inputFile, inputDir, outputDir) =>
      run(inputFile, inputDir, outputDir, Command.Analyse)
      val metadataFileBefore = Files.readString(metadataFileOf(inputFile))

      run(inputFile, inputDir, outputDir, Command.Swoop)

      val metadataFileAfter = Files.readString(metadataFileOf(inputFile))

      metadataFileAfter shouldBe metadataFileBefore
    }

    "invalidates the detection section when the detection configuration changes" in withWorkspace("stale-config") {
      (inputFile, inputDir, outputDir) =>
        run(inputFile, inputDir, outputDir, Command.Analyse)
        val metadataFileBefore = readMetadataFile(inputFile)
        val oldHash       = metadataFileBefore.map(_.configHash)

        val base      = Config.default
        val tunedBase = base.detectionConfig.jumpevents
        val tunedConfig = base.copy(
          detectionConfig = base.detectionConfig.copy(
            jumpevents = tunedBase.copy(canopy = tunedBase.canopy.copy(verticalSpeedThreshold = Speed(12.0))),
          ),
        )
        run(inputFile, inputDir, outputDir, Command.Metrics, tunedConfig)

        val metadataFileAfter = readMetadataFile(inputFile)
        val newHash      = metadataFileAfter.map(_.configHash)

        newHash should not be oldHash
        metadataFileAfter.flatMap(_.jumpevents) shouldBe defined
    }

  }

}
