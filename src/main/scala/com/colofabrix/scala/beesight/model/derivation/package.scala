package com.colofabrix.scala.beesight.model

import cats.implicits.given
import fs2.data.csv.*
import java.time.*
import java.time.format.DateTimeFormatter

/**
 * CSV cell decoders for Java time types used in flight data formats.
 */
package object derivation {

  private val offsetDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME

  private[beesight] given CellDecoder[OffsetDateTime] =
    CellDecoder[String].emap { value =>
      Either
        .catchNonFatal(OffsetDateTime.parse(value, offsetDateTimeFormatter))
        .leftMap(t => new DecoderError(t.getMessage()))
    }

  private[beesight] given CellDecoder[Instant] =
    CellDecoder[String].emap { value =>
      Either
        .catchNonFatal(Instant.parse(value))
        .leftMap(t => new DecoderError(t.getMessage()))
    }

}
