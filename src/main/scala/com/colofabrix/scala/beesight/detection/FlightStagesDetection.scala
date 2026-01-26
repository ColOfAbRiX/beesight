package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*
import cats.data.Reader

object FlightStagesDetection {

  def streamDetectA[F[_], A](using A: FileFormatAdapter[A]): fs2.Pipe[F, A, OutputFlightRow[A]] =
    streamDetectWithConfig(DetectionConfig.default)

  def streamDetectWithConfig[F[_], A: FileFormatAdapter](config: DetectionConfig): fs2.Pipe[F, A, OutputFlightRow[A]] =
    _.map(FileFormatAdapter[A].toInputFlightPoint)
      .zipWithIndex
      .mapAccumulate(Option.empty[ProcessingState[A]]) {
        case (maybeState, (point, idx)) =>
          val result =
            maybeState match {
              case None =>
                val initialState = createInitialState(point, config)
                ProcessingResult(initialState, Vector.empty)
              case Some(state) =>
                processPoint(state, point, idx, config)
            }
          (Some(result.nextState), result.outputs)
      }
      .flatMap {
        case (_, outputs) => fs2.Stream.emits(outputs)
      }

  private case class StreamData[A](
    config: DetectionConfig,
    state: ProcessingState[A],
    kinematics: PointKinematics,
  )

  // private type StreamReader[A, B] = Reader[StreamData[A], B]
  // private type StreamResult[A]  = (nextState: ProcessingState[A], outputs: Vector[OutputFlightRow[A]])
  // private type StreamUpdater[A] = Reader[StreamData[A], StreamResult[A]]

  private def createInitialState[A](point: InputFlightRow[A], config: DetectionConfig): ProcessingState[A] = {
    val kinematics = Preprocessing.computeKinematics(point, None, None, config.global)
    ProcessingState(
      index = 0,
      currentPoint = point,
      previousKinematics = Some(kinematics),
      streamPhase = StreamPhase.Streaming,
      detectedEvents = DetectedEvents.empty,
      pendingBuffer = Vector.empty,
      takeoffState = EventState.withSizes(
        config.takeoff.smoothingWindowSize,
        config.takeoff.backtrackWindowSize,
        config.landing.stabilityWindowSize,
      ),
      freefallState = EventState.withSizes(
        config.freefall.smoothingWindowSize,
        config.freefall.backtrackWindowSize,
        config.landing.stabilityWindowSize,
      ),
      canopyState = EventState.withSizes(
        config.canopy.smoothingWindowSize,
        config.canopy.backtrackWindowSize,
        config.landing.stabilityWindowSize,
      ),
      landingState = EventState.withSizes(
        config.landing.smoothingWindowSize,
        config.landing.backtrackWindowSize,
        config.landing.stabilityWindowSize,
      ),
    )
  }

  private def processPoint[A](
    state: ProcessingState[A],
    point: InputFlightRow[A],
    index: Long,
    config: DetectionConfig,
  ): ProcessingResult[A] = {
    val kinematics =
      Preprocessing.computeKinematics(point, Some(state.currentPoint), state.previousKinematics, config.global)

    val updatedState = updateAllEventStates(state, point, index, kinematics)

    updatedState.streamPhase match {
      case StreamPhase.Streaming =>
        handleStreaming(updatedState, kinematics, config)
      case StreamPhase.Validation(1, eventType) =>
        finalizeValidation(updatedState, eventType, kinematics, config)
      case StreamPhase.Validation(remaining, eventType) =>
        continueValidation(updatedState, remaining - 1, eventType)
    }
  }

  private def handleStreaming[A](
    state: ProcessingState[A],
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): ProcessingResult[A] =
    tryDetectEvent(state, kinematics, config) match {
      case Some(eventType) =>
        val validationWindow = getValidationWindowSize(config, eventType)
        val bufferedState    = addToBuffer(state)
        ProcessingResult(
          state.copy(
            streamPhase = StreamPhase.Validation(validationWindow, eventType),
            pendingBuffer = bufferedState,
          ),
          Vector.empty,
        )

      case None =>
        val maxBuffer = config.freefall.backtrackWindowSize
        val newBuffer = addToBuffer(state)
        if (newBuffer.size > maxBuffer) {
          val output = toOutputRow(newBuffer.head)
          ProcessingResult(
            state.copy(pendingBuffer = newBuffer.tail),
            Vector(output),
          )
        } else {
          ProcessingResult(
            state.copy(pendingBuffer = newBuffer),
            Vector.empty,
          )
        }
    }

  private def continueValidation[A](
    state: ProcessingState[A],
    remaining: Int,
    eventType: EventType,
  ): ProcessingResult[A] = {
    val bufferedState = addToBuffer(state)
    ProcessingResult(
      state.copy(
        streamPhase = StreamPhase.Validation(remaining, eventType),
        pendingBuffer = bufferedState,
      ),
      Vector.empty,
    )
  }

  private def finalizeValidation[A](
    state: ProcessingState[A],
    eventType: EventType,
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): ProcessingResult[A] = {
    val eventState = getEventState(state, eventType)
    val isValid    = checkValidationCondition(eventType, eventState, kinematics, config)
    val fullBuffer = addToBuffer(state)

    if (isValid) {
      // SUCCESS: Use TRIGGER state's backtrackWindow to find inflection point
      val triggerState      = fullBuffer.head
      val triggerEventState = getEventState(triggerState, eventType)
      val isRising          = eventType == EventType.Takeoff || eventType == EventType.Freefall
      val inflectionPoint   = InflectionFinder.findInflectionPoint(triggerEventState.backtrackWindow, isRising)

      // Find state to resume from (first state AFTER inflection point)
      val resumeState = inflectionPoint match {
        case Some(fp) => fullBuffer.find(_.index > fp.index).getOrElse(fullBuffer.last)
        case None     => fullBuffer.last
      }

      val newEvents = updateDetectedEvents(resumeState.detectedEvents, eventType, inflectionPoint)
      val outputs   = releaseBuffer(fullBuffer, eventType, inflectionPoint)

      // Resume from inflection+1 state with updated events
      ProcessingResult(
        resumeState.copy(
          streamPhase = StreamPhase.Streaming,
          detectedEvents = newEvents,
          pendingBuffer = Vector.empty,
        ),
        outputs,
      )
    } else {
      // FAILURE: Resume from T+1 (second state in buffer)
      val resumeState = fullBuffer.lift(1).getOrElse(fullBuffer.last)
      val outputs     = fullBuffer.map(toOutputRow)

      ProcessingResult(
        resumeState.copy(
          streamPhase = StreamPhase.Streaming,
          pendingBuffer = Vector.empty,
        ),
        outputs,
      )
    }
  }

  private def tryDetectEvent[A](
    state: ProcessingState[A],
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): Option[EventType] = {
    val candidates =
      Vector(
        (EventType.Takeoff, state.detectedEvents.takeoff.isEmpty),
        (EventType.Freefall, state.detectedEvents.takeoff.isDefined && state.detectedEvents.freefall.isEmpty),
        (EventType.Canopy, state.detectedEvents.freefall.isDefined && state.detectedEvents.canopy.isEmpty),
        (
          EventType.Landing,
          (state.detectedEvents.canopy.isDefined || state.detectedEvents.takeoff.isDefined) && state.detectedEvents.landing.isEmpty,
        ),
      )

    candidates.collectFirst {
      case (eventType, true) if checkEventTrigger(state, eventType, kinematics, config) => eventType
    }
  }

  private def checkEventTrigger[A](
    state: ProcessingState[A],
    eventType: EventType,
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): Boolean = {
    val eventState = getEventState(state, eventType)
    val triggered  = eventType match {
      case EventType.Takeoff =>
        TakeoffDetection.checkTrigger(eventState, kinematics, config.takeoff)
      case EventType.Freefall =>
        val previousSmoothed = state.previousKinematics.map(_.clippedVerticalSpeed).getOrElse(0.0)
        FreefallDetection.checkTrigger(eventState, kinematics, previousSmoothed, config.freefall)
      case EventType.Canopy =>
        CanopyDetection.checkTrigger(eventState, config.canopy)
      case EventType.Landing =>
        LandingDetection.checkTrigger(eventState, kinematics, config.landing)
    }

    val constrained = eventType match {
      case EventType.Takeoff =>
        TakeoffDetection.checkConstraints(state.detectedEvents, kinematics, config.takeoff)
      case EventType.Freefall =>
        FreefallDetection.checkConstraints(state.detectedEvents, kinematics, state.index, config.freefall)
      case EventType.Canopy =>
        CanopyDetection.checkConstraints(state.detectedEvents, kinematics, state.index)
      case EventType.Landing =>
        LandingDetection.checkConstraints(state.detectedEvents, kinematics, state.index)
    }

    triggered && constrained
  }

  private def checkValidationCondition(
    eventType: EventType,
    eventState: EventState,
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): Boolean =
    eventType match {
      case EventType.Takeoff  => TakeoffDetection.checkValidation(eventState, config.takeoff)
      case EventType.Freefall => FreefallDetection.checkValidation(eventState, config.freefall)
      case EventType.Canopy   => CanopyDetection.checkValidation(eventState, config.canopy)
      case EventType.Landing  => LandingDetection.checkValidation(eventState, kinematics, config.landing)
    }

  private def updateAllEventStates[A](
    state: ProcessingState[A],
    point: InputFlightRow[A],
    index: Long,
    kinematics: PointKinematics,
  ): ProcessingState[A] = {
    val sample = VerticalSpeedSample(index, kinematics.clippedVerticalSpeed, kinematics.correctedAltitude)

    state.copy(
      index = index,
      currentPoint = point,
      previousKinematics = Some(kinematics),
      takeoffState = updateEventState(state.takeoffState, kinematics, sample),
      freefallState = updateEventState(state.freefallState, kinematics, sample),
      canopyState = updateEventState(state.canopyState, kinematics, sample),
      landingState = updateEventState(state.landingState, kinematics, sample),
    )
  }

  private def updateEventState(
    state: EventState,
    kinematics: PointKinematics,
    sample: VerticalSpeedSample,
  ): EventState =
    EventState(
      smoothingWindow = state.smoothingWindow.enqueue(kinematics.clippedVerticalSpeed),
      backtrackWindow = state.backtrackWindow.enqueue(sample),
      stabilityWindow = state.stabilityWindow.enqueue(kinematics.clippedVerticalSpeed),
    )

  private def getEventState[A](state: ProcessingState[A], eventType: EventType): EventState =
    eventType match {
      case EventType.Takeoff  => state.takeoffState
      case EventType.Freefall => state.freefallState
      case EventType.Canopy   => state.canopyState
      case EventType.Landing  => state.landingState
    }

  private def getValidationWindowSize(config: DetectionConfig, eventType: EventType): Int =
    eventType match {
      case EventType.Takeoff  => config.takeoff.validationWindowSize
      case EventType.Freefall => config.freefall.validationWindowSize
      case EventType.Canopy   => config.canopy.validationWindowSize
      case EventType.Landing  => config.landing.validationWindowSize
    }

  private def computeCurrentPhase(events: DetectedEvents): FlightPhase =
    if (events.landing.isDefined) FlightPhase.Landed
    else if (events.canopy.isDefined) FlightPhase.UnderCanopy
    else if (events.freefall.isDefined) FlightPhase.Freefall
    else if (events.takeoff.isDefined) FlightPhase.Climbing
    else FlightPhase.BeforeTakeoff

  private def addToBuffer[A](state: ProcessingState[A]): Vector[ProcessingState[A]] =
    state.pendingBuffer :+ state

  private def toOutputRow[A](state: ProcessingState[A]): OutputFlightRow[A] =
    OutputFlightRow(
      phase = computeCurrentPhase(state.detectedEvents),
      takeoff = state.detectedEvents.takeoff,
      freefall = state.detectedEvents.freefall,
      canopy = state.detectedEvents.canopy,
      landing = state.detectedEvents.landing,
      source = state.currentPoint.source,
    )

  private def releaseBuffer[A](
    buffer: Vector[ProcessingState[A]],
    eventType: EventType,
    inflectionPoint: Option[FlightPoint],
  ): Vector[OutputFlightRow[A]] =
    inflectionPoint match {
      case None     => buffer.map(toOutputRow)
      case Some(fp) =>
        buffer.map { state =>
          val updatedEvents =
            if (state.index >= fp.index)
              updateDetectedEvents(state.detectedEvents, eventType, Some(fp))
            else
              state.detectedEvents
          OutputFlightRow(
            phase = computeCurrentPhase(updatedEvents),
            takeoff = updatedEvents.takeoff,
            freefall = updatedEvents.freefall,
            canopy = updatedEvents.canopy,
            landing = updatedEvents.landing,
            source = state.currentPoint.source,
          )
        }
    }

  private def updateDetectedEvents(
    events: DetectedEvents,
    eventType: EventType,
    point: Option[FlightPoint],
  ): DetectedEvents =
    eventType match {
      case EventType.Takeoff  => events.copy(takeoff = point.orElse(events.takeoff))
      case EventType.Freefall => events.copy(freefall = point.orElse(events.freefall))
      case EventType.Canopy   => events.copy(canopy = point.orElse(events.canopy))
      case EventType.Landing  => events.copy(landing = point.orElse(events.landing))
    }

}
