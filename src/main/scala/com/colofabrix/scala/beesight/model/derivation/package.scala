package com.colofabrix.scala.beesight.model

import cats.implicits.given
import fs2.data.csv.*
import java.time.*

package object derivation {

  given CellDecoder[OffsetDateTime] =
    CellDecoder[String].emap { value =>
      Either
        .catchNonFatal(OffsetDateTime.parse(value))
        .leftMap(t => new DecoderError(t.getMessage()))
    }

  given CellDecoder[Instant] =
    CellDecoder[String].emap { value =>
      Either
        .catchNonFatal(Instant.parse(value))
        .leftMap(t => new DecoderError(t.getMessage()))
    }

}
