# FlightStagesDetection Refactoring Plan

## Problem Statement

The current detection system has a fundamental bug: **FlightPoint.index doesn't match the row where the event is SET in the output stream.**

### Example of the Bug

When freefall is detected at point 14 and backtracking finds the true inflection at point 12:

**Current behavior (WRONG):**
```
Row 0-11: freefall=None    ← Already emitted before detection
Row 12: freefall=None      ← Already emitted! WRONG!
Row 13: freefall=None      ← Already emitted! WRONG!
Row 14: freefall=Some(FlightPoint(12, 1.0))  ← FlightPoint says 12 but row is 14
Row 15+: freefall=Some(FlightPoint(12, 1.0))
```

**Correct behavior:**
```
Row 0-11: freefall=None
Row 12: freefall=Some(FlightPoint(12, 1.0))  ← Event SET at correct row
Row 13+: freefall=Some(FlightPoint(12, 1.0))
```

### Root Causes

1. **No buffering** - Points are emitted immediately, before detection can backtrack
2. **No validation window** - Detection triggers instantly without verification
3. **Immediate emission** - By the time backtracking finds the true point, earlier rows are already emitted

---

## Algorithm Conceptual Steps

### Overview

The algorithm processes flight data points sequentially, detecting four flight events in order:
1. **Takeoff** - Aircraft starts moving
2. **Freefall** - Jumper exits aircraft
3. **Canopy** - Parachute opens
4. **Landing** - Jumper touches ground

Each event detection follows the same pattern: trigger → buffer → validate → find inflection → release with corrected FlightEvents.

### Data Structures

| Structure | Purpose | Contents |
|-----------|---------|----------|
| `Windows.backtrackVerticalSpeed` | Find inflection point | `VerticalSpeedSample` (index, speed, altitude) |
| `pendingStates` | Hold states for re-emission | `StreamState` objects |
| `StreamStatePhase` | Track processing state | `Streaming` or `WaitingValidation` |

### Step-by-Step Algorithm

#### Phase: Streaming (Normal Processing)

```
FOR each input point:
  1. Preprocess the point (smooth spikes)
  2. Compute kinematics (speed, acceleration)
  3. Update sliding windows (add to backtrackVerticalSpeed)
  4. Add current state to pendingStates buffer (always buffer last N states)
  5. Trim pendingStates to max buffer size
  6. Run detection for current phase (Takeoff/Freefall/Canopy/Landing)

  IF detection triggers:
    → Transition to WaitingValidation(validationWindowSize, eventType)
    → Continue buffering, don't emit yet
  ELSE:
    → Emit oldest state from buffer (first in, first out)
    → Remove emitted state from buffer
```

#### Phase: WaitingValidation

```
FOR each input point:
  1. Preprocess, compute kinematics, update windows
  2. Add current state to pendingStates buffer
  3. Decrement remainingPoints

  IF remainingPoints == 0:
    → Check if event is validated (condition still holds)

    IF validated:
      → Find inflection point in backtrackVerticalSpeed window
      → Match inflection index to state in pendingStates buffer
      → Release buffer with corrected FlightEvents:
        - States before inflection: event=None
        - States at/after inflection: event=Some(FlightPoint(inflectionIndex, altitude))
      → Transition to Streaming

    IF rejected (validation failed):
      → Release buffer with event=None for all
      → Transition to Streaming
      → Next point will be checked for detection

  IF still waiting (remainingPoints > 0):
    → Continue buffering, don't emit
```

#### End of Stream

When stream ends while in WaitingValidation:
- Check if validation condition holds at current point
- If condition holds → validated, release buffer with corrected events
- If condition doesn't hold → rejected, release buffer unchanged

### Visual Example (Freefall Detection)

```
Config: freefallBacktrackWindow=4, freefallValidationWindow=10

Point 10: Streaming, buffer=[6,7,8,9,10], emit 6
Point 11: Streaming, buffer=[7,8,9,10,11], emit 7
Point 12: Streaming, buffer=[8,9,10,11,12], emit 8
Point 13: Streaming, buffer=[9,10,11,12,13], emit 9
Point 14: TRIGGER! WaitingValidation(10, Freefall), buffer=[10,11,12,13,14]
Point 15: remainingPoints=9, buffer=[10,11,12,13,14,15]
Point 16: remainingPoints=8, buffer=[10,...,16]
...
Point 24: remainingPoints=0, VALIDATED!
          → Search backtrackVerticalSpeed → inflection at index 12
          → Find state with dataPointIndex=12 in buffer
          → Release buffer:
            - state10: freefall=None
            - state11: freefall=None
            - state12: freefall=Some(FlightPoint(12, altitude)) ← SET HERE
            - state13-24: freefall=Some(FlightPoint(12, altitude))
          → Transition to Streaming
Point 25: Streaming, looking for Canopy now...
```

---

## Solution Design

### StreamStatePhase ADT

```scala
enum StreamStatePhase:
  case Streaming
  case WaitingValidation(
    remainingPoints: Int,
    eventType: FlightPhase,
  )
```

### StreamState Changes

```scala
final case class StreamState[A](
  inputPoint: InputFlightRow[A],
  dataPointIndex: Long,
  kinematics: Kinematics,
  windows: Windows,
  detectedPhase: FlightPhase,
  detectedStages: FlightEvents,
  takeoffMissing: Boolean,
  pendingStates: Vector[StreamState[A]],
  streamPhase: StreamStatePhase,
)
```

### Processing Pipeline

```
Input → Preprocess → Kinematics → Windows → Detect → Buffer/Emit
```

### Key Design Decisions

1. **Windows and Buffer are separate** - Windows hold computed values for detection, Buffer holds states for emission
2. **Always buffer** - pendingStates always holds last N states, regardless of phase
3. **Per-event configuration** - Each event type has its own backtrack and validation window sizes
4. **Rejection path** - If validation fails, discard tentative detection and check next point
5. **End-of-stream validation** - Check if validation condition holds at stream end

---

## Configuration Parameters

Each flight event has its own configuration:

| Parameter | Description |
|-----------|-------------|
| `TakeoffBacktrackWindow` | States to hold for takeoff backtracking |
| `TakeoffValidationWindow` | Points to wait before validating takeoff |
| `FreefallBacktrackWindow` | States to hold for freefall backtracking |
| `FreefallValidationWindow` | Points to wait before validating freefall |
| `CanopyBacktrackWindow` | States to hold for canopy backtracking |
| `CanopyValidationWindow` | Points to wait before validating canopy |
| `LandingBacktrackWindow` | States to hold for landing backtracking |
| `LandingValidationWindow` | Points to wait before validating landing |

---

## Implementation Checklist

### Phase 1: Data Structures
- [x] Create `detection/model/StreamStatePhase.scala` with enum
- [x] Update `StreamState` to add `streamPhase` field
- [x] Update `StreamState.create` to initialize `streamPhase = StreamStatePhase.Streaming`
- [x] Add per-event backtrack/validation window configs to `DetectionConfig`

### Phase 2: Buffer Management
- [ ] Create `detection/BufferManagement.scala` module
- [ ] Implement always-buffering logic (add state, trim to max size)
- [ ] Implement buffer release with FlightEvents correction
- [ ] Implement finding state by dataPointIndex in buffer

### Phase 3: Stream Phase Logic
- [ ] Create `detection/StreamPhaseLogic.scala` module
- [ ] Implement `handleStreaming` - check detection, transition if triggered
- [ ] Implement `handleWaitingValidation` - countdown, validate/reject
- [ ] Implement validation condition checking
- [ ] Implement rejection path (release buffer unchanged, return to Streaming)

### Phase 4: Integration
- [ ] Update `FlightStagesDetection.processPoint` to use StreamPhaseLogic
- [ ] Update stream emission logic in `streamDetectA`
- [ ] Handle end-of-stream (check validation condition, flush buffer)

### Phase 5: Detection Module Updates
- [ ] Update `FreefallDetection` to return detection trigger only (no FlightPoint creation)
- [ ] Update `TakeoffDetection` similarly
- [ ] Update `CanopyDetection` similarly
- [ ] Update `LandingDetection` similarly
- [ ] Move FlightPoint creation to buffer release logic

### Phase 6: Cleanup
- [ ] Remove dead/unnecessary code
- [ ] Remove all code comments
- [ ] Ensure no `copy()` usage (per guidelines)

### Phase 7: Testing
- [ ] Compile and run existing tests
- [ ] Verify baseline 4 failures remain (no regressions)
- [ ] Test edge cases (buffer overflow, end-of-stream, rejection)

---

## Key Design Principles

1. **KISS** - Keep each function simple, under 40 lines
2. **Modules over classes** - BufferManagement and StreamPhaseLogic as pure objects
3. **No default values** - Explicit construction of all ADTs
4. **No copy()** - Full reconstruction of StreamState
5. **ADTs as data only** - StreamStatePhase is pure data
6. **No comments in code** - Code should be self-documenting through naming
7. **Explicit naming** - Descriptive names for all functions and variables

---

## Risk Mitigation

1. **Incremental implementation** - Each phase compiles independently
2. **Test after each phase** - Run tests to catch regressions early
3. **Clear separation** - BufferManagement and StreamPhaseLogic are isolated modules
