package com.colofabrix.scala.beesight.config

import com.colofabrix.scala.beesight.model.EventType

final case class CutterConfig(
  keepBeforeTakeoff: Int,
  keepAfterLanding: Int,
  minKeepPoints: Int,
)

object CutterConfig {

  val default: CutterConfig =
    CutterConfig(
      keepBeforeTakeoff = 500,
      keepAfterLanding = 500,
      minKeepPoints = 0,
    )

}
