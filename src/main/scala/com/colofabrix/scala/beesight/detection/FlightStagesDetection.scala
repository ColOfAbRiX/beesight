package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model.*
import com.colofabrix.scala.beesight.model.*
import cats.data.Reader

object FlightStagesDetection {

  // ─── Debug Configuration ───────────────────────────────────────────────────

  private val DEBUG_ENABLED = false

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
    input => {
      def processWithDrain(s: fs2.Stream[F, (Option[ProcessingState[A]], Vector[OutputFlightRow[A]])]): fs2.Pull[
        F,
        OutputFlightRow[A],
        Unit,
      ] =
        s.pull.uncons1.flatMap {
          case None =>
            fs2.Pull.done

          case Some(((state, outputs), remaining)) =>
            // Check if this is the last element by peeking at remaining
            remaining.pull.uncons1.flatMap {
              case None =>
                // This was the last element - emit outputs + flush buffer
                val finalOutputs = state match {
                  case Some(st) =>
                    debug(s"[DRAIN] Flushing ${st.pendingBuffer.size} remaining buffered points")
                    outputs ++ st.pendingBuffer.map(_.toOutputRow)
                  case None =>
                    outputs
                }
                fs2.Pull.output(fs2.Chunk.from(finalOutputs))

              case Some((next, rest)) =>
                // Not last - emit current outputs and continue
                fs2.Pull.output(fs2.Chunk.from(outputs)) >>
                processWithDrain(fs2.Stream(next) ++ rest)
            }
        }

      val mainStream = input
        .map(FileFormatAdapter[A].toInputFlightPoint)
        .zipWithIndex
        .mapAccumulate(Option.empty[ProcessingState[A]]) {
          case (maybeState, (point, idx)) =>
            val result =
              maybeState match {
                case None =>
                  debug(s"[INIT] Creating initial state at index 0")
                  val initialState = createInitialState(point, config)
                  // Add the initial state to its own buffer so it gets flushed at end of stream
                  ProcessingResult(
                    initialState.copy(pendingBuffer = Vector(initialState)),
                    Vector.empty,
                  )
                case Some(state) =>
                  processPoint(state, point, idx, config)
              }
            (Some(result.nextState), result.outputs)
        }

      processWithDrain(mainStream).stream
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
        val validationWindow = config.getValidationWindowSize(eventType)
        val bufferedState    = state.addToBuffer()
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
        val newBuffer = state.addToBuffer()

        if (newBuffer.size > maxBuffer) {
          val output = newBuffer.head.toOutputRow
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
    val bufferedState = state.addToBuffer()
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
    val eventState = state.getEventState(eventType)
    val isValid    = checkValidationCondition(eventType, eventState, kinematics, config)
    val fullBuffer = state.addToBuffer()

    debug(s"[VALIDATION FINALIZE] Event: $eventType, Valid: $isValid, Buffer size: ${fullBuffer.size}")

    if (isValid) {
      debug(s"[VALIDATION] ✓✓ $eventType CONFIRMED!")

      val backtrackSize     = config.getBacktrackWindowSize(eventType)
      val triggerStateIndex = (backtrackSize - 1).min(fullBuffer.size - 1)
      val triggerState      = fullBuffer(triggerStateIndex)
      val triggerEventState = triggerState.getEventState(eventType)
      val isRising          = eventType == EventType.Takeoff || eventType == EventType.Freefall
      val minSpeedDelta     = config.global.inflectionMinSpeedDelta
      val inflectionPoint   =
        InflectionFinder.findInflectionPoint(triggerEventState.backtrackWindow, isRising, minSpeedDelta)

      debug(s"[INFLECTION] Looking for inflection point (isRising=$isRising)")

      inflectionPoint match {
        case Some(fp) =>
          debug(s"[INFLECTION] ✓ Found at index ${fp.index}, altitude ${fp.altitude}m")
        case None =>
          debug(s"[INFLECTION] ✗ No inflection point found")
      }

      val resumeState =
        inflectionPoint match {
          case Some(fp) => fullBuffer.find(_.index > fp.index).getOrElse(fullBuffer.last)
          case None     => fullBuffer.last
        }
      debug(s"[RESUME] Resuming from index ${resumeState.index}")

      val newEvents = resumeState.detectedEvents.updateDetectedEvents(eventType, inflectionPoint)
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
      val outputs     = fullBuffer.map(_.toOutputRow)
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
        (EventType.Freefall, state.detectedEvents.freefall.isEmpty),
        (EventType.Canopy, state.detectedEvents.freefall.isDefined && state.detectedEvents.canopy.isEmpty),
        (
          EventType.Landing,
          (state.detectedEvents.canopy.isDefined || state.detectedEvents.takeoff.isDefined) && state.detectedEvents.landing.isEmpty,
        ),
      )

    val eligibleCandidates = candidates.filter(_._2).map(_._1)
    debug(s"[DETECT] Eligible events to check: ${eligibleCandidates.mkString(", ")}")

    val result =
      candidates.collectFirst {
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
    val eventState = state.getEventState(eventType)

    val triggered =
      eventType match {
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

    val constrained =
      eventType match {
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
    val result =
      eventType match {
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

  // ─── Output Generation ─────────────────────────────────────────────────────

  private def releaseBuffer[A](
    buffer: Vector[ProcessingState[A]],
    eventType: EventType,
    inflectionPoint: Option[FlightPoint],
  ): Vector[OutputFlightRow[A]] =
    inflectionPoint match {
      case None =>
        buffer.map(_.toOutputRow)
      case Some(fp) =>
        buffer.map { state =>
          val updatedEvents =
            if (state.index >= fp.index)
              state.detectedEvents.updateDetectedEvents(eventType, Some(fp))
            else
              state.detectedEvents
          OutputFlightRow(
            takeoff = updatedEvents.takeoff,
            freefall = updatedEvents.freefall,
            canopy = updatedEvents.canopy,
            landing = updatedEvents.landing,
            source = state.currentPoint.source,
          )
        }
    }

}
