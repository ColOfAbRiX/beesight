package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.model.FlightPhase
import com.colofabrix.scala.beesight.model.FlightEvents
import com.colofabrix.scala.beesight.model.InputFlightRow
import com.colofabrix.scala.beesight.model.OutputFlightRow

/**
 * Complete state of the streaming detection at each point.
 * This is the single source of truth for all detection context.
 */
final private[detection] case class StreamState[A](
  inputPoint: InputFlightRow[A],
  dataPointIndex: Long,
  kinematics: Kinematics,
  windows: Windows,
  detectedPhase: FlightPhase,
  detectedStages: FlightEvents,
  takeoffMissing: Boolean,
)

private[detection] object StreamState {

  def create[A](point: InputFlightRow[A], config: DetectionConfig): StreamState[A] =
    StreamState(
      inputPoint = point,
      dataPointIndex = 0,
      kinematics = Kinematics.create(point),
      windows = Windows.create(config),
      detectedPhase = FlightPhase.BeforeTakeoff,
      detectedStages = FlightEvents.empty,
      takeoffMissing = false,
    )

  def toOutputFlightRow[A](state: StreamState[A]): OutputFlightRow[A] =
    OutputFlightRow(
      phase = state.detectedPhase,
      takeoff = state.detectedStages.takeoff,
      freefall = state.detectedStages.freefall,
      canopy = state.detectedStages.canopy,
      landing = state.detectedStages.landing,
      lastPoint = state.detectedStages.lastPoint,
      source = state.inputPoint.source,
    )

}
