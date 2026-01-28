package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*
import cats.data.Reader

object FlightStagesDetection {

  // ─── Debug Configuration ───────────────────────────────────────────────────

  private val DEBUG_ENABLED = true

  private def debug(msg: => String): Unit =
    if (DEBUG_ENABLED) println(s"[DEBUG] $msg")

  private def debugSection(title: String)(body: => Unit): Unit =
    if (DEBUG_ENABLED) {
      println(s"\n${"=" * 60}")
      println(s"  $title")
      println(s"${"=" * 60}")
      body
    }

  // ─── Public API ────────────────────────────────────────────────────────────

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
                debug(s"[INIT] Creating initial state at index 0")
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

  // ─── Initialization ────────────────────────────────────────────────────────

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

  // ─── Main Processing ───────────────────────────────────────────────────────

  private def processPoint[A](
    state: ProcessingState[A],
    point: InputFlightRow[A],
    index: Long,
    config: DetectionConfig,
  ): ProcessingResult[A] = {
    val kinematics =
      Preprocessing.computeKinematics(point, Some(state.currentPoint), state.previousKinematics, config.global)

    val updatedState = updateAllEventStates(state, point, index, kinematics)

    debugSection(s"PROCESSING POINT $index") {
      debug(s"Altitude: ${kinematics.correctedAltitude}m, VSpeed: ${f"${kinematics.clippedVerticalSpeed}%.2f"}m/s")
      debug(s"Current Phase: ${updatedState.streamPhase}")
      debug(s"Detected Events: T=${updatedState.detectedEvents.takeoff.isDefined}, " +
        s"F=${updatedState.detectedEvents.freefall.isDefined}, " +
        s"C=${updatedState.detectedEvents.canopy.isDefined}, " +
        s"L=${updatedState.detectedEvents.landing.isDefined}")
      debug(s"Buffer Size: ${updatedState.pendingBuffer.size}")
    }

    updatedState.streamPhase match {
      case StreamPhase.Streaming =>
        debug(s"[PHASE] STREAMING - Looking for events...")
        handleStreaming(updatedState, kinematics, config)
      case StreamPhase.Validation(1, eventType) =>
        debug(s"[PHASE] VALIDATION FINAL - Finalizing $eventType validation")
        finalizeValidation(updatedState, eventType, kinematics, config)
      case StreamPhase.Validation(remaining, eventType) =>
        debug(s"[PHASE] VALIDATION - Continuing $eventType, $remaining points remaining")
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
        debug(s"[TRIGGER] ✓ Event $eventType TRIGGERED! Starting validation for $validationWindow points")
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
          debug(s"[BUFFER] Buffer full ($maxBuffer), releasing oldest point")
          ProcessingResult(
            state.copy(pendingBuffer = newBuffer.tail),
            Vector(output),
          )
        } else {
          debug(s"[BUFFER] Buffering point (${newBuffer.size}/$maxBuffer)")
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
    debug(s"[VALIDATION] Continuing $eventType validation, $remaining more points to check")
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

    debug(s"[VALIDATION FINALIZE] Event: $eventType, Valid: $isValid, Buffer size: ${fullBuffer.size}")

    if (isValid) {
      debug(s"[VALIDATION] ✓✓ $eventType CONFIRMED!")

      val triggerState      = fullBuffer.head
      val triggerEventState = getEventState(triggerState, eventType)
      val isRising          = eventType == EventType.Takeoff || eventType == EventType.Freefall
      val inflectionPoint   = InflectionFinder.findInflectionPoint(triggerEventState.backtrackWindow, isRising)

      debug(s"[INFLECTION] Looking for inflection point (isRising=$isRising)")
      inflectionPoint match {
        case Some(fp) =>
          debug(s"[INFLECTION] ✓ Found at index ${fp.index}, altitude ${fp.altitude}m")
        case None =>
          debug(s"[INFLECTION] ✗ No inflection point found")
      }

      val resumeState = inflectionPoint match {
        case Some(fp) => fullBuffer.find(_.index > fp.index).getOrElse(fullBuffer.last)
        case None     => fullBuffer.last
      }
      debug(s"[RESUME] Resuming from index ${resumeState.index}")

      val newEvents = updateDetectedEvents(resumeState.detectedEvents, eventType, inflectionPoint)
      val outputs   = releaseBuffer(fullBuffer, eventType, inflectionPoint)
      debug(s"[OUTPUT] Releasing ${outputs.size} points from buffer")

      ProcessingResult(
        resumeState.copy(
          streamPhase = StreamPhase.Streaming,
          detectedEvents = newEvents,
          pendingBuffer = Vector.empty,
        ),
        outputs,
      )
    } else {
      debug(s"[VALIDATION] ✗✗ $eventType REJECTED! Returning to streaming")

      val resumeState = fullBuffer.lift(1).getOrElse(fullBuffer.last)
      val outputs     = fullBuffer.map(toOutputRow)
      debug(s"[RESUME] Resuming from index ${resumeState.index} (T+1)")
      debug(s"[OUTPUT] Releasing ${outputs.size} points from buffer")

      ProcessingResult(
        resumeState.copy(
          streamPhase = StreamPhase.Streaming,
          pendingBuffer = Vector.empty,
        ),
        outputs,
      )
    }
  }

  // ─── Event Detection ───────────────────────────────────────────────────────

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

    val eligibleCandidates = candidates.filter(_._2).map(_._1)
    debug(s"[DETECT] Eligible events to check: ${eligibleCandidates.mkString(", ")}")

    val result = candidates.collectFirst {
      case (eventType, true) if checkEventTrigger(state, eventType, kinematics, config) => eventType
    }

    result match {
      case Some(et) => debug(s"[DETECT] ✓ $et trigger conditions MET")
      case None     => debug(s"[DETECT] No event triggered")
    }

    result
  }

  private def checkEventTrigger[A](
    state: ProcessingState[A],
    eventType: EventType,
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): Boolean = {
    val eventState = getEventState(state, eventType)

    val triggered = eventType match {
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

    debug(s"[TRIGGER CHECK] $eventType: triggered=$triggered, constrained=$constrained")

    triggered && constrained
  }

  private def checkValidationCondition(
    eventType: EventType,
    eventState: EventState,
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): Boolean = {
    val result = eventType match {
      case EventType.Takeoff  => TakeoffDetection.checkValidation(eventState, config.takeoff)
      case EventType.Freefall => FreefallDetection.checkValidation(eventState, config.freefall)
      case EventType.Canopy   => CanopyDetection.checkValidation(eventState, config.canopy)
      case EventType.Landing  => LandingDetection.checkValidation(eventState, kinematics, config.landing)
    }
    debug(s"[VALIDATION CHECK] $eventType: valid=$result")
    result
  }

  // ─── State Updates ─────────────────────────────────────────────────────────

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

  // ─── Output Generation ─────────────────────────────────────────────────────

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
