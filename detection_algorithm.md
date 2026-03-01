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
