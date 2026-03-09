package com.colofabrix.scala.beesight.config

final case class CutterConfig(
  keepBeforeTakeoff: Int,
  keepAfterLanding: Int,
  minPercentKeepPoints: Double,
)

object CutterConfig {

  val default: CutterConfig =
    CutterConfig(
      keepBeforeTakeoff = 500,
      keepAfterLanding = 500,
      minPercentKeepPoints = 0.1,
    )

}
