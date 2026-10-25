package com.colofabrix.scala.beesight.metadata

import cats.effect.unsafe.implicits.global
import com.colofabrix.scala.beesight.config.Config
import com.colofabrix.scala.beesight.output.WorkingPaths
import java.nio.file.Files
import java.time.OffsetDateTime
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class SidecarStoreSpec extends AnyWordSpec, Matchers {

  private val now              = OffsetDateTime.parse("2026-01-15T10:00:00Z")
  private val otherFingerprint = InputFingerprint(99, now)
  private val section          = CutSection(ConfigHash.of("hash"), StoredCutSummary(true, 10, 3, 4, 6))

  private def pathsFor(name: String): WorkingPaths = {
    val dir   = Files.createTempDirectory("sidecar-store")
    val input = Files.writeString(dir.resolve(s"$name.csv"), "data")
    WorkingPaths(input).run(Config.default).unsafeRunSync()
  }

  private def sidecarWithCut(version: String, fingerprint: InputFingerprint): Sidecar =
    Sidecar
      .forInput(version, fingerprint)
      .copy(sections = SidecarSections.empty.copy(cut = Some(section)))

  "SidecarStore.load" should {

    "return an empty sidecar when the file does not exist" in {
      val paths    = pathsFor("missing")
      val expected = Sidecar.forInput(SidecarStore.version, InputFingerprint.of(paths.inputFile).unsafeRunSync())

      val actual = SidecarStore.load(paths).unsafeRunSync()

      actual shouldBe expected
    }

    "round-trip a saved sidecar" in {
      val paths    = pathsFor("roundtrip")
      val file     = paths.sidecar
      val saved    = sidecarWithCut(SidecarStore.version, InputFingerprint.of(paths.inputFile).unsafeRunSync())
      SidecarStore.save(saved, file).unsafeRunSync()

      val actual = SidecarStore.load(paths).unsafeRunSync()

      actual shouldBe saved
    }

    "reset a sidecar written for a different input fingerprint" in {
      val paths = pathsFor("stale")
      val saved = sidecarWithCut(SidecarStore.version, otherFingerprint)
      SidecarStore.save(saved, paths.sidecar).unsafeRunSync()

      val actual = SidecarStore.load(paths).unsafeRunSync()

      actual shouldBe Sidecar.forInput(SidecarStore.version, InputFingerprint.of(paths.inputFile).unsafeRunSync())
    }

    "reset a sidecar written by another version" in {
      val paths = pathsFor("old-version")
      val saved = sidecarWithCut("0.0.1", InputFingerprint.of(paths.inputFile).unsafeRunSync())
      SidecarStore.save(saved, paths.sidecar).unsafeRunSync()

      val actual = SidecarStore.load(paths).unsafeRunSync()

      actual shouldBe Sidecar.forInput(SidecarStore.version, InputFingerprint.of(paths.inputFile).unsafeRunSync())
    }

    "reset a corrupt sidecar" in {
      val paths = pathsFor("corrupt")
      Files.writeString(paths.sidecar, "not json")

      val actual = SidecarStore.load(paths).unsafeRunSync()

      actual shouldBe Sidecar.forInput(SidecarStore.version, InputFingerprint.of(paths.inputFile).unsafeRunSync())
    }

  }

}
