package com.colofabrix.scala.beesight.metadata

import io.circe.syntax.*
import io.circe.{ Decoder, Encoder, Error, Json }
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import scala.util.Try

given Encoder[Instant] =
  Encoder.encodeString.contramap(_.toString)

given Decoder[Instant] =
  Decoder.decodeString.emap(text => Try(Instant.parse(text)).toEither.left.map(_.getMessage))

/**
 * SHA-256 hashes used to invalidate sidecar sections when the configuration changes.
 */
object ConfigHash {

  def sha256(content: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(content.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"$byte%02x")
      .mkString

}

/**
 * Encode/decode entry points for the sidecar.
 */
object SidecarJson {

  /** JSON value of the sidecar, without nulls for absent optional fields. */
  def encodeJson(sidecar: Sidecar): Json =
    sidecar.asJson.dropNullValues

  def encodeToString(sidecar: Sidecar): String =
    encodeJson(sidecar).noSpaces

  /** Decoding tolerates unknown fields, so sidecars written by newer versions still load. */
  def decode(json: String): Either[Error, Sidecar] =
    io.circe.parser.decode[Sidecar](json)

}
