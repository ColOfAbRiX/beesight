package com.colofabrix.scala.beesight.bench

import com.colofabrix.scala.beesight.metrics.computations.WindowedMetrics
import com.colofabrix.scala.beesight.model.{ GeoVector, IndexedPoint }
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized

/**
 * JMH microbenchmarks for the per-point windowed metrics workload, mirroring the calls made by
 * `FlightMetricsCalculator.metric` for every input point: `acceleration`, `totalSpeedRate`, `courseRate`,
 * and `diveRate` over the same window.
 *
 * This is the reference point for the "share relativeSeconds across windowed metrics" optimisation:
 * before the change each call re-extracts the seconds array; after, it is extracted once.
 *
 * Run from sbt:
 *   sbt "beesightBenchmarks/Jmh/run -i 10 -wi 5 -f 2 -t 1 .*WindowedMetricsBenchmark.*"
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class WindowedMetricsBenchmark {

  @Param(Array("9", "21", "101"))
  var windowSize: Int = uninitialized

  private var window: Vector[IndexedPoint] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    val start = Instant.parse("2026-01-01T00:00:00Z")
    window = (0 until windowSize).toVector.map { i =>
      IndexedPoint(
        idx = i.toLong,
        time = start.plusMillis(i * 100L),
        altitude = 1000.0 + i * 5.0,
        speed = GeoVector(20.0 + i * 0.1, 3.0 + i * 0.05, 40.0 - i * 0.2),
      )
    }

  /** Production path: all four metrics from one shared seconds extraction, as in `FlightMetricsCalculator`. */
  @Benchmark
  def instanceWorkload(): (Double, Double, Double, Double) =
    val wm = WindowedMetrics(window)
    (wm.acceleration.vertical, wm.totalSpeedRate, wm.courseRate, wm.diveRate)

  /** Per-metric path: each metric extracts its own seconds array. */
  @Benchmark
  def fullMetricWorkload(): (Double, Double, Double, Double) =
    (
      WindowedMetrics.acceleration(window).vertical,
      WindowedMetrics.totalSpeedRate(window),
      WindowedMetrics.courseRate(window),
      WindowedMetrics.diveRate(window),
    )

  /** Cost of a single metric call alone, for reference. */
  @Benchmark
  def accelerationOnly(): Double =
    WindowedMetrics.acceleration(window).vertical

}
