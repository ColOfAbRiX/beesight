package com.colofabrix.scala.beesight.metadata

import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.output.WorkingPaths
import java.nio.file.Files
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class MetadataFileStoreSpec extends AnyWordSpec, Matchers {

  private val section = StoredCutSummary(true, 10, 3, 4, 6)

  private def pathsFor(name: String): WorkingPaths = {
    val dir   = Files.createTempDirectory("metadata-file-store")
    val input = Files.writeString(dir.resolve(s"$name.csv"), "data")
    WorkingPaths(input).run(Config.default).unsafeRunSync()
  }

  private def fileWithCut(version: String): MetadataFile =
    MetadataFile(version, ConfigHash.of("hash"), None, Some(section))

  "MetadataFileStore.load" should {

    "return an empty metadata file when the file does not exist" in {
      val expected = MetadataFile.forInput(MetadataFileStore.version)

      val actual = MetadataFileStore.load(pathsFor("missing")).unsafeRunSync()

      actual shouldBe expected
    }

    "round-trip a saved metadata file" in {
      val paths = pathsFor("roundtrip")
      val saved = fileWithCut(MetadataFileStore.version)
      MetadataFileStore.save(saved, paths.metadataFile).unsafeRunSync()

      val actual = MetadataFileStore.load(paths).unsafeRunSync()

      actual shouldBe saved
    }

    "reset a metadata file written by another version" in {
      val paths = pathsFor("old-version")
      val saved = fileWithCut("0.0.1")
      MetadataFileStore.save(saved, paths.metadataFile).unsafeRunSync()

      val actual = MetadataFileStore.load(paths).unsafeRunSync()

      actual shouldBe MetadataFile.forInput(MetadataFileStore.version)
    }

    "reset a corrupt metadata file" in {
      val paths = pathsFor("corrupt")
      Files.writeString(paths.metadataFile, "not json")

      val actual = MetadataFileStore.load(paths).unsafeRunSync()

      actual shouldBe MetadataFile.forInput(MetadataFileStore.version)
    }

  }

}
