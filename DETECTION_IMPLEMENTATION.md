# Flight Stages Detection - Implementation Plan

## Table of Contents

1. [Overview](#1-overview)
2. [Data Structures](#2-data-structures)
3. [Scala Files](#3-scala-files)
4. [Algorithm Implementation](#4-algorithm-implementation)
5. [Handling Wrong Data](#5-handling-wrong-data)
6. [Testing Strategy](#6-testing-strategy)
7. [Rollout Plan](#7-rollout-plan)
8. [Action Items Checklist](#8-action-items-checklist)

---

## 1. Overview

### 1.1 Purpose

This document provides a comprehensive implementation plan for the flight stages detection algorithm. The algorithm processes GPS altimeter data from skydiving jumps to automatically detect four flight events:

1. **Takeoff** - Aircraft begins movement
2. **Freefall** - Jumper exits aircraft
3. **Canopy** - Parachute opens
4. **Landing** - Jumper touches ground

### 1.2 Core Concept

The algorithm uses a **streaming approach** with an fs2 `Pipe` that processes each data point as it arrives. Detection follows a three-phase pattern:

```
Detect → Validate → Backtrack → Release
```

**Why this approach?**
- **Streaming**: Memory efficient, processes one point at a time
- **Validation**: Prevents false positives from GPS spikes
- **Backtracking**: Finds the true inflection point, not just when threshold was crossed
- **Buffering**: Allows correcting past output once true event is confirmed

### 1.3 Preserved Signature

The main entry point signature must be preserved:

```scala
def streamDetectA[F[_], A](using A: FileFormatAdapter[A]): fs2.Pipe[F, A, OutputFlightRow[A]]
```

### 1.4 Key Design Decisions

1. **Simplified Buffer**: Buffer stores full `ProcessingState[A]` rather than a separate `BufferedState` type. Slight memory overhead but significantly simpler code.

2. **Use Breeze for Statistics**: Use the Breeze library (already in project) for median and other statistical computations.

3. **Unified Detection Interface**: All detection modules receive their own `EventState` with consistent signatures.

4. **ProcessingResult Type**: Avoid tuples by using explicit result types.

5. **Centralized Phase Computation**: Single function determines current phase from detected events.

### 1.5 Configuration Values Preserved

From existing `DetectionConfig`, these values are preserved:

| Parameter | Value | Purpose |
|-----------|-------|---------|
| `TakeoffSpeedThreshold` | 25.0 m/s | Min horizontal speed for takeoff |
| `TakeoffClimbRate` | -1.0 m/s | Max vertical speed (ascending) |
| `TakeoffMaxAltitude` | 600.0 m | Max altitude for takeoff detection |
| `FreefallVerticalSpeedThreshold` | 25.0 m/s | Speed threshold for freefall |
| `FreefallAccelThreshold` | 3.0 m/s² | Acceleration threshold |
| `FreefallAccelMinVelocity` | 10.0 m/s | Min velocity for accel detection |
| `FreefallMinAltitudeAbove` | 600.0 m | Min altitude above takeoff |
| `FreefallMinAltitudeAbsolute` | 600.0 m | Min absolute altitude |
| `CanopyVerticalSpeedMax` | 12.0 m/s | Max vertical speed under canopy |
| `LandingSpeedMax` | 3.0 m/s | Max total speed for landing |
| `LandingStabilityThreshold` | 0.5 m/s | Max stdDev of vertical speed |
| `LandingMeanVerticalSpeedMax` | 1.0 m/s | Max mean vertical speed |
| `LandingStabilityWindowSize` | 10 | Window for stability check |
| `ClipAcceleration` | 20.0 m/s² | Max acceleration for spike clipping |
| `SmoothingVerticalSpeedWindowSize` | 5 | Median filter window size |
| `BacktrackVerticalSpeedWindowSize` | 10 | Samples for inflection search |
| `*BacktrackWindow` | 10 | Per-event backtrack windows |
| `*ValidationWindow` | 40 | Per-event validation windows |

---

## 2. Data Structures

### 2.1 Existing Structures (Modifications Required)

#### 2.1.1 FlightPhase (UPDATE)

**Current:**
```scala
enum FlightPhase(val sequence: Int) {
  case BeforeTakeoff extends FlightPhase(0)
  case Takeoff       extends FlightPhase(1)  // RENAME
  case Freefall      extends FlightPhase(2)
  case Canopy        extends FlightPhase(3)  // RENAME
  case Landing       extends FlightPhase(4)  // RENAME
}
```

**Updated:**
```scala
enum FlightPhase(val sequence: Int) {
  case BeforeTakeoff extends FlightPhase(0)
  case Climbing      extends FlightPhase(1)  // Was: Takeoff
  case Freefall      extends FlightPhase(2)
  case UnderCanopy   extends FlightPhase(3)  // Was: Canopy
  case Landed        extends FlightPhase(4)  // Was: Landing
}
```

**Reasoning:** The requirements specify phases as periods between events. "Climbing" represents the period after takeoff (the event), "UnderCanopy" is clearer than "Canopy" (which is also the event name), and "Landed" represents the state after landing.

#### 2.1.2 OutputFlightRow (UPDATE)

**Current:**
```scala
final case class OutputFlightRow[A](
  phase: FlightPhase,
  takeoff: Option[FlightPoint],
  freefall: Option[FlightPoint],
  canopy: Option[FlightPoint],
  landing: Option[FlightPoint],
  lastPoint: Long,  // REMOVE
  source: A,
)
```

**Updated:**
```scala
final case class OutputFlightRow[A](
  phase: FlightPhase,
  takeoff: Option[FlightPoint],
  freefall: Option[FlightPoint],
  canopy: Option[FlightPoint],
  landing: Option[FlightPoint],
  source: A,
)
```

**Reasoning:** Per requirements section 3, `lastPoint` is removed. The event index is now tracked via `FlightPoint.index` which indicates when the event was first set.

#### 2.1.3 DetectionConfig (RESTRUCTURE)

**Current:** Flat structure with 25+ fields

**Updated:** Hierarchical structure for better organization and module isolation

```scala
final case class DetectionConfig(
  global: GlobalConfig,
  takeoff: TakeoffConfig,
  freefall: FreefallConfig,
  canopy: CanopyConfig,
  landing: LandingConfig,
)

final case class GlobalConfig(
  accelerationClip: Double,  // 20.0 m/s²
)

final case class TakeoffConfig(
  speedThreshold: Double,       // 25.0 m/s
  climbRate: Double,            // -1.0 m/s
  maxAltitude: Double,          // 600.0 m
  smoothingWindowSize: Int,     // 5
  backtrackWindowSize: Int,     // 10
  validationWindowSize: Int,    // 40
)

final case class FreefallConfig(
  verticalSpeedThreshold: Double,    // 25.0 m/s
  accelerationThreshold: Double,     // 3.0 m/s²
  accelerationMinVelocity: Double,   // 10.0 m/s
  minAltitudeAbove: Double,          // 600.0 m
  minAltitudeAbsolute: Double,       // 600.0 m
  smoothingWindowSize: Int,          // 5
  backtrackWindowSize: Int,          // 10
  validationWindowSize: Int,         // 40
)

final case class CanopyConfig(
  verticalSpeedMax: Double,     // 12.0 m/s
  smoothingWindowSize: Int,     // 5
  backtrackWindowSize: Int,     // 10
  validationWindowSize: Int,    // 40
)

final case class LandingConfig(
  speedMax: Double,               // 3.0 m/s
  stabilityThreshold: Double,     // 0.5 m/s
  meanVerticalSpeedMax: Double,   // 1.0 m/s
  stabilityWindowSize: Int,       // 10
  smoothingWindowSize: Int,       // 5
  backtrackWindowSize: Int,       // 10
  validationWindowSize: Int,      // 40
)
```

**Reasoning:**
- **Module Isolation**: Each detection module receives only its own config
- **Maintainability**: Easier to adjust parameters for individual events
- **Clarity**: Groups related parameters together

### 2.2 New State Structures

#### 2.2.1 VerticalSpeedSample

```scala
final case class VerticalSpeedSample(
  index: Long,
  speed: Double,
  altitude: Double,
)
```

**Purpose:** Captures a single sample in the backtrack window with all data needed for inflection point detection.

**Example:**
```scala
val sample = VerticalSpeedSample(
  index = 142,
  speed = 35.2,      // Vertical speed at this point
  altitude = 2450.0, // Altitude at this point
)
```

#### 2.2.2 EventState

```scala
final case class EventState(
  smoothingWindow: Vector[Double],
  backtrackWindow: Vector[VerticalSpeedSample],
)
```

**Purpose:** Per-event sliding windows for smoothing and backtracking.

**Why separate per event?** Each event type may need different window sizes and the windows must be independent. For example:
- Takeoff detection continues updating its windows during freefall detection
- Freefall backtrack window shouldn't be polluted with canopy data

**Example:**
```scala
val freefallState = EventState(
  smoothingWindow = Vector(10.2, 15.3, 22.1, 28.4, 35.2),  // Last 5 speeds
  backtrackWindow = Vector(
    VerticalSpeedSample(138, 10.2, 2550.0),
    VerticalSpeedSample(139, 15.3, 2530.0),
    // ... more samples
  ),
)
```

#### 2.2.3 StreamPhase and EventType

```scala
enum EventType {
  case Takeoff
  case Freefall
  case Canopy
  case Landing
}

enum StreamPhase {
  case Streaming
  case Validation(remainingPoints: Int, eventType: EventType)
}
```

**Purpose:** Tracks whether we're in normal streaming mode or validating a potential detection.

> **📖 See [Section 4.3: Stream Emission Behavior](#43-stream-emission-behavior) for the complete specification of when and what the stream emits during each phase, including the "As If Validation Never Happened" principle, buffer behavior, and emission examples.**

#### 2.2.4 DetectedEvents

```scala
final case class DetectedEvents(
  takeoff: Option[FlightPoint],
  freefall: Option[FlightPoint],
  canopy: Option[FlightPoint],
  landing: Option[FlightPoint],
)

object DetectedEvents {
  val empty: DetectedEvents = DetectedEvents(
    takeoff = None,
    freefall = None,
    canopy = None,
    landing = None,
  )
}
```

**Purpose:** Accumulates detected events as they are confirmed.

**Example progression:**
```scala
// Initially
DetectedEvents(None, None, None, None)

// After takeoff confirmed at index 50
DetectedEvents(Some(FlightPoint(50, 120.0)), None, None, None)

// After freefall confirmed at index 142
DetectedEvents(Some(FlightPoint(50, 120.0)), Some(FlightPoint(142, 4200.0)), None, None)

// etc.
```

#### 2.2.5 PointKinematics

```scala
final case class PointKinematics(
  rawVerticalSpeed: Double,
  rawNorthSpeed: Double,
  rawEastSpeed: Double,
  clippedVerticalSpeed: Double,
  clippedNorthSpeed: Double,
  clippedEastSpeed: Double,
  correctedAltitude: Double,
  horizontalSpeed: Double,
  totalSpeed: Double,
  deltaTime: Double,
)
```

**Purpose:** Holds all computed kinematics for a single point after preprocessing.

**Calculations:**
```scala
// Horizontal speed (ground speed)
horizontalSpeed = sqrt(clippedNorthSpeed² + clippedEastSpeed²)

// Total speed (3D)
totalSpeed = sqrt(horizontalSpeed² + clippedVerticalSpeed²)
```

**Example:**
```scala
val kinematics = PointKinematics(
  rawVerticalSpeed = 150.0,      // GPS spike!
  rawNorthSpeed = 5.0,
  rawEastSpeed = 3.0,
  clippedVerticalSpeed = 55.0,   // Clipped to max 20 m/s² change
  clippedNorthSpeed = 5.0,
  clippedEastSpeed = 3.0,
  correctedAltitude = 2400.0,    // Recalculated from clipped speed
  horizontalSpeed = 5.83,        // sqrt(25 + 9)
  totalSpeed = 55.31,            // sqrt(5.83² + 55²)
  deltaTime = 0.2,               // 5 Hz sample rate
)
```

#### 2.2.6 ProcessingState

```scala
final case class ProcessingState[A](
  index: Long,
  point: InputFlightRow[A],
  previousPoint: Option[InputFlightRow[A]],
  kinematics: PointKinematics,
  streamPhase: StreamPhase,
  detectedEvents: DetectedEvents,
  pendingBuffer: Vector[BufferedState[A]],
  takeoffState: EventState,
  freefallState: EventState,
  canopyState: EventState,
  landingState: EventState,
)

final case class BufferedState[A](
  index: Long,
  point: InputFlightRow[A],
  kinematics: PointKinematics,
  detectedEvents: DetectedEvents,
  phase: FlightPhase,
)
```

**Purpose:** Complete state for the streaming algorithm.

**Key Insight:** The `pendingBuffer` stores complete `ProcessingState[A]` objects (not just raw data points) to enable "time travel" when validation completes. Each buffered entry contains the input point, kinematics, event state windows, and detectedEvents at that index.

> **📖 See [Section 4.3: Stream Emission Behavior](#43-stream-emission-behavior) for detailed buffer visualization, when/how the buffer is released, and the "time travel" mechanism for resuming from buffered states.**

---

## 3. Scala Files

This section details every Scala file to be created or modified, organized by package.

### 3.1 File Structure Overview

```
src/main/scala/com/colofabrix/scala/beesight/
├── config/
│   └── DetectionConfig.scala          [MODIFY - restructure to hierarchical]
├── model/
│   ├── FlightPhase.scala              [MODIFY - rename phases]
│   └── OutputFlightRow.scala          [MODIFY - remove lastPoint]
└── detection/
    ├── FlightStagesDetection.scala    [MODIFY - implement algorithm]
    ├── Preprocessing.scala            [NEW - spike clipping]
    ├── Kinematics.scala               [NEW - speed calculations]
    ├── Smoothing.scala                [NEW - median filter]
    ├── InflectionFinder.scala         [NEW - backtrack logic]
    ├── BufferManager.scala            [NEW - buffer operations]
    ├── TakeoffDetection.scala         [NEW - takeoff detector]
    ├── FreefallDetection.scala        [NEW - freefall detector]
    ├── CanopyDetection.scala          [NEW - canopy detector]
    ├── LandingDetection.scala         [NEW - landing detector]
    └── model/
        ├── EventState.scala           [NEW - per-event state]
        ├── StreamPhase.scala          [NEW - streaming state machine]
        ├── DetectedEvents.scala       [NEW - accumulated events]
        ├── PointKinematics.scala      [NEW - computed kinematics]
        ├── ProcessingState.scala      [NEW - full processing state]
        └── BufferedState.scala        [NEW - buffered output state]
```

### 3.2 Modified Files

#### 3.2.1 config/DetectionConfig.scala

**Purpose:** Restructure from flat to hierarchical configuration.

**Why hierarchical?**
- Each detection module receives only its relevant config
- Prevents accidental cross-contamination between modules
- Makes parameter tuning clearer and more maintainable

```scala
package com.colofabrix.scala.beesight.config

final case class DetectionConfig(
  global: GlobalConfig,
  takeoff: TakeoffConfig,
  freefall: FreefallConfig,
  canopy: CanopyConfig,
  landing: LandingConfig,
)

final case class GlobalConfig(
  accelerationClip: Double,
)

final case class TakeoffConfig(
  speedThreshold: Double,
  climbRate: Double,
  maxAltitude: Double,
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)

final case class FreefallConfig(
  verticalSpeedThreshold: Double,
  accelerationThreshold: Double,
  accelerationMinVelocity: Double,
  minAltitudeAbove: Double,
  minAltitudeAbsolute: Double,
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)

final case class CanopyConfig(
  verticalSpeedMax: Double,
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)

final case class LandingConfig(
  speedMax: Double,
  stabilityThreshold: Double,
  meanVerticalSpeedMax: Double,
  stabilityWindowSize: Int,
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)

object DetectionConfig {
  val default: DetectionConfig = DetectionConfig(
    global = GlobalConfig(
      accelerationClip = 20.0,
    ),
    takeoff = TakeoffConfig(
      speedThreshold = 25.0,
      climbRate = -1.0,
      maxAltitude = 600.0,
      smoothingWindowSize = 5,
      backtrackWindowSize = 10,
      validationWindowSize = 40,
    ),
    freefall = FreefallConfig(
      verticalSpeedThreshold = 25.0,
      accelerationThreshold = 3.0,
      accelerationMinVelocity = 10.0,
      minAltitudeAbove = 600.0,
      minAltitudeAbsolute = 600.0,
      smoothingWindowSize = 5,
      backtrackWindowSize = 10,
      validationWindowSize = 40,
    ),
    canopy = CanopyConfig(
      verticalSpeedMax = 12.0,
      smoothingWindowSize = 5,
      backtrackWindowSize = 10,
      validationWindowSize = 40,
    ),
    landing = LandingConfig(
      speedMax = 3.0,
      stabilityThreshold = 0.5,
      meanVerticalSpeedMax = 1.0,
      stabilityWindowSize = 10,
      smoothingWindowSize = 5,
      backtrackWindowSize = 10,
      validationWindowSize = 40,
    ),
  )
}
```

#### 3.2.2 model/FlightPhase.scala

**Purpose:** Rename phases to represent periods between events.

```scala
package com.colofabrix.scala.beesight.model

enum FlightPhase(val sequence: Int) {
  case BeforeTakeoff extends FlightPhase(0)
  case Climbing      extends FlightPhase(1)
  case Freefall      extends FlightPhase(2)
  case UnderCanopy   extends FlightPhase(3)
  case Landed        extends FlightPhase(4)
}
```

#### 3.2.3 model/OutputFlightRow.scala

**Purpose:** Remove `lastPoint` field per requirements.

```scala
package com.colofabrix.scala.beesight.model

final case class OutputFlightRow[A](
  phase: FlightPhase,
  takeoff: Option[FlightPoint],
  freefall: Option[FlightPoint],
  canopy: Option[FlightPoint],
  landing: Option[FlightPoint],
  source: A,
)
```

### 3.3 New Files - Detection Model Package

#### 3.3.1 detection/model/EventState.scala

**Purpose:** Per-event sliding windows for smoothing and backtracking.

```scala
package com.colofabrix.scala.beesight.detection.model

final case class VerticalSpeedSample(
  index: Long,
  speed: Double,
  altitude: Double,
)

final case class EventState(
  smoothingWindow: Vector[Double],
  backtrackWindow: Vector[VerticalSpeedSample],
)

object EventState {
  val empty: EventState = EventState(
    smoothingWindow = Vector.empty,
    backtrackWindow = Vector.empty,
  )
}
```

#### 3.3.2 detection/model/StreamPhase.scala

**Purpose:** State machine for streaming vs validation phases.

```scala
package com.colofabrix.scala.beesight.detection.model

enum EventType {
  case Takeoff
  case Freefall
  case Canopy
  case Landing
}

enum StreamPhase {
  case Streaming
  case Validation(remainingPoints: Int, eventType: EventType)
}
```

#### 3.3.3 detection/model/DetectedEvents.scala

**Purpose:** Accumulate confirmed flight events.

```scala
package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.model.FlightPoint

final case class DetectedEvents(
  takeoff: Option[FlightPoint],
  freefall: Option[FlightPoint],
  canopy: Option[FlightPoint],
  landing: Option[FlightPoint],
)

object DetectedEvents {
  val empty: DetectedEvents = DetectedEvents(
    takeoff = None,
    freefall = None,
    canopy = None,
    landing = None,
  )
}
```

#### 3.3.4 detection/model/PointKinematics.scala

**Purpose:** Hold computed kinematics after preprocessing.

```scala
package com.colofabrix.scala.beesight.detection.model

final case class PointKinematics(
  rawVerticalSpeed: Double,
  rawNorthSpeed: Double,
  rawEastSpeed: Double,
  clippedVerticalSpeed: Double,
  clippedNorthSpeed: Double,
  clippedEastSpeed: Double,
  correctedAltitude: Double,
  horizontalSpeed: Double,
  totalSpeed: Double,
  deltaTime: Double,
)
```

#### 3.3.5 detection/model/ProcessingState.scala

**Purpose:** Complete algorithm state carried between points.

```scala
package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.model.InputFlightRow

final case class ProcessingState[A](
  index: Long,
  point: InputFlightRow[A],
  previousPoint: Option[InputFlightRow[A]],
  kinematics: PointKinematics,
  streamPhase: StreamPhase,
  detectedEvents: DetectedEvents,
  pendingBuffer: Vector[BufferedState[A]],
  takeoffState: EventState,
  freefallState: EventState,
  canopyState: EventState,
  landingState: EventState,
)
```

#### 3.3.6 detection/model/BufferedState.scala

**Purpose:** Minimal state stored in pending buffer for output generation.

```scala
package com.colofabrix.scala.beesight.detection.model

import com.colofabrix.scala.beesight.model.{FlightPhase, InputFlightRow}

final case class BufferedState[A](
  index: Long,
  point: InputFlightRow[A],
  kinematics: PointKinematics,
  detectedEvents: DetectedEvents,
  phase: FlightPhase,
)
```

### 3.4 New Files - Core Algorithm Components

#### 3.4.1 detection/Preprocessing.scala

**Purpose:** Spike mitigation via acceleration clipping.

**Why clipping?** GPS can produce sudden spikes of 100+ m/s velocity. Physical reality constrains acceleration to ~20 m/s² maximum (terminal velocity transition). Clipping enforces this physical constraint.

```scala
package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.GlobalConfig
import com.colofabrix.scala.beesight.detection.model.PointKinematics
import com.colofabrix.scala.beesight.model.InputFlightRow

object Preprocessing {

  def computeKinematics[A](
    current: InputFlightRow[A],
    previous: Option[InputFlightRow[A]],
    previousKinematics: Option[PointKinematics],
    config: GlobalConfig,
  ): PointKinematics = {
    val deltaTime = previous.fold(0.2) { prev =>
      java.time.Duration.between(prev.time, current.time).toMillis / 1000.0
    }

    val (clippedVert, clippedNorth, clippedEast) = previousKinematics match {
      case None => (current.verticalSpeed, current.northSpeed, current.eastSpeed)
      case Some(prevK) =>
        (
          clipSpeed(current.verticalSpeed, prevK.clippedVerticalSpeed, deltaTime, config.accelerationClip),
          clipSpeed(current.northSpeed, prevK.clippedNorthSpeed, deltaTime, config.accelerationClip),
          clipSpeed(current.eastSpeed, prevK.clippedEastSpeed, deltaTime, config.accelerationClip),
        )
    }

    val correctedAltitude = previousKinematics match {
      case None => current.altitude
      case Some(prevK) =>
        if (clippedVert != current.verticalSpeed)
          prevK.correctedAltitude - clippedVert * deltaTime
        else
          current.altitude
    }

    val horizontalSpeed = math.sqrt(clippedNorth * clippedNorth + clippedEast * clippedEast)
    val totalSpeed = math.sqrt(horizontalSpeed * horizontalSpeed + clippedVert * clippedVert)

    PointKinematics(
      rawVerticalSpeed = current.verticalSpeed,
      rawNorthSpeed = current.northSpeed,
      rawEastSpeed = current.eastSpeed,
      clippedVerticalSpeed = clippedVert,
      clippedNorthSpeed = clippedNorth,
      clippedEastSpeed = clippedEast,
      correctedAltitude = correctedAltitude,
      horizontalSpeed = horizontalSpeed,
      totalSpeed = totalSpeed,
      deltaTime = deltaTime,
    )
  }

  private def clipSpeed(
    current: Double,
    previous: Double,
    deltaTime: Double,
    maxAcceleration: Double,
  ): Double = {
    val delta = current - previous
    val maxDelta = maxAcceleration * deltaTime
    if (math.abs(delta) > maxDelta)
      previous + math.signum(delta) * maxDelta
    else
      current
  }
}
```

**Example: Spike Clipping**
```
Previous vertical speed: 35.0 m/s
Current raw vertical speed: 150.0 m/s  (GPS spike!)
Delta time: 0.2s
Max acceleration: 20.0 m/s²
Max allowed change: 20.0 * 0.2 = 4.0 m/s

Actual change: 150.0 - 35.0 = 115.0 m/s  (exceeds max!)
Clipped speed: 35.0 + 4.0 = 39.0 m/s
```

#### 3.4.2 detection/Smoothing.scala

**Purpose:** Median filter for GPS noise reduction.

**Why median filter?** Unlike mean, median is robust to outliers. A single spike won't affect the median value significantly.

```scala
package com.colofabrix.scala.beesight.detection

object Smoothing {

  def updateWindow(window: Vector[Double], value: Double, maxSize: Int): Vector[Double] = {
    val updated = window :+ value
    if (updated.size > maxSize) updated.drop(1) else updated
  }

  def median(window: Vector[Double]): Double = {
    if (window.isEmpty) 0.0
    else {
      val sorted = window.sorted
      val n = sorted.size
      if (n % 2 == 0)
        (sorted(n / 2 - 1) + sorted(n / 2)) / 2.0
      else
        sorted(n / 2)
    }
  }

  def computeAcceleration(
    currentSmoothed: Double,
    previousSmoothed: Double,
    deltaTime: Double,
  ): Double = {
    if (deltaTime <= 0) 0.0
    else (currentSmoothed - previousSmoothed) / deltaTime
  }
}
```

**Example: Median Smoothing**
```
Window: [10.2, 15.3, 150.0, 22.1, 28.4]  (contains spike at 150.0)
Sorted: [10.2, 15.3, 22.1, 28.4, 150.0]
Median: 22.1  (spike ignored!)
```

#### 3.4.3 detection/InflectionFinder.scala

**Purpose:** Find the true transition point via backtracking.

**Why backtrack?** When we cross a threshold (e.g., 25 m/s for freefall), the jumper may have actually exited earlier. We search backwards to find where velocity started rising (for freefall) or falling (for canopy/landing).

```scala
package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.detection.model.VerticalSpeedSample
import com.colofabrix.scala.beesight.model.FlightPoint

object InflectionFinder {

  def findInflectionPoint(
    backtrackWindow: Vector[VerticalSpeedSample],
    isRising: Boolean,
  ): Option[FlightPoint] = {
    if (backtrackWindow.size < 2) {
      backtrackWindow.headOption.map(s => FlightPoint(s.index, s.altitude))
    } else {
      val pairs = backtrackWindow.sliding(2).toVector
      val inflectionIndex = pairs.indexWhere { case Vector(prev, curr) =>
        if (isRising) curr.speed > prev.speed
        else curr.speed < prev.speed
      }

      if (inflectionIndex >= 0) {
        val sample = backtrackWindow(inflectionIndex)
        Some(FlightPoint(sample.index, sample.altitude))
      } else {
        backtrackWindow.headOption.map(s => FlightPoint(s.index, s.altitude))
      }
    }
  }
}
```

**Example: Finding Freefall Inflection (isRising = true)**
```
Backtrack window (oldest first):
  Index 140: speed = 5.0 m/s   (plane speed)
  Index 141: speed = 4.8 m/s   (still in plane, slightly descending)
  Index 142: speed = 8.2 m/s   ← Inflection! Speed starts rising
  Index 143: speed = 15.3 m/s
  Index 144: speed = 22.1 m/s
  Index 145: speed = 28.4 m/s  (threshold crossed here, but NOT the exit point)

Result: FlightPoint(142, altitude_at_142)
```

#### 3.4.4 detection/BufferManager.scala

**Purpose:** Manage the pending output buffer during streaming and validation.

> **📖 See [Section 4.3: Stream Emission Behavior](#43-stream-emission-behavior) for detailed specifications of when the buffer is released, what phases are assigned, and comprehensive emission examples.**

```scala
package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.detection.model.{BufferedState, DetectedEvents, PointKinematics}
import com.colofabrix.scala.beesight.model.{FlightPhase, FlightPoint, InputFlightRow, OutputFlightRow}

object BufferManager {

  def addToBuffer[A](
    buffer: Vector[BufferedState[A]],
    index: Long,
    point: InputFlightRow[A],
    kinematics: PointKinematics,
    events: DetectedEvents,
    phase: FlightPhase,
    maxSize: Int,
  ): Vector[BufferedState[A]] = {
    val state = BufferedState(index, point, kinematics, events, phase)
    val updated = buffer :+ state
    if (updated.size > maxSize) updated.drop(1) else updated
  }

  def releaseBuffer[A](
    buffer: Vector[BufferedState[A]],
    confirmedEvent: Option[(FlightPoint, EventType)],
  ): Vector[OutputFlightRow[A]] = {
    confirmedEvent match {
      case None =>
        buffer.map(toOutputRow)
      case Some((eventPoint, eventType)) =>
        buffer.map { state =>
          val updatedEvents = updateEvents(state.detectedEvents, eventPoint, eventType, state.index)
          val updatedPhase = computePhase(updatedEvents)
          toOutputRowWithEvents(state, updatedEvents, updatedPhase)
        }
    }
  }

  private def updateEvents(
    current: DetectedEvents,
    eventPoint: FlightPoint,
    eventType: EventType,
    currentIndex: Long,
  ): DetectedEvents = {
    val shouldSet = currentIndex >= eventPoint.index
    eventType match {
      case EventType.Takeoff =>
        if (shouldSet) DetectedEvents(Some(eventPoint), current.freefall, current.canopy, current.landing)
        else current
      case EventType.Freefall =>
        if (shouldSet) DetectedEvents(current.takeoff, Some(eventPoint), current.canopy, current.landing)
        else current
      case EventType.Canopy =>
        if (shouldSet) DetectedEvents(current.takeoff, current.freefall, Some(eventPoint), current.landing)
        else current
      case EventType.Landing =>
        if (shouldSet) DetectedEvents(current.takeoff, current.freefall, current.canopy, Some(eventPoint))
        else current
    }
  }

  private def computePhase(events: DetectedEvents): FlightPhase = {
    if (events.landing.isDefined) FlightPhase.Landed
    else if (events.canopy.isDefined) FlightPhase.UnderCanopy
    else if (events.freefall.isDefined) FlightPhase.Freefall
    else if (events.takeoff.isDefined) FlightPhase.Climbing
    else FlightPhase.BeforeTakeoff
  }

  private def toOutputRow[A](state: BufferedState[A]): OutputFlightRow[A] =
    OutputFlightRow(
      phase = state.phase,
      takeoff = state.detectedEvents.takeoff,
      freefall = state.detectedEvents.freefall,
      canopy = state.detectedEvents.canopy,
      landing = state.detectedEvents.landing,
      source = state.point.source,
    )

  private def toOutputRowWithEvents[A](
    state: BufferedState[A],
    events: DetectedEvents,
    phase: FlightPhase,
  ): OutputFlightRow[A] =
    OutputFlightRow(
      phase = phase,
      takeoff = events.takeoff,
      freefall = events.freefall,
      canopy = events.canopy,
      landing = events.landing,
      source = state.point.source,
    )
}
```

**Example: Buffer Release with Freefall Confirmation**
```
Buffer before release:
  [state140: freefall=None]
  [state141: freefall=None]
  [state142: freefall=None]  ← inflection point here
  [state143: freefall=None]
  ...
  [state155: freefall=None]

Confirmed event: FlightPoint(142, 4200.0)

Buffer after release:
  [state140: freefall=None, phase=Climbing]
  [state141: freefall=None, phase=Climbing]
  [state142: freefall=Some(FlightPoint(142, 4200.0)), phase=Freefall]  ← SET HERE
  [state143: freefall=Some(FlightPoint(142, 4200.0)), phase=Freefall]
  ...
  [state155: freefall=Some(FlightPoint(142, 4200.0)), phase=Freefall]
```

---

### 3.5 New Files - Detection Modules

Each detection module follows the same interface pattern for **module isolation**:

```scala
object XxxDetection {
  def checkTrigger(...): Boolean           // Does condition trigger?
  def checkConstraints(...): Boolean       // Are prerequisites met?
  def checkValidation(...): Boolean        // Still valid after waiting?
}
```

**Why this pattern?**
- **Pure functions**: No side effects, easy to test
- **Isolation**: Each module knows only about its own config and relevant kinematics
- **Composability**: Main algorithm orchestrates, modules just answer questions

#### 3.5.1 detection/TakeoffDetection.scala

**Purpose:** Detect when the aircraft starts moving.

**Trigger Condition:**
```
horizontalSpeed > speedThreshold AND smoothedVerticalSpeed < climbRate
```

**Physical Reasoning:**
- Aircraft on ground has ~0 horizontal speed
- When rolling for takeoff, horizontal speed increases rapidly
- Climb rate is negative (ascending = negative vertical speed in our convention)
- Combined with max altitude check to avoid false triggers at cruise altitude

```scala
package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.TakeoffConfig
import com.colofabrix.scala.beesight.detection.model.{DetectedEvents, PointKinematics}

object TakeoffDetection {

  def checkTrigger(
    kinematics: PointKinematics,
    smoothedVerticalSpeed: Double,
    config: TakeoffConfig,
  ): Boolean = {
    kinematics.horizontalSpeed > config.speedThreshold &&
      smoothedVerticalSpeed < config.climbRate
  }

  def checkConstraints(
    kinematics: PointKinematics,
    events: DetectedEvents,
    config: TakeoffConfig,
  ): Boolean = {
    val notAlreadyDetected = events.takeoff.isEmpty
    val belowMaxAltitude = kinematics.correctedAltitude < config.maxAltitude
    notAlreadyDetected && belowMaxAltitude
  }

  def checkValidation(
    kinematics: PointKinematics,
    smoothedVerticalSpeed: Double,
    config: TakeoffConfig,
  ): Boolean = {
    smoothedVerticalSpeed < config.climbRate
  }
}
```

**Example Scenario:**
```
Aircraft on runway:
  Point 45: horizontalSpeed = 5 m/s, verticalSpeed = 0.1 m/s  → No trigger
  Point 46: horizontalSpeed = 15 m/s, verticalSpeed = -0.5 m/s → No trigger (speed < 25)
  Point 47: horizontalSpeed = 28 m/s, verticalSpeed = -1.5 m/s → TRIGGER!
    - horizontalSpeed (28) > speedThreshold (25) ✓
    - verticalSpeed (-1.5) < climbRate (-1.0) ✓
    - altitude (120m) < maxAltitude (600m) ✓
```

#### 3.5.2 detection/FreefallDetection.scala

**Purpose:** Detect when jumper exits the aircraft.

**Trigger Condition (OR logic):**
```
smoothedVerticalSpeed > verticalSpeedThreshold
OR
(smoothedVerticalAcceleration > accelerationThreshold AND smoothedVerticalSpeed > accelerationMinVelocity)
```

**Why OR logic?**
- **Speed-based**: Catches normal exits where speed builds gradually
- **Acceleration-based**: Catches aggressive exits where acceleration spikes before speed threshold is reached

**Physical Reasoning:**
- Terminal velocity for skydivers is 50-70 m/s
- Threshold at 25 m/s catches freefall early but not plane descent (2-8 m/s)
- Minimum altitude (600m) prevents false triggers from landing approach

```scala
package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.FreefallConfig
import com.colofabrix.scala.beesight.detection.model.{DetectedEvents, PointKinematics}
import com.colofabrix.scala.beesight.model.FlightPoint

object FreefallDetection {

  def checkTrigger(
    smoothedVerticalSpeed: Double,
    smoothedVerticalAcceleration: Double,
    config: FreefallConfig,
  ): Boolean = {
    val speedTriggered = smoothedVerticalSpeed > config.verticalSpeedThreshold
    val accelTriggered = smoothedVerticalAcceleration > config.accelerationThreshold &&
      smoothedVerticalSpeed > config.accelerationMinVelocity
    speedTriggered || accelTriggered
  }

  def checkConstraints(
    kinematics: PointKinematics,
    events: DetectedEvents,
    currentIndex: Long,
    config: FreefallConfig,
  ): Boolean = {
    val notAlreadyDetected = events.freefall.isEmpty

    val afterTakeoff = events.takeoff match {
      case Some(takeoffPoint) => currentIndex > takeoffPoint.index
      case None               => true
    }

    val aboveMinAltitude = events.takeoff match {
      case Some(takeoffPoint) =>
        kinematics.correctedAltitude > takeoffPoint.altitude + config.minAltitudeAbove ||
          kinematics.correctedAltitude > config.minAltitudeAbsolute
      case None =>
        kinematics.correctedAltitude > config.minAltitudeAbsolute
    }

    notAlreadyDetected && afterTakeoff && aboveMinAltitude
  }

  def checkValidation(
    smoothedVerticalSpeed: Double,
    config: FreefallConfig,
  ): Boolean = {
    smoothedVerticalSpeed > config.verticalSpeedThreshold * 0.8
  }
}
```

**Example Scenario - Speed-Based Exit:**
```
Jumper exits aircraft:
  Point 140: smoothedSpeed = 5 m/s, accel = 0.5 m/s² → No trigger
  Point 141: smoothedSpeed = 8 m/s, accel = 2.0 m/s² → No trigger
  Point 142: smoothedSpeed = 15 m/s, accel = 3.5 m/s² → No trigger (accel but speed < 10)
  Point 143: smoothedSpeed = 22 m/s, accel = 3.2 m/s² → TRIGGER (accel=3.2 > 3.0 AND speed > 10)
  Point 145: smoothedSpeed = 28 m/s → Also would trigger via speed path
```

**Example Scenario - Acceleration-Based Exit (Aggressive Exit):**
```
Experienced skydiver doing head-down exit:
  Point 140: smoothedSpeed = 5 m/s, accel = 0.5 m/s²
  Point 141: smoothedSpeed = 12 m/s, accel = 5.0 m/s² → TRIGGER!
    - accel (5.0) > accelerationThreshold (3.0) ✓
    - speed (12) > accelerationMinVelocity (10) ✓
```

#### 3.5.3 detection/CanopyDetection.scala

**Purpose:** Detect when parachute opens.

**Trigger Condition:**
```
smoothedVerticalSpeed > 0 AND smoothedVerticalSpeed < verticalSpeedMax
```

**Physical Reasoning:**
- Under canopy, descent rate is typically 4-8 m/s
- Threshold at 12 m/s catches the transition from freefall (50+ m/s) to canopy
- Must still be descending (> 0) to distinguish from updrafts

```scala
package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.CanopyConfig
import com.colofabrix.scala.beesight.detection.model.{DetectedEvents, PointKinematics}

object CanopyDetection {

  def checkTrigger(
    smoothedVerticalSpeed: Double,
    config: CanopyConfig,
  ): Boolean = {
    smoothedVerticalSpeed > 0 && smoothedVerticalSpeed < config.verticalSpeedMax
  }

  def checkConstraints(
    kinematics: PointKinematics,
    events: DetectedEvents,
    currentIndex: Long,
  ): Boolean = {
    val freefallDetected = events.freefall.isDefined

    val afterFreefall = events.freefall match {
      case Some(freefallPoint) => currentIndex > freefallPoint.index
      case None                => false
    }

    val belowFreefallAltitude = events.freefall match {
      case Some(freefallPoint) => kinematics.correctedAltitude < freefallPoint.altitude
      case None                => false
    }

    val aboveTakeoffAltitude = events.takeoff match {
      case Some(takeoffPoint) => kinematics.correctedAltitude > takeoffPoint.altitude
      case None               => true
    }

    freefallDetected && afterFreefall && belowFreefallAltitude && aboveTakeoffAltitude
  }

  def checkValidation(
    smoothedVerticalSpeed: Double,
    config: CanopyConfig,
  ): Boolean = {
    smoothedVerticalSpeed > 0 && smoothedVerticalSpeed < config.verticalSpeedMax * 1.5
  }
}
```

**Example Scenario:**
```
Parachute deployment:
  Point 280: smoothedSpeed = 52 m/s → No trigger (above max)
  Point 281: smoothedSpeed = 45 m/s → No trigger
  Point 282: smoothedSpeed = 30 m/s → No trigger
  Point 283: smoothedSpeed = 18 m/s → No trigger
  Point 284: smoothedSpeed = 10 m/s → TRIGGER!
    - speed (10) > 0 ✓
    - speed (10) < verticalSpeedMax (12) ✓
    - freefall detected ✓
    - below freefall altitude ✓
```

#### 3.5.4 detection/LandingDetection.scala

**Purpose:** Detect when jumper touches down.

**Trigger Condition:**
```
totalSpeed < speedMax AND windowIsStable
```

**Window Stability:**
```
stdDev(verticalSpeedWindow) < stabilityThreshold AND abs(mean(verticalSpeedWindow)) < meanVerticalSpeedMax
```

**Physical Reasoning:**
- On landing, all movement stops quickly
- Total speed (not just vertical) must be low to distinguish from swooping
- Stability check prevents false triggers during turbulence

```scala
package com.colofabrix.scala.beesight.detection

import com.colofabrix.scala.beesight.config.LandingConfig
import com.colofabrix.scala.beesight.detection.model.{DetectedEvents, PointKinematics}

object LandingDetection {

  def checkTrigger(
    kinematics: PointKinematics,
    stabilityWindow: Vector[Double],
    config: LandingConfig,
  ): Boolean = {
    val lowSpeed = kinematics.totalSpeed < config.speedMax
    val isStable = checkWindowStability(stabilityWindow, config)
    lowSpeed && isStable
  }

  def checkWindowStability(
    window: Vector[Double],
    config: LandingConfig,
  ): Boolean = {
    if (window.size < config.stabilityWindowSize) false
    else {
      val mean = window.sum / window.size
      val variance = window.map(v => math.pow(v - mean, 2)).sum / window.size
      val stdDev = math.sqrt(variance)
      stdDev < config.stabilityThreshold && math.abs(mean) < config.meanVerticalSpeedMax
    }
  }

  def checkConstraints(
    kinematics: PointKinematics,
    events: DetectedEvents,
    currentIndex: Long,
  ): Boolean = {
    val hasPrerequisite = events.canopy.isDefined || events.takeoff.isDefined

    val afterCanopy = events.canopy match {
      case Some(canopyPoint) => currentIndex > canopyPoint.index
      case None              => true
    }

    val belowCanopyAltitude = events.canopy match {
      case Some(canopyPoint) => kinematics.correctedAltitude < canopyPoint.altitude
      case None              => true
    }

    hasPrerequisite && afterCanopy && belowCanopyAltitude
  }

  def checkValidation(
    kinematics: PointKinematics,
    stabilityWindow: Vector[Double],
    config: LandingConfig,
  ): Boolean = {
    kinematics.totalSpeed < config.speedMax * 2.0 &&
      checkWindowStability(stabilityWindow, config)
  }

  def updateStabilityWindow(
    window: Vector[Double],
    value: Double,
    config: LandingConfig,
  ): Vector[Double] = {
    val updated = window :+ value
    if (updated.size > config.stabilityWindowSize) updated.drop(1) else updated
  }
}
```

**Example Scenario:**
```
Landing approach:
  Point 450: totalSpeed = 8 m/s, window stdDev = 1.2 → No trigger (speed > 3)
  Point 451: totalSpeed = 5 m/s, window stdDev = 0.8 → No trigger (speed > 3)
  Point 452: totalSpeed = 2 m/s, window stdDev = 0.6 → No trigger (stdDev > 0.5)
  Point 453: totalSpeed = 1 m/s, window stdDev = 0.4, mean = 0.3 → TRIGGER!
    - totalSpeed (1) < speedMax (3) ✓
    - stdDev (0.4) < stabilityThreshold (0.5) ✓
    - mean (0.3) < meanVerticalSpeedMax (1.0) ✓
```

### 3.6 Main Algorithm - FlightStagesDetection.scala

**Purpose:** Orchestrate all components into the streaming detection pipeline.

#### 3.6.1 ProcessingResult Type

Avoid tuples by using an explicit result type:

```scala
package com.colofabrix.scala.beesight.detection.model

final case class ProcessingResult[A](
  nextState: ProcessingState[A],
  outputs: Vector[OutputFlightRow[A]],
)
```

#### 3.6.2 Unified Detector Trait

All detection modules implement this interface:

```scala
package com.colofabrix.scala.beesight.detection

trait EventDetector[C]:
  def checkTrigger(state: EventState, kinematics: PointKinematics, config: C): Boolean
  def checkConstraints(state: EventState, events: DetectedEvents, kinematics: PointKinematics, index: Long, config: C): Boolean
  def checkValidation(state: EventState, kinematics: PointKinematics, config: C): Boolean
```

#### 3.6.3 Main Algorithm (Simplified)

```scala
package com.colofabrix.scala.beesight.detection

import breeze.stats.median
import breeze.linalg.DenseVector
import com.colofabrix.scala.beesight.config.DetectionConfig
import com.colofabrix.scala.beesight.detection.model._
import com.colofabrix.scala.beesight.model._

object FlightStagesDetection:

  def streamDetectA[F[_], A](using A: FileFormatAdapter[A]): fs2.Pipe[F, A, OutputFlightRow[A]] =
    streamDetectWithConfig(DetectionConfig.default)

  def streamDetectWithConfig[F[_], A](
    config: DetectionConfig
  )(using A: FileFormatAdapter[A]): fs2.Pipe[F, A, OutputFlightRow[A]] =
    _.map(A.toInputFlightPoint)
     .zipWithIndex
     .mapAccumulate(ProcessingState.initial[A]) { case (state, (point, idx)) =>
       val result = processPoint(state, point, idx, config)
       (result.nextState, result.outputs)
     }
     .flatMap { case (_, outputs) => fs2.Stream.emits(outputs) }

  private def processPoint[A](
    state: ProcessingState[A],
    point: InputFlightRow[A],
    index: Long,
    config: DetectionConfig,
  ): ProcessingResult[A] =
    val kinematics = Preprocessing.compute(point, state.previousKinematics, config.global)
    val updatedState = updateAllEventStates(state, point, index, kinematics, config)

    updatedState.streamPhase match
      case StreamPhase.Streaming =>
        handleStreaming(updatedState, kinematics, config)

      case StreamPhase.Validation(1, eventType) =>
        finalizeValidation(updatedState, eventType, kinematics, config)

      case StreamPhase.Validation(remaining, eventType) =>
        continueValidation(updatedState, remaining - 1, eventType, kinematics)

  private def handleStreaming[A](
    state: ProcessingState[A],
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): ProcessingResult[A] =
    tryDetectEvent(state, kinematics, config) match
      case Some(eventType) =>
        val validationWindow = getEventConfig(config, eventType).validationWindowSize
        ProcessingResult(
          state.copy(
            streamPhase = StreamPhase.Validation(validationWindow, eventType),
            pendingBuffer = state.pendingBuffer :+ state,
          ),
          Vector.empty,
        )

      case None =>
        val maxBuffer = config.freefall.backtrackWindowSize
        val newBuffer = state.pendingBuffer :+ state
        if newBuffer.size > maxBuffer then
          ProcessingResult(
            state.copy(pendingBuffer = newBuffer.tail),
            Vector(toOutputRow(newBuffer.head)),
          )
        else
          ProcessingResult(
            state.copy(pendingBuffer = newBuffer),
            Vector.empty,
          )

  private def continueValidation[A](
    state: ProcessingState[A],
    remaining: Int,
    eventType: EventType,
    kinematics: PointKinematics,
  ): ProcessingResult[A] =
    ProcessingResult(
      state.copy(
        streamPhase = StreamPhase.Validation(remaining, eventType),
        pendingBuffer = state.pendingBuffer :+ state,
      ),
      Vector.empty,
    )

  private def finalizeValidation[A](
    state: ProcessingState[A],
    eventType: EventType,
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): ProcessingResult[A] =
    val eventState = getEventState(state, eventType)
    val eventConfig = getEventConfig(config, eventType)
    val isValid = getDetector(eventType).checkValidation(eventState, kinematics, eventConfig)
    val fullBuffer = state.pendingBuffer :+ state

    if isValid then
      // SUCCESS: Find inflection using TRIGGER state's backtrack window
      val triggerState = fullBuffer.head
      val triggerEventState = getEventState(triggerState, eventType)
      val isRising = eventType == EventType.Takeoff || eventType == EventType.Freefall
      val inflectionPoint = InflectionFinder.find(triggerEventState.backtrackWindow, isRising)

      // Find state to resume from (first state AFTER inflection point)
      val resumeState = inflectionPoint match
        case Some(fp) => fullBuffer.find(_.index > fp.index).getOrElse(fullBuffer.last)
        case None => fullBuffer.last

      val newEvents = updateDetectedEvents(resumeState.detectedEvents, eventType, inflectionPoint)
      val outputs = releaseBuffer(fullBuffer, eventType, inflectionPoint)

      // Resume from inflection+1 state with updated events
      ProcessingResult(
        resumeState.copy(
          streamPhase = StreamPhase.Streaming,
          detectedEvents = newEvents,
          pendingBuffer = Vector.empty,
        ),
        outputs,
      )
    else
      // FAILURE: Resume from T+1 (second state in buffer)
      val resumeState = fullBuffer.lift(1).getOrElse(fullBuffer.last)
      val outputs = fullBuffer.map(toOutputRow)

      ProcessingResult(
        resumeState.copy(
          streamPhase = StreamPhase.Streaming,
          pendingBuffer = Vector.empty,
        ),
        outputs,
      )

  private def tryDetectEvent[A](
    state: ProcessingState[A],
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): Option[EventType] =
    val candidates = Vector(
      (EventType.Takeoff, state.detectedEvents.takeoff.isEmpty),
      (EventType.Freefall, state.detectedEvents.takeoff.isDefined && state.detectedEvents.freefall.isEmpty),
      (EventType.Canopy, state.detectedEvents.freefall.isDefined && state.detectedEvents.canopy.isEmpty),
      (EventType.Landing, (state.detectedEvents.canopy.isDefined || state.detectedEvents.takeoff.isDefined) && state.detectedEvents.landing.isEmpty),
    )

    candidates.collectFirst {
      case (eventType, true) if checkEventTrigger(state, eventType, kinematics, config) => eventType
    }

  private def checkEventTrigger[A](
    state: ProcessingState[A],
    eventType: EventType,
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): Boolean =
    val eventState = getEventState(state, eventType)
    val eventConfig = getEventConfig(config, eventType)
    val detector = getDetector(eventType)
    detector.checkTrigger(eventState, kinematics, eventConfig) &&
      detector.checkConstraints(eventState, state.detectedEvents, kinematics, state.index, eventConfig)

  private def computeCurrentPhase(events: DetectedEvents): FlightPhase =
    if events.landing.isDefined then FlightPhase.Landed
    else if events.canopy.isDefined then FlightPhase.UnderCanopy
    else if events.freefall.isDefined then FlightPhase.Freefall
    else if events.takeoff.isDefined then FlightPhase.Climbing
    else FlightPhase.BeforeTakeoff

  private def getEventState[A](state: ProcessingState[A], eventType: EventType): EventState =
    eventType match
      case EventType.Takeoff  => state.takeoffState
      case EventType.Freefall => state.freefallState
      case EventType.Canopy   => state.canopyState
      case EventType.Landing  => state.landingState

  private def getEventConfig(config: DetectionConfig, eventType: EventType): Any =
    eventType match
      case EventType.Takeoff  => config.takeoff
      case EventType.Freefall => config.freefall
      case EventType.Canopy   => config.canopy
      case EventType.Landing  => config.landing

  private def getDetector(eventType: EventType): EventDetector[?] =
    eventType match
      case EventType.Takeoff  => TakeoffDetection
      case EventType.Freefall => FreefallDetection
      case EventType.Canopy   => CanopyDetection
      case EventType.Landing  => LandingDetection

  private def updateAllEventStates[A](
    state: ProcessingState[A],
    point: InputFlightRow[A],
    index: Long,
    kinematics: PointKinematics,
    config: DetectionConfig,
  ): ProcessingState[A] =
    state.copy(
      index = index,
      currentPoint = point,
      previousKinematics = Some(kinematics),
      takeoffState = updateEventState(state.takeoffState, kinematics, config.takeoff),
      freefallState = updateEventState(state.freefallState, kinematics, config.freefall),
      canopyState = updateEventState(state.canopyState, kinematics, config.canopy),
      landingState = updateEventState(state.landingState, kinematics, config.landing),
    )

  private def updateEventState(state: EventState, kinematics: PointKinematics, config: EventConfigBase): EventState =
    val newSmoothing = (state.smoothingWindow :+ kinematics.clippedVerticalSpeed).takeRight(config.smoothingWindowSize)
    val sample = VerticalSpeedSample(kinematics.index, kinematics.clippedVerticalSpeed, kinematics.correctedAltitude)
    val newBacktrack = (state.backtrackWindow :+ sample).takeRight(config.backtrackWindowSize)
    EventState(newSmoothing, newBacktrack)

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
    inflectionPoint match
      case None => buffer.map(toOutputRow)
      case Some(fp) =>
        buffer.map { state =>
          val updatedEvents = if state.index >= fp.index then
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

  private def updateDetectedEvents(
    events: DetectedEvents,
    eventType: EventType,
    point: Option[FlightPoint],
  ): DetectedEvents =
    eventType match
      case EventType.Takeoff  => events.copy(takeoff = point.orElse(events.takeoff))
      case EventType.Freefall => events.copy(freefall = point.orElse(events.freefall))
      case EventType.Canopy   => events.copy(canopy = point.orElse(events.canopy))
      case EventType.Landing  => events.copy(landing = point.orElse(events.landing))
```

---

## 4. Algorithm Implementation

### 4.1 Processing Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         STREAMING DETECTION FLOW                            │
└─────────────────────────────────────────────────────────────────────────────┘

Input Stream (fs2)
       │
       ▼
┌──────────────────┐
│ toInputFlightRow │ ← FileFormatAdapter converts raw format
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│   zipWithIndex   │ ← Add row index for tracking
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────────┐
│                     mapAccumulate(state)                         │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                    processPoint()                          │  │
│  │  ┌─────────────────┐                                       │  │
│  │  │ Preprocessing   │ → Clip spikes, compute kinematics     │  │
│  │  └────────┬────────┘                                       │  │
│  │           │                                                │  │
│  │           ▼                                                │  │
│  │  ┌─────────────────┐                                       │  │
│  │  │ Update Windows  │ → Per-event smoothing & backtrack     │  │
│  │  └────────┬────────┘                                       │  │
│  │           │                                                │  │
│  │           ▼                                                │  │
│  │  ┌─────────────────┐     ┌─────────────────────────────┐   │  │
│  │  │ StreamPhase?    │────▶│ Streaming: Check triggers   │   │  │
│  │  └────────┬────────┘     │ Validation: Count down      │   │  │
│  │           │              └─────────────────────────────┘   │  │
│  │           │                                                │  │
│  │           ▼                                                │  │
│  │  ┌─────────────────┐                                       │  │
│  │  │ Buffer Mgmt     │ → Add to buffer, emit when ready      │  │
│  │  └────────┬────────┘                                       │  │
│  │           │                                                │  │
│  │           ▼                                                │  │
│  │  Return (newState, Vector[OutputFlightRow])                │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────┐
│ flatMap(emits)   │ ← Flatten buffered outputs
└────────┬─────────┘
         │
         ▼
Output Stream: OutputFlightRow[A]
```

### 4.2 State Transition Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         STATE MACHINE                                       │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌──────────────────────┐
                    │    STREAMING         │
                    │  (Normal operation)  │
                    └──────────┬───────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
          ▼                    ▼                    ▼
    Check Takeoff        Check Freefall       Check Canopy/Landing
          │                    │                    │
          │                    │                    │
          └────────────────────┼────────────────────┘
                               │
                    Trigger detected at T?
                               │
              ┌────────────────┴────────────────┐
              │ NO                              │ YES
              ▼                                 ▼
    ┌─────────────────┐              ┌─────────────────────────┐
    │ Emit oldest     │              │ VALIDATION(40, Event)   │
    │ from buffer     │              │ Buffer state at T       │
    └─────────────────┘              │ (freeze Streaming state)│
                                     └───────────┬─────────────┘
                                                 │
                                     ┌───────────┴───────────┐
                                     │ remaining > 0?        │
                                     ├───────────────────────┤
                                     │ YES                   │
                                     │ → Buffer state, dec   │
                                     │ → No emit             │
                                     ├───────────────────────┤
                                     │ NO (remaining == 0)   │
                                     │ → Check still valid   │
                                     └───────────┬───────────┘
                                                 │
                                     ┌───────────┴───────────┐
                                     │ Still valid?          │
                                     └───────────┬───────────┘
                              ┌──────────────────┴──────────────────┐
                              │ YES                                 │ NO
                              ▼                                     ▼
                    ┌─────────────────────┐              ┌─────────────────────┐
                    │ 1. Find inflection I│              │ Resume from T+1     │
                    │    using buffer[0]  │              │ using buffered state│
                    │    .backtrackWindow │              │ at pendingBuffer[1] │
                    │ 2. Mark event at I  │              │                     │
                    │ 3. Resume from I+1  │              │ Output all buffered │
                    │    using buffered   │              │ points unchanged    │
                    │    state at I+1     │              │                     │
                    │ 4. Output buffered  │              │                     │
                    │    with phases      │              │                     │
                    └─────────┬───────────┘              └─────────┬───────────┘
                              │                                    │
                              └──────────────────┬─────────────────┘
                                                 │
                                                 ▼
                                    ┌───────────────────────────────┐
                                    │ Back to STREAMING             │
                                    │ Resume from buffered state    │
                                    │ (as if Validation never       │
                                    │  happened to that state)      │
                                    └───────────────────────────────┘
```

**Key Points:**
- **On trigger at T:** Buffer the current `ProcessingState` which includes all windows at that moment
- **On success:** Use `pendingBuffer[0].backtrackWindow` to find inflection I, then resume from the buffered state at index > I
- **On failure:** Resume from `pendingBuffer[1]` (state at T+1)
- **Both cases:** Output all buffered points, then continue with the restored state. Subsequent detections use the windows from the restored state, not the current point.

### 4.3 Stream Emission Behavior

This section provides the **authoritative specification** for when and what the stream emits during each phase of the detection algorithm. All emission-related logic in other sections (2.2.3, 2.2.6, 3.4.4, 3.6.3) implements this specification.

#### 4.3.1 Core Principles

1. **Goal**: Every emitted point has the **correct phase** at emission time
2. **No early emissions**: Never emit before the earliest possible inflection point (respects backtrack window)
3. **Rollback on success**: After validation success, emit only up to inflection point, then **reprocess** remaining points
4. **Recursive**: Reprocessing follows the same rules - can trigger new validations

#### 4.3.2 Emission Phases Overview

The algorithm has four distinct emission phases:

| Phase | Stream State | Buffer Behavior | Emission Behavior |
|-------|--------------|-----------------|-------------------|
| **Streaming** | Normal processing | Rolling buffer up to `backtrackWindowSize` | Emit oldest when buffer exceeds max |
| **Validation** | Counting down | Buffer all incoming states | No emissions |
| **Post-Validation Success** | Releasing partial buffer | Emit up to inflection, reprocess rest | Emit only confirmed points |
| **Post-Validation Failure** | Releasing full buffer | Clear buffer | Emit all buffered points unchanged |

#### 4.3.3 Before Validation (Streaming Phase)

During normal streaming, the algorithm maintains a **rolling buffer** to enable backtracking when a detection is later confirmed:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    STREAMING PHASE - BUFFER BEHAVIOR                        │
└─────────────────────────────────────────────────────────────────────────────┘

Point arrives → Add to pendingBuffer → Check buffer size

                    ┌───────────────────────────────────────┐
                    │ pendingBuffer.size > backtrackWindowSize? │
                    └───────────────────────────────────────┘
                                       │
                      ┌────────────────┴────────────────┐
                      │ YES                             │ NO
                      ▼                                 ▼
            ┌─────────────────────┐          ┌─────────────────────┐
            │ EMIT oldest point   │          │ No emission         │
            │ (pendingBuffer.head)│          │ (continue buffering)│
            │ Drop from buffer    │          │                     │
            └─────────────────────┘          └─────────────────────┘
```

**Emission Content During Streaming:**
```scala
OutputFlightRow(
  phase = computeCurrentPhase(state.detectedEvents),  // Based on already-confirmed events
  takeoff = state.detectedEvents.takeoff,             // Only confirmed events
  freefall = state.detectedEvents.freefall,
  canopy = state.detectedEvents.canopy,
  landing = state.detectedEvents.landing,
  source = state.currentPoint.source,
)
```

**Example - Buffer During Streaming:**
```
backtrackWindowSize = 5

Points 0-4:    Buffer grows: [0] → [0,1] → ... → [0,1,2,3,4]
               Emissions: None (buffer not full yet)

Point 5:       Buffer would be [0...5] = 6 items > 5
               Emit: Point 0 with phase=BeforeTakeoff
               Buffer becomes: [1,2,3,4,5]

Point 6:       Buffer would be [1...6] = 6 items > 5
               Emit: Point 1 with phase=BeforeTakeoff
               Buffer becomes: [2,3,4,5,6]
```

#### 4.3.4 During Validation

When a detection trigger occurs (e.g., freefall threshold crossed), the algorithm enters **Validation phase** and stops emitting:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    VALIDATION PHASE - BUFFER BEHAVIOR                        │
└─────────────────────────────────────────────────────────────────────────────┘

Detection triggered at T → StreamPhase.Validation(validationWindowSize, eventType)

Points T to T+N:
  ┌──────────────────────────────────────────────────────────────────────────┐
  │ Each point:                                                              │
  │   1. Compute kinematics and update windows                               │
  │   2. Add FULL ProcessingState snapshot to pendingBuffer                  │
  │   3. Decrement remainingPoints                                           │
  │   4. Emit: NOTHING                                                       │
  └──────────────────────────────────────────────────────────────────────────┘

Why no emissions?
  - We don't know yet if the detection is valid
  - If valid, we need to emit only up to inflection point with new phase
  - Remaining points must be REPROCESSED through normal streaming
```

**Buffer Contents During Validation:**

Each buffered entry contains:
- The raw `InputFlightRow[A]` for reprocessing
- The complete `ProcessingState[A]` snapshot for state restoration

```
┌─────────────────────────────────────────────────────────────────┐
│  pendingBuffer during Validation                                 │
├─────────────────────────────────────────────────────────────────┤
│ [entry5] [entry6] ... [entryT] [entryT+1] ... [entryT+N]        │
│                           ▲        ▲              ▲              │
│                           │        │              │              │
│                        Trigger  Possible       Current           │
│                        point    Inflection     point             │
│                                 point (I)                        │
└─────────────────────────────────────────────────────────────────┘
```

**Why Full State Snapshots?**

This enables "time travel" - when validation completes, we can resume processing from any buffered point **with the exact state that existed at that point**, including all sliding windows in their correct historical positions.

#### 4.3.5 After Validation - Success (CRITICAL: Emit-Then-Reprocess)

When validation succeeds (conditions still valid after N points), the algorithm:

1. **Finds the inflection point (I)** using the trigger state's backtrack window
2. **Updates state at I** with the new detection
3. **Emits ONLY up to and including I** (with corrected phase)
4. **Reprocesses remaining points** through normal streaming logic

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    VALIDATION SUCCESS - EMIT THEN REPROCESS                  │
└─────────────────────────────────────────────────────────────────────────────┘

Validation completes at T+N, still valid

Step 1: Find inflection point I in pendingBuffer[trigger].backtrackWindow

Step 2: Update state at I with new detection
        state_I_updated = state_I.copy(
          detectedEvents = state_I.detectedEvents.copy(freefall = Some(FlightPoint(I, alt)))
        )

Step 3: Emit buffered points ONLY up to and including I
        emit state5 → phase=BeforeTakeoff (index < I)
        emit state6 → phase=BeforeTakeoff (index < I)
        emit state_I_updated → phase=Freefall ← LAST EMISSION FROM BUFFER

Step 4: Collect remaining raw points for reprocessing
        reprocessQueue = [rawPoint_{I+1}, rawPoint_{I+2}, ..., rawPoint_{T+N}]

Step 5: Resume from state_I_updated, feed reprocessQueue back into streaming
        - Windows continue from where they were at I
        - Now looking for NEXT event (e.g., Canopy after Freefall)
        - Buffer starts empty
        - Normal streaming rules apply (can trigger new validations)
```

**Key Insight: Reprocessing, Not Batch Emission**

Points after the inflection point are **reprocessed**, not batch-emitted. This means:
- They go through normal `processPoint()` logic
- They can trigger new validations (e.g., Canopy during Freefall reprocessing)
- They are emitted according to normal streaming buffer rules
- Nested reprocessing is possible (recursive)

#### 4.3.6 After Validation - Failure

When validation fails (conditions no longer valid), the algorithm:

1. **Emits the entire buffer unchanged** (no phase corrections)
2. **Resumes from state at T+1** (first point after trigger)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    VALIDATION FAILURE - EMIT ALL UNCHANGED                   │
└─────────────────────────────────────────────────────────────────────────────┘

Validation completes at T+N, NO LONGER valid

Step 1: Emit buffer WITHOUT corrections

        For each state in pendingBuffer:
          emit with ORIGINAL phase and events (as if trigger never happened)

Step 2: Resume from pendingBuffer[T+1] (state after trigger)
        Continue streaming, looking for same event type again
```

**Example - False Trigger (GPS Spike):**
```
Trigger at T=10 (spike caused threshold crossing)
By T+20, smoothed speed is back to normal → Validation FAILS

Emit all buffered points unchanged:
  Point 5: phase=BeforeTakeoff, freefall=None   (unchanged)
  Point 6: phase=BeforeTakeoff, freefall=None   (unchanged)
  ...
  Point 20: phase=BeforeTakeoff, freefall=None  (unchanged)

Resume from state at index 11, still looking for Freefall
```

#### 4.3.7 End of Stream

When the input stream ends, any remaining buffered points must be flushed:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    END OF STREAM - BUFFER FLUSH                              │
└─────────────────────────────────────────────────────────────────────────────┘

Input stream ends → Check pendingBuffer

If pendingBuffer.nonEmpty:
  - Emit all remaining points with their current phases
  - No phase corrections (validation incomplete or never triggered)

If in Validation phase when stream ends:
  - Treat as validation failure
  - Emit all buffered points unchanged
```

#### 4.3.8 Emission Timing Summary

| Scenario | When Emitted | Phase Assigned | What Happens Next |
|----------|--------------|----------------|-------------------|
| Streaming, buffer full | Immediately (oldest) | Current known phase | Continue streaming |
| Streaming, buffer not full | Deferred | - | Continue buffering |
| During validation | Never | - | Continue validation |
| Validation success | Emit up to I only | Corrected at I | Reprocess I+1 to T+N |
| Validation failure | Emit all buffered | Original (unchanged) | Resume from T+1 |
| End of stream | Immediate flush | Current known phase | Done |

#### 4.3.9 The Reprocessing Mechanism

When validation succeeds, points after the inflection point are **re-injected** into the stream for reprocessing. This requires special stream handling:

```scala
// Using fs2 Pull for re-injection capability
def go(
  inputStream: Stream[F, InputFlightRow[A]],
  state: ProcessingState[A],
): Pull[F, OutputFlightRow[A], Unit] =
  inputStream.pull.uncons1.flatMap {
    case None =>
      // End of stream - flush remaining buffer
      Pull.output(Chunk.seq(flushBuffer(state))) >> Pull.done

    case Some((point, rest)) =>
      processPoint(state, point) match {
        case ContinueStreaming(newState, outputs) =>
          Pull.output(Chunk.seq(outputs)) >> go(rest, newState)

        case ValidationSuccess(newState, outputs, reprocessQueue) =>
          // Re-inject reprocessQueue at the front of the stream
          val newStream = Stream.emits(reprocessQueue) ++ rest
          Pull.output(Chunk.seq(outputs)) >> go(newStream, newState)

        case ValidationFailure(newState, outputs) =>
          Pull.output(Chunk.seq(outputs)) >> go(rest, newState)
      }
  }
```

**Key Properties of Reprocessing:**

1. **Windows continue**: The smoothing/backtrack windows from state_I are preserved
2. **Looking for next event**: After Freefall, now looking for Canopy
3. **Buffer restarts empty**: Fresh rolling buffer begins
4. **Can nest**: If Canopy triggers during reprocessing, same validation+reprocess cycle applies
5. **Recursive correctness**: Each reprocessing level follows the same rules

### 4.4 Complete Worked Example: Freefall Detection

This section provides a detailed trace through the algorithm to demonstrate the emit-then-reprocess behavior.

#### 4.4.1 Setup

```
Configuration:
  backtrackWindowSize = 5
  validationWindowSize = 10
```

#### 4.4.2 Input Data

```
Index | Vertical Speed | What's Happening
------|----------------|------------------
  0   |     0.5        | On ground
  1   |     0.8        | On ground
  2   |     2.0        | Plane climbing
  3   |     3.0        | Plane climbing
  4   |     4.0        | Plane climbing
  5   |     5.0        | Plane at altitude
  6   |     5.5        | Plane at altitude
  7   |     8.0        | ← EXIT (inflection point - speed starts rising)
  8   |    15.0        | Freefall accelerating
  9   |    22.0        | Freefall accelerating
 10   |    28.0        | ← TRIGGER (crosses 25 m/s threshold)
 11   |    35.0        | Freefall (validation continues)
 12   |    42.0        | Freefall (validation continues)
 ...  |    ...         | ...
 20   |    54.0        | ← VALIDATION COMPLETE at T+10
 21   |    53.5        | Freefall (after validation)
```

#### 4.4.3 Phase 1: Streaming (Index 0-4)

Buffer grows, NO emissions yet (buffer size ≤ backtrackWindowSize):

```
Index 0: buffer = [state0]                      → emit: nothing
Index 1: buffer = [state0, state1]              → emit: nothing
Index 2: buffer = [state0, state1, state2]      → emit: nothing
Index 3: buffer = [state0,...,state3]           → emit: nothing
Index 4: buffer = [state0,...,state4]           → emit: nothing (size=5=max)
```

#### 4.4.4 Phase 2: Streaming with Emissions (Index 5-9)

```
Index 5: buffer would be [0,1,2,3,4,5] = 6 items > 5
         → EMIT: point 0 with phase=BeforeTakeoff
         → buffer = [state1,...,state5]

Index 6: → EMIT: point 1 with phase=BeforeTakeoff
         → buffer = [state2,...,state6]

Index 7: → EMIT: point 2 with phase=BeforeTakeoff
         → buffer = [state3,...,state7]

Index 8: → EMIT: point 3 with phase=BeforeTakeoff
         → buffer = [state4,...,state8]

Index 9: → EMIT: point 4 with phase=BeforeTakeoff
         → buffer = [state5,...,state9]
```

#### 4.4.5 Phase 3: TRIGGER at Index 10

Speed = 28 m/s > threshold (25 m/s) → **TRIGGER FREEFALL**

```
Index 10:
  - Trigger detected!
  - Enter Validation(remainingPoints=10, Freefall)
  - buffer = [state5, state6, state7, state8, state9, state10]
  - EMIT: NOTHING (validation starts)
```

**Backtrack window at trigger (indices 6-10):**
```
  Index 6: speed = 5.5  (plane)
  Index 7: speed = 8.0  ← INFLECTION (first rise after falling/stable)
  Index 8: speed = 15.0
  Index 9: speed = 22.0
  Index 10: speed = 28.0
```

#### 4.4.6 Phase 4: Validation (Index 11-19)

```
Index 11: remaining=9, buffer=[state5,...,state11], emit: nothing
Index 12: remaining=8, buffer=[state5,...,state12], emit: nothing
...
Index 19: remaining=1, buffer=[state5,...,state19], emit: nothing
```

#### 4.4.7 Phase 5: Validation Complete at Index 20

```
Index 20: remaining=0 → CHECK VALIDATION

Current speed = 54 m/s > 25*0.8 = 20 m/s → VALID!

Step 1: Find inflection using backtrack window at TRIGGER (index 10)
        → Inflection found at Index 7 (where speed started rising)
        → EventPoint = FlightPoint(7, altitude_at_7)

Step 2: Update state at index 7 with freefall detection
        state7_updated = state7.copy(
          detectedEvents = DetectedEvents(freefall = Some(FlightPoint(7, alt)))
        )

Step 3: Emit buffered points ONLY up to and including inflection point:
        → EMIT: state5 with phase=BeforeTakeoff, freefall=None
        → EMIT: state6 with phase=BeforeTakeoff, freefall=None
        → EMIT: state7_updated with phase=Freefall, freefall=Some(7, alt) ← EVENT POINT

Step 4: Collect remaining raw points for reprocessing:
        reprocessQueue = [rawPoint8, rawPoint9, ..., rawPoint20]

Step 5: Resume from state7_updated:
        - detectedEvents now includes freefall
        - pendingBuffer = [] (empty)
        - streamPhase = Streaming
        - Now looking for Canopy (not Freefall)
```

#### 4.4.8 Phase 6: Reprocessing Points 8-20

Points 8-20 are re-injected into the stream and processed through normal streaming logic:

```
Reprocess Index 8:
  - Start with state7_updated (has freefall=Some(7))
  - Process point 8 normally
  - buffer = [state8]
  - emit: nothing (buffer not full)

Reprocess Index 9:
  - buffer = [state8, state9]
  - emit: nothing

...

Reprocess Index 13:
  - buffer would be [8,9,10,11,12,13] = 6 items > 5
  - EMIT: point 8 with phase=Freefall, freefall=Some(7, alt)
  - buffer = [state9,...,state13]

Reprocess Index 14:
  - EMIT: point 9 with phase=Freefall
  - buffer = [state10,...,state14]

... continues through index 20 ...
```

#### 4.4.9 Phase 7: Continue with Original Stream (Index 21+)

After reprocessing completes, the original stream continues from index 21:

```
Index 21 (from original stream):
  - Process normally
  - buffer = [state17,...,state21] (continuing from reprocessing)
  - EMIT: point 16 with phase=Freefall
  - ... continues normally, looking for Canopy
```

#### 4.4.10 Complete Output Sequence

```
Total output (in order emitted):

BATCH 1 - Streaming (at indices 5-9):
  point 0: phase=BeforeTakeoff, freefall=None
  point 1: phase=BeforeTakeoff, freefall=None
  point 2: phase=BeforeTakeoff, freefall=None
  point 3: phase=BeforeTakeoff, freefall=None
  point 4: phase=BeforeTakeoff, freefall=None

BATCH 2 - Validation Success (at index 20):
  point 5: phase=BeforeTakeoff, freefall=None
  point 6: phase=BeforeTakeoff, freefall=None
  point 7: phase=Freefall, freefall=Some(7, alt)  ← EVENT POINT

BATCH 3 - Reprocessing (during reprocess of 8-20):
  point 8:  phase=Freefall, freefall=Some(7, alt)  (emitted when buffer fills)
  point 9:  phase=Freefall, freefall=Some(7, alt)
  point 10: phase=Freefall, freefall=Some(7, alt)
  point 11: phase=Freefall, freefall=Some(7, alt)
  point 12: phase=Freefall, freefall=Some(7, alt)
  point 13: phase=Freefall, freefall=Some(7, alt)
  point 14: phase=Freefall, freefall=Some(7, alt)
  point 15: phase=Freefall, freefall=Some(7, alt)

BATCH 4 - Continued streaming (index 21+):
  point 16: phase=Freefall, freefall=Some(7, alt)
  point 17: phase=Freefall, freefall=Some(7, alt)
  ...
```

#### 4.4.11 Key Observations

1. **Every emitted point has the correct phase**: Point 7 is correctly tagged as Freefall even though the trigger was at index 10.

2. **No points emitted prematurely**: Points 5-7 were held in the buffer until validation confirmed the event.

3. **Reprocessing uses normal rules**: Points 8-20 went through regular streaming logic, not batch-emitted.

4. **Buffer restarts after validation**: During reprocessing, the buffer starts empty and refills according to normal rules.

5. **State continuity**: The windows (smoothing, backtrack) continue from state7, ensuring subsequent detections (like Canopy) use correct historical data.

---

## 5. Handling Wrong Data

### 5.1 Problem Statement

Some GPS data files contain anomalies that make detection difficult or impossible:

| Problem Type | Description | Example |
|--------------|-------------|---------|
| **Excessive spikes** | Continuous GPS errors throughout file | Every 3rd reading is a spike |
| **Missing phases** | File starts mid-flight or ends early | Recording started after exit |
| **Corrupted segments** | Long stretches of invalid data | 30 seconds of zeros |
| **Unusual flight profiles** | Valid but atypical patterns | Hop-n-pop (canopy 1s after exit) |
| **Non-jump data** | Plane descent without jump | Ferry flight, weather abort |

### 5.2 Design Goals

Two optimization objectives for handling wrong data:

1. **Minimize difference from real points** - When detection succeeds, the detected point should be as close as possible to the true event
2. **Minimize number of failures** - Maximize the percentage of files that produce valid detections

### 5.3 Failure Modes and Mitigations

#### 5.3.1 Spike-Related Failures

**Failure Mode:** GPS spikes trigger false detections

**Mitigations Built Into Algorithm:**

| Mitigation | How It Helps | Configuration |
|------------|--------------|---------------|
| Acceleration clipping | Limits velocity change to physical maximum | `accelerationClip = 20.0 m/s²` |
| Median smoothing | Single spikes don't affect median | `smoothingWindowSize = 5` |
| Validation window | Requires condition to hold for 40 points (~8 seconds) | `validationWindowSize = 40` |
| Backtracking | Finds true transition, not spike-triggered point | `backtrackWindowSize = 10` |

**Example: Spike Rejection**
```
Point 100: speed = 5 m/s   (plane)
Point 101: speed = 150 m/s (spike!)  → Clipped to 9 m/s
Point 102: speed = 6 m/s   (plane)

Without clipping: Would trigger freefall at 101 (FALSE POSITIVE)
With clipping: Smoothed speed stays at ~6 m/s, no trigger
```

#### 5.3.2 Missing Takeoff

**Failure Mode:** Recording starts after aircraft is airborne

**Mitigation:** Detection proceeds without takeoff

```scala
// Freefall constraint allows missing takeoff:
val afterTakeoff = events.takeoff match {
  case Some(takeoffPoint) => currentIndex > takeoffPoint.index
  case None               => true  // ← Proceed anyway if no takeoff
}
```

**Impact on Output:**
```
takeoff = None
freefall = Some(FlightPoint(142, 4200.0))  // Detected successfully
canopy = Some(FlightPoint(284, 1200.0))
landing = Some(FlightPoint(453, 120.0))
```

#### 5.3.3 Missing Freefall (Plane Landing)

**Failure Mode:** Aircraft lands without dropping jumpers

**Behavior:** Algorithm detects takeoff, no freefall/canopy, then landing

```
Expected Output:
  takeoff = Some(FlightPoint(47, 120.0))
  freefall = None   // No jump occurred
  canopy = None
  landing = Some(FlightPoint(1200, 115.0))  // Plane landed
```

**Why This Works:**
- Landing constraint checks for `canopy.isDefined || takeoff.isDefined`
- Plane landing has low total speed and stability, triggers landing detection
- File is NOT a failure - it's a correct detection of a non-jump flight

#### 5.3.4 Very Early Canopy (Hop-n-Pop)

**Failure Mode:** Canopy opens 1-3 seconds after exit (hop-n-pop jumps)

**Challenge:** Freefall validation window (40 points = 8 seconds) may not complete before canopy conditions are met

**Why Validation Still Works:**

Consider a hop-n-pop scenario:
- Real freefall at point 100, canopy at point 107
- Algorithm tentatively detects freefall at point 105
- Validation continues until point 145 (40 points later)
- By point 145, the skydiver is under canopy BUT the validation criteria are still met because:
  - The smoothed speed during the freefall portion was high enough
  - The validation checks that conditions WERE valid, not that they ARE valid now
- Backtracking then finds the true exit around point 100-102
- After freefall is confirmed, canopy detection begins and finds point ~107

**Key Insight:** Do NOT relax validation for freefall. The standard validation works correctly because it validates the historical data in the buffer, not the current conditions.

### 5.4 Graceful Degradation Strategy

When full detection is impossible, provide partial results:

| Scenario | Available Events | Phase Assignment |
|----------|------------------|------------------|
| Normal jump | All 4 events | Full phase progression |
| Recording started late | No takeoff, 3 others | BeforeTakeoff → Freefall (skip Climbing) |
| Hop-n-pop | All 4, short freefall | Full phases, freefall is short |
| Plane landing | Takeoff + landing | BeforeTakeoff → Climbing → Landed |
| Only descent data | Canopy + landing | BeforeTakeoff → UnderCanopy → Landed |

---

## 6. Testing Strategy

### 6.1 Configuration Completeness

**Important Clarification:** All numeric values shown in code examples (like `40` for validation window, `20.0` for acceleration clipping) are **configuration parameters**, not hardcoded values. The implementation uses `config.xxx` for all thresholds.

**Complete Configuration Parameter Map:**

| Example Value | Config Parameter | Location |
|---------------|------------------|----------|
| `20.0` m/s² | `config.global.accelerationClip` | `GlobalConfig` |
| `25.0` m/s | `config.takeoff.speedThreshold` | `TakeoffConfig` |
| `-1.0` m/s | `config.takeoff.climbRate` | `TakeoffConfig` |
| `600.0` m | `config.takeoff.maxAltitude` | `TakeoffConfig` |
| `5` | `config.takeoff.smoothingWindowSize` | `TakeoffConfig` |
| `10` | `config.takeoff.backtrackWindowSize` | `TakeoffConfig` |
| `40` | `config.takeoff.validationWindowSize` | `TakeoffConfig` |
| `25.0` m/s | `config.freefall.verticalSpeedThreshold` | `FreefallConfig` |
| `3.0` m/s² | `config.freefall.accelerationThreshold` | `FreefallConfig` |
| `10.0` m/s | `config.freefall.accelerationMinVelocity` | `FreefallConfig` |
| `600.0` m | `config.freefall.minAltitudeAbove` | `FreefallConfig` |
| `600.0` m | `config.freefall.minAltitudeAbsolute` | `FreefallConfig` |
| `5` | `config.freefall.smoothingWindowSize` | `FreefallConfig` |
| `10` | `config.freefall.backtrackWindowSize` | `FreefallConfig` |
| `40` | `config.freefall.validationWindowSize` | `FreefallConfig` |
| `12.0` m/s | `config.canopy.verticalSpeedMax` | `CanopyConfig` |
| `5` | `config.canopy.smoothingWindowSize` | `CanopyConfig` |
| `10` | `config.canopy.backtrackWindowSize` | `CanopyConfig` |
| `40` | `config.canopy.validationWindowSize` | `CanopyConfig` |
| `3.0` m/s | `config.landing.speedMax` | `LandingConfig` |
| `0.5` m/s | `config.landing.stabilityThreshold` | `LandingConfig` |
| `1.0` m/s | `config.landing.meanVerticalSpeedMax` | `LandingConfig` |
| `10` | `config.landing.stabilityWindowSize` | `LandingConfig` |
| `5` | `config.landing.smoothingWindowSize` | `LandingConfig` |
| `10` | `config.landing.backtrackWindowSize` | `LandingConfig` |
| `40` | `config.landing.validationWindowSize` | `LandingConfig` |

**Code Pattern:**
```scala
// ❌ WRONG - hardcoded
if (remaining <= 40) { ... }

// ✓ CORRECT - configurable
if (remaining <= config.freefall.validationWindowSize) { ... }
```

### 6.2 Test Categories

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           TEST PYRAMID                                        │
└──────────────────────────────────────────────────────────────────────────────┘

                        ┌─────────────────┐
                        │   Regression    │  ← Real jump files (140+)
                        │     Tests       │     Validate against expected points
                        └────────┬────────┘
                                 │
                    ┌────────────┴────────────┐
                    │     Integration Tests   │  ← Full pipeline tests
                    │   (FlightStagesDetection)│    End-to-end scenarios
                    └────────────┬────────────┘
                                 │
        ┌────────────────────────┴────────────────────────┐
        │                    Unit Tests                    │
        │  Preprocessing | Smoothing | Inflection | Buffer │
        │  TakeoffDet | FreefallDet | CanopyDet | LandingDet │
        └──────────────────────────────────────────────────┘
```

### 6.3 Unit Tests

#### 6.3.1 PreprocessingSpec

```scala
package com.colofabrix.scala.beesight.detection

import munit.FunSuite
import com.colofabrix.scala.beesight.config.GlobalConfig

class PreprocessingSpec extends FunSuite {

  val config = GlobalConfig(accelerationClip = 20.0)

  test("clipSpeed should not modify speed within acceleration limits") {
    val result = Preprocessing.clipSpeed(
      current = 35.0,
      previous = 32.0,
      deltaTime = 0.2,
      maxAcceleration = 20.0,
    )
    assertEquals(result, 35.0)  // 3.0 m/s change < 4.0 m/s max
  }

  test("clipSpeed should clip speed exceeding acceleration limits") {
    val result = Preprocessing.clipSpeed(
      current = 150.0,  // GPS spike
      previous = 35.0,
      deltaTime = 0.2,
      maxAcceleration = 20.0,
    )
    assertEquals(result, 39.0)  // 35.0 + 4.0 (max change)
  }

  test("clipSpeed should handle negative acceleration") {
    val result = Preprocessing.clipSpeed(
      current = 10.0,
      previous = 50.0,  // Rapid deceleration
      deltaTime = 0.2,
      maxAcceleration = 20.0,
    )
    assertEquals(result, 46.0)  // 50.0 - 4.0 (max change)
  }

  test("computeKinematics should calculate horizontal speed correctly") {
    // Given north=3, east=4, expected horizontal = 5 (Pythagorean)
    val point = InputFlightRow(
      time = java.time.Instant.now(),
      latitude = 0.0,
      longitude = 0.0,
      altitude = 1000.0,
      verticalSpeed = 0.0,
      northSpeed = 3.0,
      eastSpeed = 4.0,
      source = (),
    )
    val kinematics = Preprocessing.computeKinematics(point, None, None, config)
    assertEqualsDouble(kinematics.horizontalSpeed, 5.0, 0.001)
  }

  test("computeKinematics should correct altitude when speed is clipped") {
    val prevKinematics = PointKinematics(
      rawVerticalSpeed = 35.0,
      rawNorthSpeed = 0.0,
      rawEastSpeed = 0.0,
      clippedVerticalSpeed = 35.0,
      clippedNorthSpeed = 0.0,
      clippedEastSpeed = 0.0,
      correctedAltitude = 2000.0,
      horizontalSpeed = 0.0,
      totalSpeed = 35.0,
      deltaTime = 0.2,
    )

    val currentPoint = InputFlightRow(
      time = java.time.Instant.now().plusMillis(200),
      latitude = 0.0,
      longitude = 0.0,
      altitude = 1850.0,  // Suspicious jump (150 m/s implied!)
      verticalSpeed = 150.0,  // GPS spike
      northSpeed = 0.0,
      eastSpeed = 0.0,
      source = (),
    )

    val result = Preprocessing.computeKinematics(
      currentPoint, Some(prevPoint), Some(prevKinematics), config
    )

    // Clipped speed = 35 + 4 = 39
    assertEqualsDouble(result.clippedVerticalSpeed, 39.0, 0.001)
    // Corrected altitude = 2000 - 39 * 0.2 = 1992.2
    assertEqualsDouble(result.correctedAltitude, 1992.2, 0.1)
  }
}
```

#### 6.3.2 SmoothingSpec

```scala
class SmoothingSpec extends FunSuite {

  test("median should return middle value for odd-sized window") {
    val window = Vector(10.0, 150.0, 20.0, 30.0, 15.0)  // Contains spike
    val result = Smoothing.median(window)
    assertEquals(result, 20.0)  // Spike ignored
  }

  test("median should return average of middle two for even-sized window") {
    val window = Vector(10.0, 20.0, 30.0, 40.0)
    val result = Smoothing.median(window)
    assertEquals(result, 25.0)  // (20 + 30) / 2
  }

  test("updateWindow should maintain max size") {
    val window = Vector(1.0, 2.0, 3.0, 4.0, 5.0)
    val result = Smoothing.updateWindow(window, 6.0, maxSize = 5)
    assertEquals(result, Vector(2.0, 3.0, 4.0, 5.0, 6.0))  // Dropped 1.0
  }

  test("computeAcceleration should calculate rate of change") {
    val result = Smoothing.computeAcceleration(
      currentSmoothed = 30.0,
      previousSmoothed = 10.0,
      deltaTime = 0.2,
    )
    assertEquals(result, 100.0)  // (30 - 10) / 0.2 = 100 m/s²
  }
}
```

#### 6.3.3 InflectionFinderSpec

```scala
class InflectionFinderSpec extends FunSuite {

  test("findInflectionPoint should find rising inflection for freefall") {
    val window = Vector(
      VerticalSpeedSample(140, 5.0, 4500.0),   // Plane
      VerticalSpeedSample(141, 4.8, 4498.0),  // Still plane
      VerticalSpeedSample(142, 8.2, 4490.0),  // Exit point!
      VerticalSpeedSample(143, 15.3, 4470.0),
      VerticalSpeedSample(144, 25.0, 4440.0),
    )
    val result = InflectionFinder.findInflectionPoint(window, isRising = true)
    assertEquals(result, Some(FlightPoint(142, 4490.0)))
  }

  test("findInflectionPoint should find falling inflection for canopy") {
    val window = Vector(
      VerticalSpeedSample(280, 52.0, 1500.0),
      VerticalSpeedSample(281, 48.0, 1480.0),
      VerticalSpeedSample(282, 35.0, 1460.0),  // Deceleration starts
      VerticalSpeedSample(283, 20.0, 1445.0),
      VerticalSpeedSample(284, 10.0, 1435.0),
    )
    val result = InflectionFinder.findInflectionPoint(window, isRising = false)
    assertEquals(result, Some(FlightPoint(281, 1480.0)))  // First speed drop
  }

  test("findInflectionPoint should return first point if no inflection found") {
    val window = Vector(
      VerticalSpeedSample(100, 10.0, 4500.0),
      VerticalSpeedSample(101, 15.0, 4490.0),  // All rising
      VerticalSpeedSample(102, 20.0, 4480.0),
    )
    val result = InflectionFinder.findInflectionPoint(window, isRising = true)
    assertEquals(result, Some(FlightPoint(100, 4500.0)))  // Fallback to first
  }
}
```

#### 6.3.4 Detection Module Tests

```scala
class TakeoffDetectionSpec extends FunSuite {

  val config = TakeoffConfig(
    speedThreshold = 25.0,
    climbRate = -1.0,
    maxAltitude = 600.0,
    smoothingWindowSize = 5,
    backtrackWindowSize = 10,
    validationWindowSize = 40,
  )

  test("checkTrigger should return true when speed and climb rate conditions met") {
    val kinematics = PointKinematics(
      horizontalSpeed = 28.0,  // > 25
      // ... other fields
    )
    val result = TakeoffDetection.checkTrigger(kinematics, smoothedVerticalSpeed = -1.5, config)
    assert(result)  // -1.5 < -1.0 ✓
  }

  test("checkTrigger should return false when only speed condition met") {
    val kinematics = PointKinematics(horizontalSpeed = 28.0, ...)
    val result = TakeoffDetection.checkTrigger(kinematics, smoothedVerticalSpeed = 0.5, config)
    assert(!result)  // 0.5 is NOT < -1.0
  }

  test("checkConstraints should reject if takeoff already detected") {
    val events = DetectedEvents(
      takeoff = Some(FlightPoint(50, 120.0)),
      freefall = None, canopy = None, landing = None,
    )
    val result = TakeoffDetection.checkConstraints(kinematics, events, config)
    assert(!result)  // Already have takeoff
  }

  test("checkConstraints should reject if above max altitude") {
    val kinematics = PointKinematics(correctedAltitude = 700.0, ...)
    val result = TakeoffDetection.checkConstraints(kinematics, DetectedEvents.empty, config)
    assert(!result)  // 700 > 600
  }
}
```

### 6.4 Integration Tests

```scala
class FlightStagesDetectionIntegrationSpec extends FunSuite {

  test("should detect all four events for normal skydive") {
    val data = loadTestFile("src/test/resources/flysight/Jump 800.csv")

    val results = fs2.Stream.emits(data)
      .through(FlightStagesDetection.streamDetectA[fs2.Pure, FlySightRow])
      .compile
      .toVector

    val finalRow = results.last
    assert(finalRow.takeoff.isDefined)
    assert(finalRow.freefall.isDefined)
    assert(finalRow.canopy.isDefined)
    assert(finalRow.landing.isDefined)

    // Verify phase progression
    val phases = results.map(_.phase).distinct
    assertEquals(phases, Vector(
      FlightPhase.BeforeTakeoff,
      FlightPhase.Climbing,
      FlightPhase.Freefall,
      FlightPhase.UnderCanopy,
      FlightPhase.Landed,
    ))
  }

  test("should handle file with missing takeoff") {
    val data = loadTestFile("src/test/resources/flysight/Jump 924.csv")  // Started mid-flight

    val results = fs2.Stream.emits(data)
      .through(FlightStagesDetection.streamDetectA[fs2.Pure, FlySightRow])
      .compile
      .toVector

    val finalRow = results.last
    assert(finalRow.takeoff.isEmpty)  // No takeoff detected
    assert(finalRow.freefall.isDefined)  // But freefall worked
    assert(finalRow.canopy.isDefined)
    assert(finalRow.landing.isDefined)
  }

  test("should handle plane landing (no jump)") {
    val data = loadTestFile("src/test/resources/flysight/coming_down_with_plane.CSV")

    val results = fs2.Stream.emits(data)
      .through(FlightStagesDetection.streamDetectA[fs2.Pure, FlySightRow])
      .compile
      .toVector

    val finalRow = results.last
    assert(finalRow.takeoff.isDefined)
    assert(finalRow.freefall.isEmpty)  // No jump
    assert(finalRow.canopy.isEmpty)
    assert(finalRow.landing.isDefined)  // Plane landed
  }

  test("validation window should reject false positive") {
    // Create synthetic data with spike that triggers but doesn't validate
    val data = Vector(
      // ... stable plane flight
      createRow(100, verticalSpeed = 5.0),
      createRow(101, verticalSpeed = 150.0),  // GPS spike!
      createRow(102, verticalSpeed = 5.0),    // Back to normal
      // ... continue stable
    )

    val results = fs2.Stream.emits(data)
      .through(FlightStagesDetection.streamDetectWithConfig(testConfig))
      .compile
      .toVector

    val freefallPoints = results.flatMap(_.freefall)
    assert(freefallPoints.isEmpty)  // Spike was rejected
  }
}
```

### 6.5 Regression Tests with Success Rate Tracking

**Purpose:** Validate detection accuracy against known good results from the existing 140+ jump files. The test data is split into two categories:

1. **Reliable data (untagged)** - Files without excessive anomalies, must pass >95%
2. **Unreliable data (tagged)** - Files with known issues, manually inspected expected values

```scala
package com.colofabrix.scala.beesight.detection

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class FlightStagesDetectionSpec extends AnyWordSpec with Matchers {

  val summaryData = loadSummary(resultsFile)
  val untaggedLines = summaryData.filter(_.tag.isEmpty)
  val taggedLines = summaryData.filter(_.tag.nonEmpty)

  var untaggedSuccesses = 0
  var untaggedFailures = List.empty[String]

  "FlightStagesDetection" should {

    untaggedLines.foreach { line =>
      s"detect all flight stages correctly for ${line.file}" in {
        val data = loadFlySightFile(line.file)
        val results = runDetection(data)
        val finalRow = results.last

        val success = finalRow.takeoff.isDefined &&
                      finalRow.freefall.isDefined &&
                      finalRow.canopy.isDefined &&
                      finalRow.landing.isDefined

        if (success) {
          untaggedSuccesses += 1
        } else {
          val output = s"""
            |FAILED: ${line.file}
            |  takeoff:  ${finalRow.takeoff.map(p => s"index=${p.index}, alt=${p.altitude}").getOrElse("MISSING")}
            |  freefall: ${finalRow.freefall.map(p => s"index=${p.index}, alt=${p.altitude}").getOrElse("MISSING")}
            |  canopy:   ${finalRow.canopy.map(p => s"index=${p.index}, alt=${p.altitude}").getOrElse("MISSING")}
            |  landing:  ${finalRow.landing.map(p => s"index=${p.index}, alt=${p.altitude}").getOrElse("MISSING")}
          """.stripMargin
          untaggedFailures = output :: untaggedFailures
          fail(output)
        }
      }
    }

    "achieve >95% success rate for untagged files" in {
      val total = untaggedLines.size
      val rate = untaggedSuccesses.toDouble / total * 100

      println(s"\n=== UNTAGGED FILES SUMMARY ===")
      println(s"Total: $total")
      println(s"Passed: $untaggedSuccesses")
      println(s"Failed: ${total - untaggedSuccesses}")
      println(s"Success Rate: ${f"$rate%.1f"}%")

      if (untaggedFailures.nonEmpty) {
        println("\n=== FAILED FILES FOR MANUAL REVIEW ===")
        untaggedFailures.foreach(println)
      }

      rate should be >= 95.0
    }
  }

  var taggedSuccesses = 0
  var taggedFailures = List.empty[String]

  "FlightStagesDetection with tagged (unreliable) data" should {

    taggedLines.foreach { line =>
      s"detect stages with manual reference for ${line.file} (tag: ${line.tag})" in {
        val data = loadFlySightFile(line.file)
        val results = runDetection(data)
        val finalRow = results.last

        val freefallOk = checkWithinTolerance(finalRow.freefall, line.expectedFreefall, tolerance = 5)
        val canopyOk = checkWithinTolerance(finalRow.canopy, line.expectedCanopy, tolerance = 5)
        val landingOk = checkWithinTolerance(finalRow.landing, line.expectedLanding, tolerance = 10)

        val success = freefallOk && canopyOk && landingOk

        if (success) {
          taggedSuccesses += 1
        } else {
          val output = s"""
            |FAILED: ${line.file} (tag: ${line.tag})
            |  freefall: expected=${line.expectedFreefall.getOrElse("N/A")}, got=${finalRow.freefall.map(_.index).getOrElse("MISSING")}, ok=$freefallOk
            |  canopy:   expected=${line.expectedCanopy.getOrElse("N/A")}, got=${finalRow.canopy.map(_.index).getOrElse("MISSING")}, ok=$canopyOk
            |  landing:  expected=${line.expectedLanding.getOrElse("N/A")}, got=${finalRow.landing.map(_.index).getOrElse("MISSING")}, ok=$landingOk
          """.stripMargin
          taggedFailures = output :: taggedFailures
        }
      }
    }

    "report success rate for tagged files" in {
      val total = taggedLines.size
      val rate = if (total > 0) taggedSuccesses.toDouble / total * 100 else 0.0

      println(s"\n=== TAGGED FILES SUMMARY ===")
      println(s"Total: $total")
      println(s"Passed: $taggedSuccesses")
      println(s"Failed: ${total - taggedSuccesses}")
      println(s"Success Rate: ${f"$rate%.1f"}%")

      if (taggedFailures.nonEmpty) {
        println("\n=== TAGGED FAILURES FOR MANUAL REVIEW ===")
        taggedFailures.foreach(println)
      }
    }
  }

  private def checkWithinTolerance(
    detected: Option[FlightPoint],
    expected: Option[Long],
    tolerance: Int,
  ): Boolean =
    (detected, expected) match {
      case (Some(d), Some(e)) => math.abs(d.index - e) <= tolerance
      case (None, None)       => true
      case _                  => false
    }
}
```

---

## 7. Rollout Plan

### 7.1 Implementation Phases (5 Steps)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     5-STEP IMPLEMENTATION PLAN                               │
└─────────────────────────────────────────────────────────────────────────────┘

Step 1: Foundation (Days 1-2)
├── Create hierarchical DetectionConfig with defaults
├── Create simplified model types (EventState, StreamPhase, DetectedEvents)
├── Implement Preprocessing.scala using Breeze for statistics
├── Implement InflectionFinder.scala
└── Unit tests for these components

Step 2: Detection Modules (Days 3-4)
├── Implement all 4 detection modules with unified signatures
├── Each receives (EventState, Config, extras) pattern
├── Centralize phase transition logic
└── Unit tests for detection modules

Step 3: Main Algorithm (Days 5-6)
├── Implement simplified FlightStagesDetection.scala
├── Single processPoint with pattern matching
├── Simplified buffer management (stores full ProcessingState)
├── Use ProcessingResult instead of tuples
└── Integration tests

Step 4: Integration Testing (Days 7-8)
├── Run against untagged files (target >95%)
├── Run against tagged files (track failure rate)
├── Tune thresholds as needed
└── Fix edge cases

Step 5: Finalization (Days 9-10)
├── Code cleanup and documentation
├── Final test pass
└── Update REQUIREMENTS.md if any deviations
```

### 7.2 Success Criteria

1. **Untagged Files**: ≥95% success rate
2. **Tagged Files**: Track and report success rate (minimize failures)
3. **Point Accuracy**: Detected points within ±5 rows of expected for freefall/canopy
4. **No Regressions**: All currently working files continue to work

---

## 8. Action Items Checklist

### 8.1 Configuration - NO HARDCODED VALUES

**CRITICAL REMINDER:** All numeric values MUST be configuration parameters. The examples in this document show default values in comments, but actual implementation uses `config.xxx`:

```scala
// ❌ WRONG - Never do this
if (remaining <= 40) { ... }
val maxChange = 20.0 * deltaTime

// ✓ CORRECT - Always use config
if (remaining <= config.freefall.validationWindowSize) { ... }
val maxChange = config.global.accelerationClip * deltaTime
```

### 8.2 File Modifications

- [ ] **config/DetectionConfig.scala**
  - [ ] Create `GlobalConfig` case class with `accelerationClip: Double`
  - [ ] Create `TakeoffConfig` case class with 6 parameters
  - [ ] Create `FreefallConfig` case class with 8 parameters
  - [ ] Create `CanopyConfig` case class with 4 parameters
  - [ ] Create `LandingConfig` case class with 7 parameters
  - [ ] Create `DetectionConfig` case class combining all configs
  - [ ] Create `DetectionConfig.default` with all existing values
  - [ ] Remove old flat structure

- [ ] **model/FlightPhase.scala**
  - [ ] Rename `Takeoff` → `Climbing`
  - [ ] Rename `Canopy` → `UnderCanopy`
  - [ ] Rename `Landing` → `Landed`

- [ ] **model/OutputFlightRow.scala**
  - [ ] Remove `lastPoint: Long` field
  - [ ] Update all usages

- [ ] **detection/FlightStagesDetection.scala**
  - [ ] Implement `streamDetectWithConfig` method (new, takes config)
  - [ ] Update `streamDetectA` to call `streamDetectWithConfig(DetectionConfig.default)`
  - [ ] Implement `initialState` function
  - [ ] Implement `processPoint` function
  - [ ] Implement `updateAllEventStates` function
  - [ ] Implement `updateEventState` function
  - [ ] Implement `handleStreaming` function
  - [ ] Implement `handleValidation` function
  - [ ] Implement `detectNextEvent` function
  - [ ] Implement `computeCurrentPhase` function
  - [ ] Implement `getValidationWindowSize` function
  - [ ] Implement `getBacktrackWindow` function
  - [ ] Implement `checkValidationCondition` function
  - [ ] Implement `updateDetectedEvents` function
  - [ ] Add end-of-stream buffer flush logic

### 8.3 New Files - Detection Model

- [ ] **detection/model/EventState.scala**
  - [ ] Create `VerticalSpeedSample` case class
  - [ ] Create `EventState` case class
  - [ ] Create `EventState.empty` companion

- [ ] **detection/model/StreamPhase.scala**
  - [ ] Create `EventType` enum (Takeoff, Freefall, Canopy, Landing)
  - [ ] Create `StreamPhase` enum (Streaming, Validation)

- [ ] **detection/model/DetectedEvents.scala**
  - [ ] Create `DetectedEvents` case class
  - [ ] Create `DetectedEvents.empty` companion

- [ ] **detection/model/PointKinematics.scala**
  - [ ] Create `PointKinematics` case class with 10 fields

- [ ] **detection/model/ProcessingState.scala**
  - [ ] Create `ProcessingState[A]` case class with 11 fields

- [ ] **detection/model/BufferedState.scala**
  - [ ] Create `BufferedState[A]` case class with 5 fields

### 8.4 New Files - Core Components

- [ ] **detection/Preprocessing.scala**
  - [ ] Implement `computeKinematics` function
  - [ ] Implement private `clipSpeed` function
  - [ ] Ensure all thresholds come from `GlobalConfig`

- [ ] **detection/Smoothing.scala**
  - [ ] Implement `updateWindow` function
  - [ ] Implement `median` function
  - [ ] Implement `computeAcceleration` function

- [ ] **detection/InflectionFinder.scala**
  - [ ] Implement `findInflectionPoint` function with rising/falling logic

- [ ] **detection/BufferManager.scala**
  - [ ] Implement `addToBuffer` function
  - [ ] Implement `releaseBuffer` function
  - [ ] Implement `toOutputRow` helper
  - [ ] Implement `toOutputRowWithEvents` helper
  - [ ] Implement `updateEvents` helper
  - [ ] Implement `computePhase` helper

### 8.5 New Files - Detection Modules

- [ ] **detection/TakeoffDetection.scala**
  - [ ] Implement `checkTrigger` using `config.takeoff.*`
  - [ ] Implement `checkConstraints` using `config.takeoff.*`
  - [ ] Implement `checkValidation` using `config.takeoff.*`

- [ ] **detection/FreefallDetection.scala**
  - [ ] Implement `checkTrigger` using `config.freefall.*`
  - [ ] Implement `checkConstraints` using `config.freefall.*`
  - [ ] Implement `checkValidation` using `config.freefall.*`

- [ ] **detection/CanopyDetection.scala**
  - [ ] Implement `checkTrigger` using `config.canopy.*`
  - [ ] Implement `checkConstraints` (no config needed, uses detected events)
  - [ ] Implement `checkValidation` using `config.canopy.*`

- [ ] **detection/LandingDetection.scala**
  - [ ] Implement `checkTrigger` using `config.landing.*`
  - [ ] Implement `checkWindowStability` using `config.landing.*`
  - [ ] Implement `checkConstraints` (no config needed, uses detected events)
  - [ ] Implement `checkValidation` using `config.landing.*`
  - [ ] Implement `updateStabilityWindow` using `config.landing.*`

### 8.6 Test Files

- [ ] **test/detection/PreprocessingSpec.scala**
  - [ ] Test clipSpeed within limits
  - [ ] Test clipSpeed exceeding limits
  - [ ] Test negative acceleration clipping
  - [ ] Test horizontal speed calculation
  - [ ] Test altitude correction

- [ ] **test/detection/SmoothingSpec.scala**
  - [ ] Test median with odd window
  - [ ] Test median with even window
  - [ ] Test window size maintenance
  - [ ] Test acceleration calculation

- [ ] **test/detection/InflectionFinderSpec.scala**
  - [ ] Test rising inflection (freefall)
  - [ ] Test falling inflection (canopy)
  - [ ] Test no inflection found fallback

- [ ] **test/detection/BufferManagerSpec.scala**
  - [ ] Test buffer size limiting
  - [ ] Test release without event
  - [ ] Test release with event correction

- [ ] **test/detection/TakeoffDetectionSpec.scala**
  - [ ] Test trigger conditions
  - [ ] Test constraint checking
  - [ ] Test validation

- [ ] **test/detection/FreefallDetectionSpec.scala**
  - [ ] Test speed-based trigger
  - [ ] Test acceleration-based trigger
  - [ ] Test altitude constraints
  - [ ] Test validation

- [ ] **test/detection/CanopyDetectionSpec.scala**
  - [ ] Test trigger conditions
  - [ ] Test prerequisite constraints

- [ ] **test/detection/LandingDetectionSpec.scala**
  - [ ] Test trigger with stability
  - [ ] Test stability window calculation
  - [ ] Test speed constraints

- [ ] **test/detection/FlightStagesDetectionIntegrationSpec.scala**
  - [ ] Test normal 4-event jump
  - [ ] Test missing takeoff
  - [ ] Test plane landing (no jump)
  - [ ] Test validation rejection

- [ ] **test/detection/RegressionSpec.scala**
  - [ ] Load expected results from points_results.csv
  - [ ] Generate tests for all jump files
  - [ ] Verify detection accuracy

### 8.7 Documentation Updates

- [ ] Update README.md with new algorithm description
- [ ] Update REQUIREMENTS.md if any deviations from spec
- [ ] Add configuration tuning guide
- [ ] Document known edge cases

### 8.8 Configuration Values Summary

All these values are preserved from existing code and stored in `DetectionConfig.default`:

| Value | Parameter Path |
|-------|----------------|
| 20.0 | `config.global.accelerationClip` |
| 25.0 | `config.takeoff.speedThreshold` |
| -1.0 | `config.takeoff.climbRate` |
| 600.0 | `config.takeoff.maxAltitude` |
| 5 | `config.takeoff.smoothingWindowSize` |
| 10 | `config.takeoff.backtrackWindowSize` |
| 40 | `config.takeoff.validationWindowSize` |
| 25.0 | `config.freefall.verticalSpeedThreshold` |
| 3.0 | `config.freefall.accelerationThreshold` |
| 10.0 | `config.freefall.accelerationMinVelocity` |
| 600.0 | `config.freefall.minAltitudeAbove` |
| 600.0 | `config.freefall.minAltitudeAbsolute` |
| 5 | `config.freefall.smoothingWindowSize` |
| 10 | `config.freefall.backtrackWindowSize` |
| 40 | `config.freefall.validationWindowSize` |
| 12.0 | `config.canopy.verticalSpeedMax` |
| 5 | `config.canopy.smoothingWindowSize` |
| 10 | `config.canopy.backtrackWindowSize` |
| 40 | `config.canopy.validationWindowSize` |
| 3.0 | `config.landing.speedMax` |
| 0.5 | `config.landing.stabilityThreshold` |
| 1.0 | `config.landing.meanVerticalSpeedMax` |
| 10 | `config.landing.stabilityWindowSize` |
| 5 | `config.landing.smoothingWindowSize` |
| 10 | `config.landing.backtrackWindowSize` |
| 40 | `config.landing.validationWindowSize` |

---

## Summary

This implementation plan provides a complete roadmap for implementing the flight stages detection algorithm. Key highlights:

1. **Preserved Interface**: `streamDetectA[F[_], A]` signature unchanged
2. **Hierarchical Config**: All 27 parameters in structured `DetectionConfig`
3. **Streaming Architecture**: fs2-based with `mapAccumulate` for state management
4. **Robust Detection**: Spike clipping, median smoothing, validation windows, backtracking
5. **Wrong Data Handling**: Graceful degradation, file classification, retry strategies
6. **Comprehensive Testing**: Unit, integration, regression, property-based tests
7. **Phased Rollout**: 5-week implementation with clear milestones

**Total New Files**: 16
**Total Modified Files**: 4
**Total Test Files**: 10

---

**IMPLEMENTATION PLAN COMPLETE**

