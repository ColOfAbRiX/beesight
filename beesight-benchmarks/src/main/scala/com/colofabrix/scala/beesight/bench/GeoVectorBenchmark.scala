package com.colofabrix.scala.beesight.bench

import com.colofabrix.scala.beesight.model.GeoVector
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized

/**
 * JMH microbenchmarks for GeoVector operations — comparing sqrt+pow vs squared comparisons.
 *
 * Run from sbt:
 *   sbt "beesightDetection/Jmh/run -i 10 -wi 5 -f 2 -t 1 .*GeoVectorBenchmark.*"
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class GeoVectorBenchmark {

  private var vec: GeoVector    = uninitialized
  private var threshold: Double = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    vec = GeoVector(12.3, 45.6, 78.9)
    threshold = 100.0

  @Benchmark
  def magnitude_sqrt_pow(): Double =
    import java.lang.Math.*
    sqrt(pow(vec.east, 2) + pow(vec.north, 2) + pow(vec.vertical, 2))

  @Benchmark
  def magnitude_direct_mul(): Double =
    import java.lang.Math.*
    sqrt(vec.east * vec.east + vec.north * vec.north + vec.vertical * vec.vertical)

  @Benchmark
  def magnitudeSquared(): Double =
    vec.east * vec.east + vec.north * vec.north + vec.vertical * vec.vertical

  @Benchmark
  def comparison_via_magnitude(): Boolean =
    vec.magnitude < threshold

  @Benchmark
  def comparison_via_magnitudeSquared(): Boolean =
    val sq = vec.east * vec.east + vec.north * vec.north + vec.vertical * vec.vertical
    sq < threshold * threshold

  @Benchmark
  def horizontal_sqrt_pow(): Double =
    import java.lang.Math.*
    sqrt(pow(vec.east, 2) + pow(vec.north, 2))

  @Benchmark
  def horizontal_direct_mul(): Double =
    import java.lang.Math.*
    sqrt(vec.east * vec.east + vec.north * vec.north)

}
