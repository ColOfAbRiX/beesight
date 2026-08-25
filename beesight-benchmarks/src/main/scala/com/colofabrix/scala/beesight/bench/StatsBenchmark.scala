package com.colofabrix.scala.beesight.bench

import com.colofabrix.scala.beesight.collections.NonEmptyWindow
import com.colofabrix.scala.beesight.math.Stats
import com.colofabrix.scala.beesight.model.GeoVector
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

/**
 * JMH microbenchmarks for the Stats object — the hottest math operations in the detection pipeline.
 *
 * Run from sbt:
 *   sbt "beesightDetection/Jmh/run -i 10 -wi 5 -f 2 -t 1 .*StatsBenchmark.*"
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class StatsBenchmark {

  //  medianBy  //

  private var geoWindow: NonEmptyWindow.NonEmptyFilled[GeoVector] = uninitialized

  @Setup(Level.Trial)
  def setupMedian(): Unit =
    val rng   = new java.util.Random(42)
    val first = GeoVector(rng.nextDouble() * 100, rng.nextDouble() * 100, rng.nextDouble() * 100)
    var w     = NonEmptyWindow[GeoVector](5, first)

    for _ <- 0 until 4 do 
      w = w.enqueue(GeoVector(rng.nextDouble() * 100, rng.nextDouble() * 100, rng.nextDouble() * 100))

    geoWindow = w.asInstanceOf[NonEmptyWindow.NonEmptyFilled[GeoVector]]

  @Benchmark
  def medianBy_geoVector(): GeoVector =
    Stats.medianBy(geoWindow)(identity)

  //  stdDevBy  //

  private var doubleWindow: NonEmptyWindow.NonEmptyFilled[Double] = uninitialized

  @Setup(Level.Trial)
  def setupStdDev(): Unit =
    val rng   = new java.util.Random(42)
    val first = rng.nextDouble() * 100.0
    var w     = NonEmptyWindow[Double](20, first)

    for _ <- 0 until 19 do 
      w = w.enqueue(rng.nextDouble() * 100.0)
    
    doubleWindow = w.asInstanceOf[NonEmptyWindow.NonEmptyFilled[Double]]

  @Benchmark
  def stdDevBy_double(): Double =
    Stats.stdDevBy(doubleWindow)(identity)

  //  findInflectionBy  //

  private var inflectionWindow: NonEmptyWindow.NonEmptyFilled[Double] = uninitialized

  @Setup(Level.Trial)
  def setupInflection(): Unit =
    val rng = new java.util.Random(42)
    // Create a signal with a clear inflection: flat then rising
    val first = 10.0
    var w     = NonEmptyWindow[Double](10, first)
    for _ <- 1 until 5 do
      w = w.enqueue(10.0 + rng.nextDouble() * 0.5)
    for i <- 5 until 10 do
      w = w.enqueue(10.0 + (i - 5) * 5.0 + rng.nextDouble() * 0.5)
    inflectionWindow = w.asInstanceOf[NonEmptyWindow.NonEmptyFilled[Double]]

  @Benchmark
  def findInflectionBy_double(): Option[Int] =
    Stats.findInflectionBy(inflectionWindow)(identity)
    
}
