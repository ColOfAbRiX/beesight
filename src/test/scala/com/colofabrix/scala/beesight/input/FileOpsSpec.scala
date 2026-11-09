package com.colofabrix.scala.beesight.input

import com.colofabrix.scala.beesight.IOConfigValues
import com.colofabrix.scala.beesight.config.{ Config, GlobalOptions }
import java.nio.file.{ Files, Path }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

final class FileOpsSpec extends AnyFreeSpec with Matchers with IOConfigValues {

  private def discover(input: Path, output: Option[Path]): List[String] = {
    val config = Config.default.copy(global = GlobalOptions.default.copy(input = input, output = output))
    FileOps.discoverCsvFiles().result(config).map(_.getFileName.toString)
  }

  "Discovering input files" - {

    "skips the tool's own outputs when writing in place" in {
      val dir = Files.createTempDirectory("file-ops-in-place")
      Files.writeString(dir.resolve("jump_1002.zip"), "data")
      Files.writeString(dir.resolve("jump_1002.csv"), "data")
      Files.writeString(dir.resolve("jump_1002_output.CSV"), "data")
      Files.writeString(dir.resolve("jump_1002.metrics.csv"), "data")
      Files.writeString(dir.resolve("real_jump.csv"), "data")

      val actual = discover(dir, None)

      actual shouldBe List("jump_1002.zip", "real_jump.csv")
    }

    "keeps every file when the output goes to another directory" in {
      val dir    = Files.createTempDirectory("file-ops-separate")
      val output = Files.createTempDirectory("file-ops-out")
      Files.writeString(dir.resolve("jump_1002.zip"), "data")
      Files.writeString(dir.resolve("jump_1002.csv"), "data")
      Files.writeString(dir.resolve("jump_1002_output.CSV"), "data")
      Files.writeString(dir.resolve("jump_1002.metrics.csv"), "data")
      Files.writeString(dir.resolve("real_jump.csv"), "data")

      val actual = discover(dir, Some(output))

      actual shouldBe List(
        "jump_1002.csv",
        "jump_1002.metrics.csv",
        "jump_1002.zip",
        "jump_1002_output.CSV",
        "real_jump.csv",
      )
    }

    "keeps an explicitly named input file" in {
      val dir  = Files.createTempDirectory("file-ops-explicit")
      val file = Files.writeString(dir.resolve("jump_1002_output.CSV"), "data")

      val actual = discover(file, None)

      actual shouldBe List("jump_1002_output.CSV")
    }

  }

}
