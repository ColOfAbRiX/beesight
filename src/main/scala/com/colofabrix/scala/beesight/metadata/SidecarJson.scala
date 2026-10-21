package com.colofabrix.scala.beesight.metadata

import io.circe.syntax.*
import io.circe.{ Decoder, Encoder, Error, Json }
import java.time.{ Instant, OffsetDateTime }
import scala.util.Try

/**
 * Encode/decode entry points for the sidecar.
 */
object SidecarJson {

  given Encoder[Instant] =
    Encoder
      .encodeString
      .contramap(_.toString)

  given Decoder[Instant] =
    Decoder
      .decodeString
      .emap(text => Try(Instant.parse(text)).toEither.left.map(_.getMessage))

  given Encoder[OffsetDateTime] =
    Encoder
      .encodeString
      .contramap(_.toString)

  given Decoder[OffsetDateTime] =
    Decoder
      .decodeString
      .emap(text => Try(OffsetDateTime.parse(text)).toEither.left.map(_.getMessage))

  def encodeJson(sidecar: Sidecar): Json =
    sidecar
      .asJson
      .dropNullValues

  def encodeToString(sidecar: Sidecar): String =
    encodeJson(sidecar).noSpaces

  def decode(json: String): Either[Error, Sidecar] =
    io.circe.parser.decode[Sidecar](json)

}
