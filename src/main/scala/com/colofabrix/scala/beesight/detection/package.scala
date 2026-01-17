package com.colofabrix.scala.beesight

import cats.data.Reader
import com.colofabrix.scala.beesight.model.*
import com.colofabrix.scala.beesight.config.DetectionConfig

package object detection {

  private[detection] type TryDetect[B] =
    Reader[(DetectionConfig, FlightStagesPoints, StreamState[?], FlightStagePoint), B]

  extension (self: Option[FlightStagePoint]) {
    private[detection] infix def isAfter(currentIndex: Long): Boolean =
      self.map(_.lineIndex).forall(currentIndex > _)
  }

}
