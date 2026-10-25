# Detection Algorithm

Beesight automatically analyses GPS telemetry from a skydive. It provides three analyses over the same stream of
data points:

- **Jump event detection** — identifies the key moments of the jump (takeoff, freefall exit, canopy deployment,
  landing) and labels every data point with its flight phase.
- **Swoop detection** — when the canopy ride ends with a high-performance landing, locates the swoop and measures
  its speeds, rotation and timing.
- **Flight metrics** — computes kinematic, aerodynamic and GPS-quality metrics for every data point.

```
  GPS points ──▸ Jump event detection ──▸ FlightEvents (takeoff, freefall, canopy, landing)
                     │
                     ├──▸ Swoop analysis ────▸ SwoopData (turn, rollout, max speeds, rotation)
                     │
                     └──▸ Flight metrics ────▸ one FlightMetric per point
```

This document describes how each analysis works at a conceptual level.

## Jump events detection

A skydive recording captured by a GPS logger contains much more than just the jump itself — it includes time on
the ground, the climb to altitude in the aircraft, freefall, canopy flight, and the landing. The jump event
detection divides this continuous recording into flight phases separated by transition events, so that every data
point is labelled with the phase it belongs to. The detection is resilient: not every phase must be present, and
transitions can be found even when intermediate phases are missing.

### Flight Phases and Transition Events

The recording is divided into five sequential **phases**, separated by four **transition events**:

```
  BeforeTakeoff ──▸ Climbing ──▸ Freefall ──▸ UnderCanopy ──▸ Landed
                 ↑            ↑            ↑               ↑
              Takeoff      Freefall      Canopy         Landing
```

The algorithm's job is to find the four transition events. Once found, every data point in the recording is
labelled with the phase it belongs to.

Not all phases need to be present. The algorithm is designed to detect transitions even when intermediate phases
are missing — for example, if the takeoff is too subtle to detect, the algorithm can jump directly from
`BeforeTakeoff` to `Freefall`. This resilience is described in detail in the
[Missing Phase Detection](#missing-phase-detection) section.

### Clipping

GPS velocity readings occasionally contain sharp spikes caused by signal loss or multipath errors. Before
detection begins, the raw GPS data is cleaned by **clipping**: a sliding window (`preprocessWindowSize`, 20
points by default) runs over the stream and each data point, as it passes through the window, is compared against
the linear interpolation of the window's **oldest and newest points**, evaluated at the point's time. If the
deviation of the vertical speed from the interpolated value exceeds the clip threshold (`accelerationClip`, 20
m/s by default), the point is replaced with the interpolated one, effectively stitching the signal back
together.

```
 velocity                           velocity
    ▲                                  ▲
    │          × ◂── spike             │
    │          │                       │
    │ -  -  - ╱-╲ -  -  - Clip         │         .○.
    │        ╱   ╲                     │        /   \
    │       ○     ○──○──○              │       ○     ○──○
    │      ╱                           │      /   ↑
    │  ○──○                            │  ○──○  clipped value
    │                                  │
    └─────────────────────▸ time       └─────────────────────────▸ time
      Before                           After
```

### The Sliding Window

The algorithm processes data through a fixed-size sliding window that moves over the recording one point at a time.
The window is logically divided into three regions:

```
  ┌─────────────┬───┬───────────────────┐
  │ Back Window │ ● │ Validation Window │
  └─────────────┴───┴───────────────────┘
                  ↑
                focus
```

**Back Window** — A configurable number of data points behind the focus. This region is used during the inflection
step to look backwards and pinpoint the exact moment a transition occurred. Its size is the larger of the backtrack
window and the smoothing window for each stage detector.

**Focus ●** — The single data point currently being evaluated for a transition. All trigger checks are performed at
this point. Its position within the window determines how much past and future context is available.

**Validation Window** — A configurable number of data points ahead of the focus. This region is used to confirm that
the trajectory after a candidate transition point matches the expected behaviour for the detected phase.

The total window size is derived from the three sub-regions: the maximum back window across all stage detectors,
plus one for the focus, plus the maximum validation window across all stage detectors.

**Window entries** — As each point enters the sliding window it is enriched with two derived quantities: a
**smoothed speed**, the component-wise median over the newest points of the window (size configured per flight
stage, `smoothingWindowSize`, 5 by default), and a per-point **acceleration**, the speed change against the
previous point over the elapsed time. The median filter preserves sharp transitions (unlike a moving average)
while suppressing the high-frequency jitter typical of consumer GPS devices. The smoothed speed feeds the trigger
and inflection checks; the validation checks work on the raw points.

### The Three-Step Detection Pipeline

For each focus position, the algorithm runs a three-step pipeline to determine whether a transition has occurred:

```
  ╭───────────╮   yes   ╭──────────────╮   yes   ╭─────────────╮
  │  Trigger  │ ─ ─ ─ ▸ │  Validation  │ ─ ─ ─ ▸ │ Inflection  │
  ╰───────────╯         ╰──────────────╯         ╰─────────────╯
        │                      │                        │
        ▼ no                   ▼ no                     ▼
   advance focus         reject candidate        pinpoint the exact
   to next point         (false positive)        transition moment
```

**Trigger** — A fast, threshold-based check on the kinematic properties at the focus point: speed, acceleration,
and altitude. Each flight stage has its own set of thresholds tuned to the physical characteristics of that
transition. The trigger acts as a cheap filter: _does this point look like it could be a transition?_ Most points
fail this check and are skipped immediately.

**Validation** — A forward-looking check that uses the validation window to confirm the trajectory after the
candidate point. For example, after a freefall trigger fires, validation checks that the jumper has actually
descended a significant distance over the validation window — consistent with real freefall rather than a transient
GPS spike. This step eliminates false positives that pass the trigger but don't correspond to real transitions.

**Inflection** — A backward-looking search over the back window to pinpoint the _exact_ moment the transition
occurred. The trigger tells us _roughly where_ a transition happened; the inflection step tells us _precisely when_.
It works by fitting two straight lines (a two-segment linear regression) to the signal in the back window, trying
every possible split point, and selecting the split that minimises the total fitting error. The optimal split point
is where the signal changes character — for example, where level flight gives way to rapid descent. If no clear
inflection is found, the focus point itself is used as the transition moment.

### Detection Lifecycle

The detection operates in two alternating modes: **steady state** and **sweep**.

#### Steady State

In normal operation, the focus sits at a fixed position within the window — the backtrack offset. Each time a new
data point enters the window, the oldest point slides out and is emitted with its detected phase label. The
three-step pipeline runs once at the focus position. If nothing is detected, the window advances by one point and
the process repeats.

```
Steady state — focus at backtrack offset, one check per point:

                 ┌───────────┬───┬──────────────┐
  emit oldest ◂─ │           │ ● │              │ ◂─ new point
                 └───────────┴───┴──────────────┘
```

#### On Detection

When the pipeline detects and confirms a transition at the focus point, the window is split in two:

1. **Before the detection** — All points to the left of the detection point are emitted with their pre-existing
   phase label. They belonged to the previous phase and are now finalised.

2. **From the detection onwards** — All points from the detection point to the right of the window are updated to
   the new phase. They form the seed of the new, partially-filled window.

```
Detection occurs at ●:

  ┌───────────┬───┬──────────────┐
  │ emit ◂──  │ ● │  ──▸ keep    │
  └───────────┴───┴──────────────┘
        ▼               ▼
  Emitted with      Relabelled to
  previous phase    new phase
```

Because a large portion of the window was just emitted, the remaining window is partially empty and needs to
accumulate new data points before detection can run again.

#### The Sweep

Once the window refills after a detection, the focus doesn't return to its normal backtrack offset. Instead, it
starts at position zero — the very beginning of the window — and **sweeps** forward, position by position, up to
the backtrack offset. At each position, the full three-step pipeline runs.

```
  Sweep — focus advances from 0 towards backtrack offset:

  ┌───┬──────────────+─ ─ ─ ─ ─ ─┐
  │ ● │              | refilling │    position 0: no detection
  └───┴──────────────+─ ─ ─ ─ ─ ─┘
  ┌─────┬───┬──────────────+─ ─ ─┐
  │     │ ● │              |     │    position 1: no detection
  └─────┴───┴──────────────+─ ─ ─┘
  ┌───────┬───┬──────────────+───┐
  │       │ ● │              |   │    position 2: detection! ──▸ split & emit
  └───────┴───┴──────────────+───┘
```

This sweep is what allows the algorithm to detect a new transition **as early as one point after the previous
detection**. If a freefall exit happens just one data point after takeoff, the sweep will find it because it starts
checking from position zero, although with limited data in the back window.

Once the sweep completes without finding a transition, the focus returns to the normal backtrack offset and the
algorithm re-enters steady state.

### Missing Phase Detection

The algorithm doesn't just look for the next expected transition — it also looks ahead to handle cases where a
phase might be too short, too noisy, or simply absent in the data.

For each current phase, the algorithm runs detection for multiple transition types, prioritised from most expected
to least expected:

| Current Phase   | Transitions Checked (in priority order) |
| --------------- | --------------------------------------- |
| `BeforeTakeoff` | Takeoff, then Freefall                  |
| `Climbing`      | Freefall, then Landing                  |
| `Freefall`      | Canopy, then Landing                    |
| `UnderCanopy`   | Landing                                 |
| `Landed`        | _(none — detection complete)_           |

The first detector that fires wins. This means:

- If takeoff is missed (e.g. the recording starts mid-climb), the algorithm detects freefall directly from
  `BeforeTakeoff`, skipping the `Climbing` phase entirely.
- If canopy deployment is missed (e.g. due to a very short canopy ride), the algorithm detects landing directly
  from `Freefall`, skipping `UnderCanopy`.
- If the aircraft comes back down without a jump, the algorithm can detect landing directly from `Climbing`.

This design makes the detection robust to real-world data where not every phase is cleanly represented.

### Per-Stage Detection

Each of the four transition detectors uses the same three-step pipeline but with different physical criteria
tailored to the characteristics of that particular transition.

**Takeoff** — Looks for the onset of climbing speed combined with horizontal movement, constrained to low
altitudes. The aircraft must be moving both upward and forward, and the altitude must be below a maximum threshold
to avoid confusing high-altitude manoeuvres with takeoff. Validation confirms that the altitude keeps rising over
the validation window, at least at the validation climb rate.

**Freefall Exit** — Looks for the onset of rapid vertical descent or a sudden burst of acceleration, constrained
to high altitudes. The trigger fires on either high downward speed or significant acceleration combined with
downward motion. Validation confirms that the jumper has descended a sustained distance over the validation
window — consistent with genuine freefall.

**Canopy Deployment** — Looks for the transition from freefall speeds to a moderate descent rate. The vertical
speed must have dropped below the freefall threshold, the jumper must be decelerating, and the altitude must sit
below the freefall exit and above a minimum canopy altitude. Validation checks that the descent rate over the
validation window falls between canopy speeds and freefall speeds — fast enough to still be descending, but too
slow to be in freefall.

**Landing** — Looks for sustained near-zero velocity in both vertical and horizontal axes, combined with low
variance of the vertical speed across the window and an altitude below the canopy deployment. Unlike the other
detectors, landing doesn't use inflection detection — the transition moment is simply the focus point where motion
stops. Validation confirms that the vertical speed stays stable across the entire validation window, ruling out
temporary pauses during canopy flight.

### Configuration

Window parameters, one set per stage (`takeoff`, `freefall`, `canopy`, `landing`; the defaults are shared by all
stages):

| Parameter                | Default    | Role                                                               |
| ------------------------ | ---------- | ------------------------------------------------------------------ |
| `*.smoothingWindowSize`  | 5 points   | Size of the median-smoothing window applied to entering points     |
| `*.backtrackWindowSize`  | 10 points  | How far behind the focus the trigger and inflection search reaches |
| `*.validationWindowSize` | 100 points | Forward-looking points used to confirm a detection                 |

General and per-stage thresholds:

| Parameter                          | Default    | Role                                                        |
| ---------------------------------- | ---------- | ----------------------------------------------------------- |
| `preprocessWindowSize`             | 20 points  | Size of the sliding window used for clipping                |
| `accelerationClip`                 | 20 m/s     | Maximum vertical-speed deviation before a point is replaced |
| `takeoff.verticalSpeedThreshold`   | −2.0 m/s   | Climb rate (negative = upward) the trigger requires         |
| `takeoff.horizontalSpeedThreshold` | 30.0 m/s   | Minimum horizontal speed for the takeoff trigger            |
| `takeoff.maxTakeoffAltitude`       | 600 m      | Maximum altitude at which takeoff can be detected           |
| `takeoff.validationVerticalSpeed`  | −2.466 m/s | Minimum sustained climb rate to confirm takeoff             |
| `freefall.verticalSpeedThreshold`  | 25.0 m/s   | Descent rate above which the primary trigger fires          |
| `freefall.accelerationThreshold`   | 3.0 m/s²   | Acceleration magnitude for the secondary trigger            |
| `freefall.accelerationMinVelocity` | 10.0 m/s   | Minimum descent speed for the secondary trigger             |
| `freefall.minFreefallAltitude`     | 600 m      | Minimum altitude at which freefall can be detected          |
| `freefall.validationVerticalSpeed` | 3.3565 m/s | Minimum sustained descent rate to confirm freefall          |
| `canopy.verticalSpeedThreshold`    | 10.0 m/s   | Upper bound on descent speed under canopy                   |
| `canopy.minCanopyAltitude`         | 600 m      | Minimum altitude at which canopy deployment can be detected |
| `canopy.validationVerticalSpeed`   | 1.3426 m/s | Minimum sustained descent rate under canopy                 |
| `landing.verticalSpeedThreshold`   | 1.0 m/s    | Maximum absolute vertical speed at landing                  |
| `landing.horizontalSpeedThreshold` | 2.0 m/s    | Maximum horizontal speed at landing                         |
| `landing.stabilityThreshold`       | 0.5 m/s    | Maximum std dev of the vertical speed across the window     |

## Swoop Detection

A **swoop** (canopy piloting) is a high-performance landing: the pilot dives the canopy towards the ground, levels
off just above it, and glides across the landing area at high speed. Beesight locates the swoop and measures it
with a set of metrics validated against the expected values from the reference recordings.

### Input and Preconditions

The swoop analysis runs on a cut of the recording that starts a configurable margin (10 s by default) before the
detected freefall event and runs to the end of the recording. When no freefall was detected, the whole recording
is analysed. A swoop is reported only when the canopy event was detected, every detection stage finds its event,
and the cut contains at least two points — otherwise no swoop is reported at all.

The analysis works on **raw (unsmoothed) speeds**: smoothing was observed to flatten the true peak of the dive and
to shift event indices by several samples, and the stage thresholds are tuned for the raw signal.

### The Swoop Profile

A clean swoop has a characteristic vertical-speed (vz) signature:

```
  vz (m/s)
    ▲
 21 │                   ╭──╮ ◂── dive point (max descent, below the altitude cap)
    │                  ╱   │
 15 │                 ╱    │
    │                ╱     │
  8 │  ────────╮    ╱      ╰──╮        ◂── rollout: descent decays towards the bottom of turn
  5 │  cruise  ╰───╯          ╰──╮  ╭── flare bump
  0 │                            ╰──╯  ╰──▸ stop (motion ends or pilot turns away)
    └───────────────────────────────────────▸ time
      turn initiation ────────────▸
```

The events, in order:

| Event                             | Definition                                                                                       |
| --------------------------------- | ------------------------------------------------------------------------------------------------ |
| **Pattern entry**                 | First crossing below the pattern-entry altitude (762 m / 2500 ft AGL); bounds all backward walks |
| **Turn initiation** (top of turn) | Where the speed-building turn began — the walk-back from the dive point                          |
| **Dive point**                    | The maximum descent rate below the altitude cap — the bottom of the dive                         |
| **Rollout start**                 | The maximum-descent point inside the 100 ft band above the stop — where recovery begins          |
| **Bottom of turn**                | Where the recovery ends and level flight begins; closes the rotation window                      |
| **Stop**                          | Where motion ends, or the pilot turns away, after the swoop; bounds all forward windows          |

### Ground Estimation

All swoop altitudes are reported as **AGL** (Above Ground Level), relative to a detected ground estimate. The
primary estimate is the median altitude over a short window (10 s by default) starting at the detected landing
point, located by its time within the series. When no landing event is available, the same window is taken from
the last slow point of the recording (total speed below 4.5 m/s); as a final fallback the altitude of the last
point is used.

### The Detection Pipeline

```
       cut stream (freefall − margin … end of recording)
                         │
                         ▼
                ┌─────────────────┐
                │ ground estimate │   all altitudes are AGL from here on
                └────────┬────────┘
                         ▼
                ┌─────────────────┐
                │  pattern entry  │   first crossing below 2500 ft AGL
                └────────┬────────┘
                         ▼
                ┌─────────────────┐
                │    dive point   │   max descent below the cap — no dive, no swoop
                └────────┬────────┘
             ┌───────────┴───────────┐
             ▼                       ▼
    ┌─────────────────┐     ┌─────────────────┐
    │   top of turn   │     │       stop      │   slow point fixes the heading, then stop or turn-away
    └────────┬────────┘     └────────┬────────┘
             └───────────┬───────────┘
                         ▼
                ┌─────────────────┐
                │ rollout start   │   max-descent walk inside the 100 ft band above the stop
                └────────┬────────┘
                         ▼
                ┌─────────────────┐
                │ bottom of turn  │   end of the recovery
                └─────────────────┘
```

**Stage 1 — Pattern entry.** The first point that crosses below the pattern-entry altitude (762 m / 2500 ft AGL).
All backward walks performed by the later stages are bounded below by this point, so manoeuvres high in the
pattern cannot leak into the swoop measurement.

**Stage 2 — Dive point.** The first point achieving the maximum descent rate among the points below the altitude
cap (304.8 m / 1000 ft AGL, searched from the pattern entry forward). Points at or below zero descent never
qualify, so a profile without a dive yields no swoop at all.

A swoop-gate experiment (candidate peaks + episode validation with a height floor and a continuity check) was
evaluated over the whole corpus and **discarded**: it rejected more real swoops than it repaired. The known
weakness of the argmax anchor — a manoeuvre stronger than the swoop breaking the downstream windows — remains
open, and any future fix must be validated against the reference recordings before wiring.

**Stage 3 — Top of turn (turn initiation).** From the dive point, walk backward to the first point below the pull
threshold (11.176 m/s ≈ 25 mph; falling back to the minimum-descent point if the threshold is never crossed),
then continue backward through the steepening phase: first while the descent is still accelerating downward, then
— when the descent rate is high enough to arm it — through an accumulator walk that tolerates brief pauses and
stops when the accumulated descent-rate increase exceeds the steepening threshold. The walk is aborted, keeping
its starting point, when the altitude gained exceeds the cap (152.4 m / 500 ft) or the turn rate exceeds
30°/s. The result is where the speed-building turn began.

**Stage 4 — Stop.** From the dive point forward, the first point below both the slow descent threshold
(4.4704 m/s ≈ 10 mph) and the slow horizontal threshold (8.9408 m/s ≈ 20 mph) fixes the **swoop heading**. From
there forward, the stop is the first point that is nearly stopped (horizontal speed below 0.89408 m/s ≈ 2 mph) or
that deviates more than 45° from the swoop heading — the pilot either comes to rest or carries the speed away
across the landing area. The stop bounds all forward measurement windows.

**Stage 5 — Rollout start.** From the top of turn, advance forward to the first point less than the rollout window
(30.48 m / 100 ft) above the stop altitude; from there, walk backward while the descent rate strictly increases.
The last index before the increase stops is the maximum-descent point — the rollout start. Starting the walk
100 ft above the stop (rather than at the landing point) is what bypasses the flare noise: the walk hits the swoop
peak first and stops at the cruise dip between any manoeuvre and the swoop. The walk never moving keeps the
starting point; reaching the top of turn fails the detection.

**Stage 6 — Bottom of turn.** Walk forward from the rollout while the altitude strictly decreases or the descent
stays fast (≥ 4.4704 m/s) — the last such point is the bottom candidate. Then take the earlier of two bracketed
crossings, compared by interpolated time: the backward altitude crossing (the first point more than the offset
(1 m) above the bottom altitude) or the forward descent crossing (the descent rate falling to 1.25 m/s). When
neither exists, the point after the rollout is used.

### Swoop Metrics

- **Max speeds** — maximum vertical and total speed over [turn initiation, stop), maximum horizontal speed over
  [rollout start, stop). Each is a first-max scan over strictly positive values, with the dive point as fallback
  for degenerate windows, and each carries the AGL altitude at which it occurred.
- **Degrees of rotation** — the cumulative signed heading change over (turn initiation, bottom of turn], measured
  as described below; negative for left turns, positive for right turns.
- **Turn direction** — left or right, resolved from the sign of the rotation.
- **Time to execute the turn** — the elapsed time from turn initiation to rollout start.

### Measuring the Rotation

The rotation is the signed sum of the per-sample turn angles — heading deltas wrapped to [-180°, 180°] — over the
turn window. Samples whose turn angle reaches a full turn (360°) are skipped, filtering GPS heading glitches.

Two refinements keep the measurement faithful to what the pilot flew:

- **Counter-rotation segments** — inside a steep, fast dive (dive angle above 60° and descent rate above
  26.8224 m/s ≈ 60 mph), a run of samples turning opposite to the established direction opens a _segment_. While
  the segment lasts, its angles are replaced by time-weighted shares of the net heading change across it, resolved
  into the direction of the turn that preceded it — so GPS wobble during the high-G portion of the dive doesn't
  cancel out real rotation. The replacement only applies when the resolved change stays under 315°, and the
  segment is capped by the dead-reckoned displacement from its start (9.144 m / 30 ft), computed from the
  horizontal velocities.
- **Drift trimming** — when enabled, the samples at both ends of the window that rotate at or below the drift
  thresholds (7° at the tail, 2° at the head) are trimmed off, sharing one budget for the maximum net shift the
  trims may introduce (20°). This excludes the cruise drift before and after the turn from the measurement.

### Altitude Reference: AGL vs MSL

GPS loggers report **MSL** (Mean Sea Level) altitude; swoop metrics are reported as **AGL**, relative to the
detected ground estimate (see [Ground Estimation](#ground-estimation)). Beesight computes internally in MSL and
converts to AGL at the reporting boundary. All comparisons against reference expected values must be
AGL-to-AGL — comparing MSL against AGL fails by the drop-zone elevation, which exceeds the test tolerance.

### Configuration

| Parameter                         | Default              | Role                                                               |
| --------------------------------- | -------------------- | ------------------------------------------------------------------ |
| `altitudes.patternEntryAltitude`  | 762 m (2500 ft)      | Pattern entry: first crossing below this AGL altitude              |
| `altitudes.diveAltitudeCap`       | 304.8 m (1000 ft)    | Dive point: maximum AGL altitude for the max-descent scan          |
| `altitudes.maxAltitudeGain`       | 152.4 m (500 ft)     | Steepening walk: altitude gain that aborts the walk                |
| `altitudes.bottomAltitudeOffset`  | 1 m                  | Bottom of turn: altitude offset for the backward bracket           |
| `altitudes.rolloutWindow`         | 30.48 m (100 ft)     | Rollout start: height above the stop where the search starts       |
| `speeds.pullThreshold`            | 11.176 m/s (25 mph)  | Top of turn: descent rate below which the dive is over             |
| `speeds.steepeningEntry`          | 6.7056 m/s (15 mph)  | Descent rate that arms the steepening accumulator                  |
| `speeds.steepeningAccumulator`    | 6.7056 m/s (15 mph)  | Accumulated descent-rate increase that ends the walk               |
| `speeds.stopDescentThreshold`     | 4.4704 m/s (10 mph)  | Slow point: descent rate to fix the swoop heading                  |
| `speeds.stopHorizontalThreshold`  | 8.9408 m/s (20 mph)  | Slow point: horizontal speed to fix the swoop heading              |
| `speeds.stopEndHorizontal`        | 0.89408 m/s (2 mph)  | Horizontal speed that confirms the stop                            |
| `speeds.bottomDescentThreshold`   | 4.4704 m/s (10 mph)  | Bottom candidate: descent rate that continues the walk             |
| `speeds.bottomVzCross`            | 1.25 m/s             | Bottom of turn: descent rate of the forward crossing               |
| `angles.stopHeadingDeviation`     | 45°                  | Heading deviation from the swoop heading that ends the stop search |
| `angles.maxTurnRate`              | 30°/s                | Steepening walk: turn rate that aborts the walk                    |
| `rotation.fullTurnAngle`          | 360°                 | Per-sample turn angles at or above this value are skipped          |
| `rotation.diveAngleCap`           | 60°                  | Dive angle above which a point is inside a steep dive              |
| `rotation.diveVerticalSpeed`      | 26.8224 m/s (60 mph) | Descent rate above which a point is inside a fast dive             |
| `rotation.turnStateThreshold`     | 2°                   | Per-sample angle that classifies a turn to the left or right       |
| `rotation.segmentAngleCap`        | 315°                 | Resolved segment heading change below which angles are replaced    |
| `rotation.segmentDisplacementCap` | 9.144 m (30 ft)      | Maximum dead-reckoned displacement of a segment                    |
| `rotation.trimEnabled`            | true                 | Whether the drift at the window ends is trimmed off                |
| `rotation.tailTrimThreshold`      | 7°                   | Per-sample angle that counts as drift at the window tail           |
| `rotation.tailTrimCap`            | 20°                  | Net angle the tail trim may shift the measurement by               |
| `rotation.headTrimThreshold`      | 2°                   | Per-sample angle that counts as drift at the window head           |
| `rotation.headTrimCap`            | 20°                  | Net angle the head trim may shift the measurement by               |
| `ground.window`                   | 10 s                 | Duration of the window used to estimate the ground altitude        |
| `ground.slowSpeed`                | 4.4704 m/s (10 mph)  | Total speed of a slow point in the ground fallback                 |
| `stream.beforeCanopyMargin`       | 10 s                 | Data retained before the freefall event when cutting the stream    |

## Flight Metrics

Alongside the event detection, Beesight computes a set of **flight metrics** — kinematic, aerodynamic and
GPS-quality quantities emitted for every data point of the recording. The metrics run on the raw point stream,
independently of the event pipeline, and can be combined with the detected events and swoop data into a single
enriched output per point.

### The Metrics Window

Each point's metrics are computed over a centred sliding window of at most `2 · slopeHalfWindow + 1` points
(9 by default). The window is clamped at the two ends of the recording — the first and last points are computed
with whatever context is available. The windowed quantities are least-squares slopes of the underlying signals
over this window, which makes them robust against per-sample GPS noise.

The altitude reported by the metrics is relative to the configured ground elevation (default 0, i.e. MSL as
reported by the GPS).

### Kinematic Metrics

**Vertical speed** (m/s) — the vertical component of the raw GPS velocity, positive downward (the convention used
throughout Beesight). Positive values mean descent, negative values mean climb.

**Horizontal speed** (m/s) — the speed over the ground: the magnitude of the horizontal velocity components,
`√(north² + east²)`.

**Total speed** (m/s) — the magnitude of the full 3D velocity, `√(north² + east² + vertical²)`.

**Course** (deg) — the direction of travel over the ground, computed as `atan2(east, north)` normalised to
[0°, 360°): 0° = North, 90° = East, 180° = South, 270° = West.

**Course rate** (deg/s) — how fast the direction of travel rotates: the least-squares slope of the course over
the metrics window. The course is first unwrapped across the north discontinuity, so a turn passing through north
doesn't produce a false spike in the slope.

**Turn rate** (deg/s) — the instantaneous signed rotation of the course against the previous point: the course
delta wrapped to (−180°, 180°] divided by the elapsed time. Positive values turn right (clockwise), negative
values turn left; 0 for the first point of the recording.

**Glide ratio** — the horizontal distance covered per unit of descent: `horizontal speed / vertical speed`. It
answers "how far forward per metre down" — a glide ratio of 3 means 3 m forward for every metre of altitude lost.
It is 0 when not descending.

**Dive angle** (deg) — the angle of the flight path below the horizon: `atan2(vertical, horizontal)`. Positive
when descending, 0 in level flight, and ±90° in a pure vertical dive (no horizontal speed).

**Dive rate** (deg/s) — how fast the flight path steepens or flattens: the least-squares slope of the dive angle
over the metrics window. Positive values mean steepening, negative values mean flattening out.

**Acceleration** (m/s²) — the smoothed acceleration vector: the component-wise least-squares slopes of the north,
east and vertical speeds over the metrics window, in the local north/east/down frame.

**Acceleration magnitude** (m/s²) — the Euclidean norm of the acceleration vector.

**Acceleration along-track** (m/s²) — the projection of the acceleration onto the direction of travel, computed
from the horizontal components: the dot product of the acceleration with the horizontal velocity, divided by the
horizontal speed. Positive values accelerate forward, negative values brake.

**Acceleration cross-track** (m/s²) — the component of the acceleration to the right of the direction of travel:
`(a_east·v_north − a_north·v_east) / horizontal speed`. This is the centripetal acceleration in turns — positive
pulls right, negative pulls left.

### Energy Metrics

**Total energy** (J/kg) — the specific mechanical energy of the pilot: the kinetic energy per unit mass plus the
potential energy per unit mass, `V²/2 + g·h`, with the altitude measured above the configured ground elevation.
In still air with no drag it stays constant: trading altitude for speed and back moves energy between the two
terms without changing the sum.

**Energy rate** (J/kg/s) — the rate of change of the total energy, computed as `V·dV/dt − g·vz` from the windowed
total-speed slope and the vertical speed. It is the mechanical power gained or lost per unit mass: negative under
drag, positive when gaining energy (e.g. the aircraft climb).

### Aerodynamic Coefficients

**Lift coefficient** and **drag coefficient** — dimensionless measures of the aerodynamic forces acting on the
pilot/canopy system, estimated from the observed accelerations:

1. The windowed acceleration is corrected for gravity by subtracting `g` from its vertical component.
2. The component of the remaining acceleration **along** the flight path is the deceleration caused by **drag**;
   everything **perpendicular** to the flight path is caused by **lift**.
3. Each acceleration becomes a coefficient through the dynamic pressure `q = ρ·V²/2` — with ρ the air density
   from the ISA troposphere model at the point's altitude — as `C = m·a / (q·S)`, where `m` is the configured
   flying weight and `S` the configured canopy planform area.

The lift coefficient reflects how hard the canopy works the air to support the pilot (large during a snivel, a
front-riser dive or a flare); the drag coefficient reflects how much the system bleeds speed (large in deep
brakes or a dive). Both are 0 when stationary.

### GPS Quality Metrics

Computed from the accuracy fields the input format provides; fields the format lacks yield no value.

**Horizontal / vertical accuracy** (m), **speed accuracy** (m/s), **satellites** — the device-reported values,
passed through for quality monitoring.

**Course accuracy** (deg) — the accuracy of the course as reported by the device, when the format provides it;
otherwise estimated from the speed accuracy as `deg(speedAccuracy / totalSpeed)` — the angular error implied by
the speed error at the current speed.

**Spherical error probable** (m) — an estimate of the radius of the sphere containing 95% of the 3D position
fixes, derived from the reported horizontal and vertical accuracies as `0.5127 · (2·hAcc + vAcc)`.

**Speed score accuracy** (m/s) — the accuracy of a 3-second speed average, derived from the vertical accuracy as
`√2 · vAcc / 3`. It quantifies the uncertainty of competition-style speed scores computed from the recording.

### Configuration

| Parameter         | Default  | Role                                                                     |
| ----------------- | -------- | ------------------------------------------------------------------------ |
| `slopeHalfWindow` | 4        | Half-size of the least-squares window; the full window is 9 points       |
| `mass`            | 90 kg    | Total flying weight used by the lift/drag coefficients                   |
| `planformArea`    | 2.0 m²   | Canopy planform area used by the lift/drag coefficients                  |
| `groundElevation` | _(none)_ | Optional ground elevation (m MSL) the altitudes are reported relative to |
