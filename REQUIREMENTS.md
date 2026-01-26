# Flight Stages Detection - Requirements Document

## 1. Purpose and Objective

Automatically detect and label four **flight events** from skydiving altimeter timeseries data:

1. **Takeoff** - Aircraft begins movement
2. **Freefall** - Jumper exits aircraft
3. **Canopy** - Parachute opens
4. **Landing** - Jumper touches ground

**Correct Behavior:**
```
Row 0-11: freefall=None
Row 12: freefall=Some(FlightPoint(12, altitude))  ← Event SET at correct row
Row 13+: freefall=Some(FlightPoint(12, altitude))
```

The event index in output must match the row where the event is FIRST SET.

---

## 2. Terminology

| Term | Definition |
|------|------------|
| **Flight Event** | Specific point in time when a transition occurs (takeoff, freefall, canopy, landing) |
| **Flight Phase** | Period between events: BeforeTakeoff → Climbing → Freefall → UnderCanopy → Landed |
| **Point** | Single row of input data with timestamp, altitude, velocities |
| **Inflection Point** | True moment event occurred (found via backtracking from detection trigger) |
| **Spike** | Erroneous GPS reading producing unrealistic velocity/altitude values |

---

## 3. Data Format

### Input (Do Not Change)

```scala
final case class InputFlightRow[A](
  time: java.time.Instant,
  altitude: Double,       // Meters above sea level
  northSpeed: Double,     // Velocity north in m/s
  eastSpeed: Double,      // Velocity east in m/s
  verticalSpeed: Double,  // Velocity down in m/s (positive = descending)
  source: A,
)
```

### Output (Remove lastPoint)

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

---

## 4. Domain Constraints

1. **Events occur in order** - Takeoff → Freefall → Canopy → Landing (never out of order)
2. **Takeoff may be missing** - Recording can start after aircraft is airborne
3. **Freefall may be missing** - Plane may land without dropping jumpers, or data may be erroneous
4. **Canopy can occur very early** - As early as 1 second after exit (hop-n-pop jumps)
5. **Landing can occur without freefall/canopy** - Aircraft landing, aborted jumps
6. **Freefall cannot occur below 600m** - Physical/safety constraint
7. **One of each event maximum** - Only one of each event type per file
8. **Plane may descend before exit** - Aircraft can lose altitude while approaching drop zone (2-8 m/s typical), which is much slower than freefall terminal velocity (50-70 m/s). Speed thresholds (25 m/s) and acceleration detection distinguish exit from plane descent.
9. **Landing altitude may differ from takeoff** - Takeoff and landing can be at different altitudes (mountain dropzones, coastal areas)

---

## 5. Data Anomalies

### 5.1 Spike Types Observed

| Location | Description |
|----------|-------------|
| **Start of recording** | Initial readings show unrealistic values (GPS acquiring signal) |
| **During climb** | Sudden altitude/speed jumps (GPS multipath/interference) |
| **Right before exit** | Erratic readings (jumper movement affecting antenna) |
| **During canopy ride** | Occasional spikes (antenna orientation changes) |

### 5.2 Spike Characteristics

- **Duration:** Can last up to 10 seconds
- **Vertical speed spikes:** Sudden jump to 100+ m/s then back to normal
- **Altitude spikes:** Sudden altitude gain/loss of hundreds of meters
- **Impact:** Can trigger false detection if not handled
- **Beginning:** They happen often at the beginning of the data because they GPS needs to warm up
- **Exit:** As the skydiver approaches the door more satellites become visible and this creates spikes

---

## 6. Detection Criteria

### 6.1 Takeoff Detection

**Trigger Condition:**
```
horizontalSpeed > speedThreshold AND smoothedVerticalSpeed < climbRate
```

**Constraints:**
- Event not already detected
- Not marked as missed takeoff
- Altitude below max takeoff altitude

**Inflection Search:** Rising velocity (isRising = true)

### 6.2 Freefall Detection

**Trigger Condition (OR):**
```
smoothedVerticalSpeed > verticalSpeedThreshold
OR
(smoothedVerticalAcceleration > accelerationThreshold AND smoothedVerticalSpeed > accelerationMinVelocity)
```

**Constraints:**
- Must occur after takeoff (if detected)
- Altitude above takeoff + minAltitudeAbove OR above minAltitudeAbsolute (600m)
- Index must be after takeoff index

**Inflection Search:** Rising velocity (isRising = true)

### 6.3 Canopy Detection

**Trigger Condition:**
```
smoothedVerticalSpeed > 0 AND smoothedVerticalSpeed < verticalSpeedMax
```

**Constraints:**
- Freefall must be detected first
- Altitude above takeoff altitude
- Altitude below freefall altitude
- Index must be after freefall index

**Inflection Search:** Falling velocity (isRising = false)

### 6.4 Landing Detection

**Trigger Condition:**
```
totalSpeed < speedMax AND windowIsStable
```

**Window Stability:**
```
stdDev(verticalSpeedWindow) < stabilityThreshold AND abs(mean(verticalSpeedWindow)) < meanVerticalSpeedMax
```

**Constraints:**
- Canopy OR takeoff must be detected first
- Altitude below canopy altitude
- Index must be after canopy index

**Inflection Search:** Falling velocity (isRising = false)

---

## 7. Algorithm

### 7.1 Core Concept: Detect → Validate → Backtrack → Release

Detection happens when thresholds are crossed, but this point may NOT be the true transition. Need to **backtrack** to find the inflection point.

### 7.2a Shared phase

FOR each input point:
  1. Preprocess the point (remove spikes via clipping)
  2. Compute kinematics (speeds, smoothing via median filter)
  3. Update sliding windows per event


### 7.2 Phase: Streaming (Normal Processing)

```
FOR each input point:
  4. Add current state to pendingStates buffer
  5. Trim buffer to max size
  6. Run detection for current phase

  IF detection triggers:
    → Transition to Validation(validationWindowSize, eventType)
    → Continue buffering, don't emit yet
  ELSE:
    → Emit oldest state from buffer (FIFO)
    → Remove emitted state from buffer
```

### 7.3 Phase: Validation

```
FOR each input point:
  2. Add current state to buffer
  3. Decrement remainingPoints

  IF remainingPoints == 0:
    IF condition still holds (validated):
      → Find inflection point in backtrack window
      → Match inflection index to state in buffer
      → Release buffer with corrected events:
        - States before inflection: event=None
        - States at/after inflection: event=Some(FlightPoint)
      → Transition to Streaming

    IF rejected (validation failed):
      → Release buffer unchanged (event=None for all)
      → Transition to Streaming

  IF still waiting (remainingPoints > 0):
    → Continue buffering, don't emit
```

### 7.4 End of Stream

When stream ends while in Validation:
- Check if validation condition holds at current point
- If condition holds → validated, release buffer with corrected events
- If condition doesn't hold → rejected, release buffer unchanged

### 7.5 Visual Example (Freefall Detection)

```
Config: backtrackWindow=5, validationWindow=10

Point 10: Streaming, buffer=[6,7,8,9,10], emit 6
Point 11: Streaming, buffer=[7,8,9,10,11], emit 7
Point 12: Streaming, buffer=[8,9,10,11,12], emit 8
Point 13: Streaming, buffer=[9,10,11,12,13], emit 9
Point 14: TRIGGER! Validation(10, Freefall), buffer=[10,11,12,13,14]
Point 15: remainingPoints=9, buffer=[10,11,12,13,14,15]
...
Point 24: remainingPoints=0, VALIDATED!
          → Search backtrack window → inflection at index 12
          → Release buffer:
            - state10: freefall=None
            - state11: freefall=None
            - state12: freefall=Some(FlightPoint(12, altitude)) ← SET HERE
            - state13-24: freefall=Some(FlightPoint(12, altitude))
          → Transition to Streaming
Point 25: Streaming, looking for Canopy now...
```

---

## 8. Configuration Structure

### 8.1 Top-Level Configuration

```scala
final case class DetectionConfig(
  global: GlobalConfig,
  takeoff: TakeoffConfig,
  freefall: FreefallConfig,
  canopy: CanopyConfig,
  landing: LandingConfig,
)
```

### 8.2 Global Configuration

```scala
final case class GlobalConfig(
  accelerationClip: Double,  // Max acceleration for spike clipping (m/s²)
)
```

### 8.3 Takeoff Configuration

```scala
final case class TakeoffConfig(
  speedThreshold: Double,       // Min horizontal speed to detect takeoff
  climbRate: Double,            // Max vertical speed (ascending, negative value)
  maxAltitude: Double,          // Max altitude for takeoff detection
  smoothingWindowSize: Int,     // Median filter window size
  backtrackWindowSize: Int,     // Samples for inflection search
  validationWindowSize: Int,    // Samples to validate detection
)
```

### 8.4 Freefall Configuration

```scala
final case class FreefallConfig(
  verticalSpeedThreshold: Double,    // Speed threshold for freefall
  accelerationThreshold: Double,     // Acceleration threshold
  accelerationMinVelocity: Double,   // Min velocity for accel detection
  minAltitudeAbove: Double,          // Min altitude above takeoff
  minAltitudeAbsolute: Double,       // Min absolute altitude (600m)
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)
```

### 8.5 Canopy Configuration

```scala
final case class CanopyConfig(
  verticalSpeedMax: Double,     // Max vertical speed under canopy
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)
```

### 8.6 Landing Configuration

```scala
final case class LandingConfig(
  speedMax: Double,               // Max total speed for landing
  stabilityThreshold: Double,     // Max stdDev of vertical speed
  meanVerticalSpeedMax: Double,   // Max mean vertical speed
  stabilityWindowSize: Int,       // Window for stability check
  smoothingWindowSize: Int,
  backtrackWindowSize: Int,
  validationWindowSize: Int,
)
```

---

## 9. State Structure

### 9.1 Per-Event State Separation

Each event maintains its own sliding windows:

```scala
final case class EventState(
  smoothingWindow: Vector[Double],           // For median filter
  backtrackWindow: Vector[VerticalSpeedSample],  // For inflection search
)

final case class VerticalSpeedSample(
  index: Long,
  speed: Double,
  altitude: Double,
)
```

### 9.2 Processing State

```scala
final case class ProcessingState[A](
  point: InputFlightRow[A],
  index: Long,
  kinematics: PointKinematics,
  streamPhase: StreamPhase,
  detectedEvents: DetectedEvents,
  buffer: Vector[ProcessingState[A]],
  takeoffState: EventState,
  freefallState: EventState,
  canopyState: EventState,
  landingState: EventState,
)

enum StreamPhase {
  case Streaming
  case Validation(remainingPoints: Int, eventType: EventType)
}

enum EventType { case Takeoff, Freefall, Canopy, Landing }

final case class DetectedEvents(
  takeoff: Option[FlightPoint],
  freefall: Option[FlightPoint],
  canopy: Option[FlightPoint],
  landing: Option[FlightPoint],
)
```

---

## 10. Spike Mitigation

### 10.1 Acceleration Clipping (Global)

Limit velocity change between consecutive samples to maximum physically possible:
```
if abs(current - previous) > accelerationClip * dt:
  clipped = previous + sign(delta) * accelerationClip * dt
```

Applied to: verticalSpeed, northSpeed, eastSpeed

### 10.2 Altitude Correction

When vertical speed is clipped, recalculate altitude:
```
altitude = previousAltitude - clippedVerticalSpeed * dt
```

### 10.3 Median Filter Smoothing (Per-Event)

Use median of window for smoothed values - robust to outliers:
```
smoothedSpeed = median(smoothingWindow)
```

### 10.4 Validation Windows

Wait N samples after detection trigger to confirm event is real, not spike-induced.

---

## 11. Design Principles

1. **KISS** - Keep each function simple, under 40 lines
2. **Modules over classes** - Use objects with pure functions
3. **No default values** - Explicit construction of all ADTs
4. **No copy()** - Full reconstruction of state objects
5. **ADTs as data only** - No behavior in case classes
6. **No comments in code** - Self-documenting through naming
7. **Explicit naming** - Descriptive names for all functions and variables
8. **Median filter** - For GPS spike robustness
9. **Per-event state** - Separate sliding windows for each event type
10. **Module isolation** - Detection modules must not leak implementation details between each other or algorithm steps. Each detection module receives only: kinematics, its own config, and detected events (as Option[FlightPoint]). No internal state, windows, or thresholds from other modules.

---

## 12. Implementation Checklist

- [ ] Update OutputFlightRow (remove lastPoint)
- [ ] Create new config structure (GlobalConfig + per-event configs)
- [ ] Create state structures (EventState, ProcessingState)
- [ ] Implement preprocessing with clipping
- [ ] Implement median filter smoothing
- [ ] Implement buffer management
- [ ] Implement stream phase logic (Streaming + Validation)
- [ ] Update detection modules (TakeoffDetection, FreefallDetection, CanopyDetection, LandingDetection)
- [ ] Implement inflection point finding
- [ ] Implement end-of-stream handling
- [ ] Run tests and validate

