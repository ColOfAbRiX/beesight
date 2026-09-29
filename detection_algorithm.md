# Detection Algorithm

Beesight automatically identifies the key moments of a skydive from GPS telemetry. This document describes how the
detection algorithm works at a conceptual level.

## Flight Phases and Transition Events

A skydive recording captured by a GPS logger contains much more than just the jump itself — it includes time on the
ground, the climb to altitude in the aircraft, freefall, canopy flight, and the landing. Beesight divides this
continuous recording into five sequential **phases**, separated by four **transition events**:

```
  BeforeTakeoff ──▸ Climbing ──▸ Freefall ──▸ UnderCanopy ──▸ Landed
                 ↑            ↑            ↑               ↑
              Takeoff      Freefall      Canopy         Landing
```

The algorithm's job is to find the four transition events. Once found, every data point in the recording is labelled
with the phase it belongs to.

Not all phases need to be present. The algorithm is designed to detect transitions even when intermediate phases are
missing — for example, if the takeoff is too subtle to detect, the algorithm can jump directly from `BeforeTakeoff`
to `Freefall`. This resilience is described in detail in the [Missing Phase Detection](#missing-phase-detection)
section.

## Preprocessing

Before detection begins, the raw GPS data is cleaned to remove sensor noise and artefacts.

**Despiking** — GPS velocity readings occasionally contain sharp spikes caused by signal loss or multipath errors.
The algorithm detects these by comparing each data point against a linear interpolation of its neighbours. If the
deviation exceeds a configurable clip threshold, the spike is replaced with the interpolated value, effectively
stitching the signal back together.

```
 velocity                           velocity
    ▲                                  ▲
    │          × ◂── spike             │
    │         ╱ ╲                      │
    │        ╱   ╲                     │
    │       ○     ○──○──○              │       ○──○──○──○
    │      ╱                           │      /   ↑
    │  ○──○                            │  ○──○  interpolated value
    │                                  │
    │                                  │
    └─────────────────────▸ time       └─────────────────────────▸ time
      Before                           After
```

**Median Smoothing** — After despiking, velocities are smoothed using a component-wise median computed over a small
sliding window. The median filter preserves sharp transitions (unlike a moving average) while suppressing the
high-frequency jitter that is typical of consumer GPS devices.

## The Sliding Window

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

The total window size is derived from the three sub-regions: the maximum back window across all stage detectors, plus
one for the focus, plus the maximum validation window across all stage detectors.

## The Three-Step Detection Pipeline

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
transition. The trigger acts as a cheap filter: *does this point look like it could be a transition?* Most points
fail this check and are skipped immediately.

**Validation** — A forward-looking check that uses the validation window to confirm the trajectory after the
candidate point. For example, after a freefall trigger fires, validation checks that the jumper has actually
descended a significant distance over the validation window — consistent with real freefall rather than a transient
GPS spike. This step eliminates false positives that pass the trigger but don't correspond to real transitions.

**Inflection** — A backward-looking search over the back window to pinpoint the *exact* moment the transition
occurred. The trigger tells us *roughly where* a transition happened; the inflection step tells us *precisely when*.
It works by fitting two straight lines (a two-segment linear regression) to the signal in the back window, trying
every possible split point, and selecting the split that minimises the total fitting error. The optimal split point
is where the signal changes character — for example, where level flight gives way to rapid descent. If no clear
inflection is found, the focus point itself is used as the transition moment.

## Detection Lifecycle

The detection operates in two alternating modes: **steady state** and **sweep**.

### Steady State

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

### On Detection

When the pipeline detects a transition at the focus point, the window is split in two:

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

### The Sweep

Once the window refills after a detection, the focus doesn't return to its normal backtrack offset. Instead, it
starts at position zero — the very beginning of the window — and **sweeps** forward, position by position, up to
the backtrack offset. At each position, the full three-step pipeline runs.

```
  Sweep — focus advances from 0 towards backtrack offset:

  ┌───┬──────────────────────────┐
  │ ● │                          │    position 0: no detection
  └───┴──────────────────────────┘
  ┌─────┬───┬────────────────────┐
  │     │ ● │                    │    position 1: no detection
  └─────┴───┴────────────────────┘
  ┌───────┬───┬──────────────────┐
  │       │ ● │                  │    position 2: detection! ──▸ split & emit
  └───────┴───┴──────────────────┘
```

This sweep is what allows the algorithm to detect a new transition **as early as one point after the previous
detection**. If a freefall exit happens just one data point after takeoff, the sweep will find it because it starts
checking from position zero, although with limited data in the back window.

Once the sweep completes without finding a transition, the focus returns to the normal backtrack offset and the
algorithm re-enters steady state.

## Missing Phase Detection

The algorithm doesn't just look for the next expected transition — it also looks ahead to handle cases where a
phase might be too short, too noisy, or simply absent in the data.

For each current phase, the algorithm runs detection for multiple transition types, prioritised from most expected
to least expected:

| Current Phase    | Transitions Checked (in priority order)  |
|------------------|------------------------------------------|
| `BeforeTakeoff`  | Takeoff, then Freefall                   |
| `Climbing`       | Freefall, then Landing                   |
| `Freefall`       | Canopy, then Landing                     |
| `UnderCanopy`    | Landing                                  |
| `Landed`         | *(none — detection complete)*            |

The first detector that fires wins. This means:

- If takeoff is missed (e.g. the recording starts mid-climb), the algorithm detects freefall directly from
  `BeforeTakeoff`, skipping the `Climbing` phase entirely.
- If canopy deployment is missed (e.g. due to a very short canopy ride), the algorithm detects landing directly
  from `Freefall`, skipping `UnderCanopy`.
- If the aircraft comes back down without a jump, the algorithm can detect landing directly from `Climbing`.

This design makes the detection robust to real-world data where not every phase is cleanly represented.

## Per-Stage Detection

Each of the four transition detectors uses the same three-step pipeline but with different physical criteria
tailored to the characteristics of that particular transition.

**Takeoff** — Looks for the onset of climbing speed combined with horizontal movement, constrained to low
altitudes. The aircraft must be moving both upward and forward, and the altitude must be below a maximum threshold
to avoid confusing high-altitude manoeuvres with takeoff. Validation confirms that the altitude continues to rise
after the candidate point.

**Freefall Exit** — Looks for the onset of rapid vertical descent or a sudden burst of acceleration, constrained
to high altitudes. The trigger fires on either high downward speed or significant acceleration combined with
downward motion. Validation confirms that the jumper has actually descended a substantial distance over the
validation window — consistent with genuine freefall.

**Canopy Deployment** — Looks for the transition from freefall speeds to a moderate descent rate. The vertical
speed must have dropped below the freefall threshold, and the jumper must be decelerating. Validation checks that
the descent rate over the validation window falls between canopy speeds and freefall speeds — fast enough to still
be descending, but too slow to be in freefall.

**Landing** — Looks for sustained near-zero velocity in both vertical and horizontal axes, combined with low speed
variance across the window. Unlike the other detectors, landing doesn't use inflection detection — the transition
moment is simply the focus point where motion stops. Validation confirms that the speed remains stable across the
entire validation window, ruling out temporary pauses during canopy flight.

## Swoop Detection

A **swoop** (canopy piloting) is a high-performance landing: the pilot dives the canopy towards the ground, levels
off just above it, and glides across the landing area at high speed. Beesight computes a set of swoop metrics from
the canopy-to-landing portion of the flight, validated against the expected values from the reference recordings.

### The Swoop Profile

A clean swoop has a characteristic vertical-speed (vz) signature:

```
  vz (m/s)
    ▲
 21 │                    ╭──╮ ◂── dive point (bottom of the dive, max vz)
    │                   ╱  │
 15 │                  ╱   │
    │                 ╱    │
  8 │  ────────╮     ╱     ╰──╮        ◂── rollout: vz decays
  5 │  cruise  ╰───╯          ╰──╮  ╭── flare bump
  0 │                            ╰──╯  ╰──▸ landing (vz ≈ 0)
    └───────────────────────────────────────▸ time
      turn initiation ──────────────▸
```

The phases, in order:

| Phase | Definition | Example (jump_1.csv) |
|---|---|---|
| **Turn initiation** | vz starts rising from cruise — the pilot enters the dive | 192m MSL, vz ≈ 5 |
| **Dive point** (rollout start) | Maximum vz — the bottom of the dive | 105m MSL, vz 21.16 |
| **Rollout** | vz decays from the peak as the pilot recovers | 70m AGL |
| **Finished rollout** | vz falls below ~1.25 m/s — level flight begins | 39m MSL |
| **Landing** | Motion stops | 37m MSL |

### Why Naive Peak Detection Fails

The canopy-to-landing segment routinely contains **multiple local vz peaks**:

1. **Manoeuvre peaks before the swoop** — the pilot may perform high-speed dives during the canopy ride
   (e.g. 18.9 m/s at 288m AGL in the reference recording) that can rival or exceed the actual swoop peak.
2. **Flare bumps after the swoop** — small speed increases near touchdown (e.g. 6.4 m/s at 43m) caused by
   the flare and ground-effect noise.
3. **Noise near landing** — raw GPS vz oscillates (±1 m/s) once the pilot is on the ground.

A backward walk from landing that stops at the first local maximum locks onto the flare noise; a global
argmax locks onto the strongest manoeuvre even when the swoop is weaker. Neither anchor is reliable alone.

### The Detection Algorithm

The swoop detector follows the reference event semantics. It operates on the canopy-to-landing segment
(indices from the `Canopy` event to the `Landing` event) and works on **raw (despiked but unsmoothed)
speeds** — the reference implementation applies no smoothing, and median smoothing was observed to flatten
the true peak (21.58 → 21.16 m/s) and shift event indices by up to 5 samples.

```
                                 ╭──────────────────────╮
                                 │  Dive point          │
                                 │  (global argmax vz   │
         all rejected            │   below the cap)     │
        ┌───────────────────────►│                      │
        │                        ╰──────────────────────╯
  ╭────────────────────╮                   │  │  │
  │ no argmax below    │     ┌─────────────┘  │  └──────────────────┐
  │ the cap, or no     │     ▼                ▼                     ▼
  │ positive descent   │ ╭─────────────────╮ ╭─────────────────╮ ╭──────────────────╮
  ╰────────────────────╯ │ turn initiation │ │ rollout start   │ │ finished rollout │
                         │ (backward walk) │ │ (100ft walk)    │ │ (vz < 1.25 m/s)  │
                         ╰─────────────────╯ ╰─────────────────╯ ╰──────────────────╯
```

**Step 1 — Dive point.** The **global argmax** of vz below the altitude cap (`diveAltitudeCap`, ≈ 300 m /
1000 ft — the reference implementation's cap, which excludes high-altitude manoeuvres from consideration),
searched from the pattern entry forward. When no point below the cap has a positive descent, no swoop is
reported at all.

A swoop-gate experiment (candidate peaks + episode validation with a height floor and a continuity check)
was evaluated over the whole corpus and **discarded**: it rejected more real swoops than it repaired. The
known weakness of the argmax anchor — a manoeuvre stronger than the swoop breaking the downstream windows —
remains open, and any future fix must be validated against the reference recordings before wiring.

**Step 2 — Derive the remaining events** (reference event semantics):

- **Turn initiation** — from the dive point, walk backward to the first point below the pull threshold
  (25 mph ≈ 11.2 m/s; falling back to the minimum-descent point if the threshold is never crossed), then
  continue backward while the descent is still steepening (vz still increasing in forward time). The
  result is where the dive *began*. (The reference implementation's optional 500 ft altitude-loss and 30°/s
  turn-rate caps are omitted in this simplified walk; cruise oscillations stop the walk naturally.)
- **Rollout start** — from the first point below 100 ft AGL above the stop altitude, walk backward
  while vz is strictly increasing; the last index before the increase stops is the maximum-descent
  point — the rollout start. Starting the walk 100 ft above the stop (rather than at the landing
  point) is what bypasses the flare noise: the walk hits the swoop peak first and stops at the cruise
  dip between any manoeuvre and the swoop.
- **Finished rollout** — walk forward from rollout start until vz < 1.25 m/s (interpolated).
- **Max speeds** — scanned on raw data over the reference windows: max vertical and total speed over
  [turn initiation, landing), max horizontal over [rollout start, landing).

### Altitude Reference: AGL vs MSL

GPS loggers report **MSL** (Mean Sea Level) altitude; the reference expected values report event altitudes as
**AGL** (Above Ground Level), relative to a detected ground estimate (the average altitude around the
post-landing slow point, minus 2 m). Beesight computes metrics in MSL internally and converts to AGL
at the reporting boundary, subtracting the landing altitude as the ground estimate. All comparisons
against the reference expected values must be AGL-to-AGL — comparing MSL against AGL fails by the
drop-zone elevation (≈ 35 m in the reference data), which exceeds the test tolerance.

### Configuration

| Parameter | Default | Role |
|---|---|---|
| `pullThreshold` | 11.176 m/s | Descent rate below which the dive is considered over (turn-initiation walk) |
| `rolloutWindow` | 30.48 m | Height above the stop altitude where the rollout search starts |
| `carveRateThreshold` | 20 deg/s | Smoothed turn rate above which a sample belongs to the carve |
| `carveReliableSpeed` | 10 m/s | Minimum horizontal speed for the GPS heading to be trusted in the rotation computation |
| `carveGapSamples` | 5 | Maximum run of below-threshold samples tolerated inside the carve |
| `minHorizontalSpeed` | 2.0 m/s | Below this, heading is too noisy for rotation computation |
