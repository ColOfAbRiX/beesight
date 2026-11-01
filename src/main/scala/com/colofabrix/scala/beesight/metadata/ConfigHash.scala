package com.colofabrix.scala.beesight.metadata

import cats.Eq
import com.colofabrix.scala.beesight.config.Config
import io.circe.{ Decoder, Encoder }
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * SHA-256 hash of the configuration that produced a metadata file, used to invalidate the cached analysis when the
 * configuration changes.
 */
opaque type ConfigHash = String

object ConfigHash {

  def sha256(content: String): ConfigHash =
    MessageDigest
      .getInstance("SHA-256")
      .digest(content.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"$byte%02x")
      .mkString

  def of(value: String): ConfigHash =
    value

  extension (config: Config)
    def configHash: ConfigHash =
      sha256(s"${config.detectionConfig.jumpevents}|${config.dataCutterConfig}")

  given Eq[ConfigHash] =
    Eq.fromUniversalEquals

  given Encoder[ConfigHash] =
    Encoder.encodeString.contramap(_.value)

  given Decoder[ConfigHash] =
    Decoder.decodeString.map(of)

}

extension (self: ConfigHash) {

  def value: String = self

}
