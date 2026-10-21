package com.colofabrix.scala.beesight.metadata

import cats.Eq
import io.circe.{ Decoder, Encoder }
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * SHA-256 hash of the configuration that produced a sidecar section, used to invalidate the section when the
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
