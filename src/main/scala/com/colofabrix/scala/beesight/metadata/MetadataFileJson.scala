package com.colofabrix.scala.beesight.metadata

import io.circe.syntax.*
import io.circe.{ Decoder, Encoder, Error, Json }
import java.time.{ Instant, OffsetDateTime }
import scala.util.Try

/**
 * Encode/decode entry points for the metadata file.
 */
object MetadataFileJson {

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

  def encodeJson(metadataFile: MetadataFile): Json =
    metadataFile
      .asJson
      .dropNullValues

  def encodeToString(metadataFile: MetadataFile): String =
    encodeJson(metadataFile).noSpaces

  def decode(json: String): Either[Error, MetadataFile] =
    io.circe.parser.decode[MetadataFile](json)

}
