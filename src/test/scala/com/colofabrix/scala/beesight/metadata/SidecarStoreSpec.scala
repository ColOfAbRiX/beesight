package com.colofabrix.scala.beesight.metadata

import cats.effect.unsafe.implicits.global
import java.nio.file.Files
import java.time.OffsetDateTime
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class SidecarStoreSpec extends AnyWordSpec, Matchers {

  private val now              = OffsetDateTime.parse("2026-01-15T10:00:00Z")
  private val fingerprint      = InputFingerprint(10, now)
  private val otherFingerprint = InputFingerprint(99, now)
  private val section          = CutSection(ConfigHash.of("hash"), StoredCutSummary(true, 10, 3, 4, 6))

  private def tempFile(name: String): java.nio.file.Path =
    Files.createTempDirectory("sidecar-store").resolve(name)

  "SidecarStore.load" should {

    "return an empty sidecar when the file does not exist" in {
      val expected = Sidecar.forInput(SidecarStore.version, fingerprint)

      val actual = SidecarStore.load(tempFile("missing.meta"), fingerprint).unsafeRunSync()

      actual shouldBe expected
    }

    "round-trip a saved sidecar" in {
      val file  = tempFile("roundtrip.csv.meta")
      val saved = Sidecar.forInput(SidecarStore.version, fingerprint).withCut(section)
      SidecarStore.save(saved, file).unsafeRunSync()

      val actual = SidecarStore.load(file, fingerprint).unsafeRunSync()

      actual shouldBe saved
    }

    "reset a sidecar written for a different input fingerprint" in {
      val file  = tempFile("stale.csv.meta")
      val saved = Sidecar.forInput(SidecarStore.version, otherFingerprint).withCut(section)
      SidecarStore.save(saved, file).unsafeRunSync()

      val actual = SidecarStore.load(file, fingerprint).unsafeRunSync()

      actual shouldBe Sidecar.forInput(SidecarStore.version, fingerprint)
    }

    "reset a sidecar written by another version" in {
      val file  = tempFile("old-version.csv.meta")
      val saved = Sidecar.forInput("0.0.1", fingerprint).withCut(section)
      SidecarStore.save(saved, file).unsafeRunSync()

      val actual = SidecarStore.load(file, fingerprint).unsafeRunSync()

      actual shouldBe Sidecar.forInput(SidecarStore.version, fingerprint)
    }

    "reset a corrupt sidecar" in {
      val file = tempFile("corrupt.csv.meta")
      Files.writeString(file, "not json")

      val actual = SidecarStore.load(file, fingerprint).unsafeRunSync()

      actual shouldBe Sidecar.forInput(SidecarStore.version, fingerprint)
    }

  }

}
