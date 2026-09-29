package com.colofabrix.scala.beesight.bench

import com.colofabrix.scala.beesight.collections.NonEmptyWindow
import com.colofabrix.scala.beesight.math.toArrayBy
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized

/**
 * JMH microbenchmarks establishing the performance baseline of the `Foldable[NonEmptyFilled]` instance
 * (`foldableNonEmptyFilled`) and its zero-allocation alternatives.
 *
 * The `Foldable` instance currently materialises a `Vector` on every fold, while the trait's own
 * `foldLeft`/`foldNewest` iterate the circular buffer directly. These benchmarks capture the cost of both
 * approaches before any optimisation, so regressions and improvements can be measured against a committed
 * baseline.
 *
 * Window sizes mirror the production configuration (`reference.conf`): smoothing 5, backtrack 10,
 * validation/preprocess 100.
 *
 * Run from sbt:
 *   sbt "beesightBenchmarks/Jmh/run -i 10 -wi 5 -f 2 -t 1 .*FoldableBenchmark.*"
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class FoldableBenchmark {

  @Param(Array("5", "10", "100"))
  var windowSize: Int = uninitialized

  private var window: NonEmptyWindow.NonEmptyFilled[Double] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    var w = NonEmptyWindow[Double](windowSize, 0.0)
    for i <- 1 until windowSize do
      w = w.enqueue(i.toDouble)
    window = w.asInstanceOf[NonEmptyWindow.NonEmptyFilled[Double]]

  /** Baseline: fold through the cats `Foldable` instance (allocates an intermediate Vector). */
  @Benchmark
  def foldable_foldLeft(): Double =
    summon[cats.Foldable[NonEmptyWindow.NonEmptyFilled]].foldLeft(window, 0.0)(_ + _)

  /** Baseline: foldRight through the cats `Foldable` instance (allocates an intermediate Vector). */
  @Benchmark
  def foldable_foldRight(): Double =
    summon[cats.Foldable[NonEmptyWindow.NonEmptyFilled]]
      .foldRight(window, cats.Eval.now(0.0))((a, acc) => acc.map(_ + a)).value

  /** Zero-allocation alternative: fold directly over the circular buffer via the trait. */
  @Benchmark
  def direct_foldLeft(): Double =
    window.foldLeft(0.0)(_ + _)

  /** Zero-allocation alternative: fold over the newest elements via the trait. */
  @Benchmark
  def foldNewest(): Double =
    window.foldNewest(windowSize)(0.0)(_ + _)

  /** Baseline: `toArrayBy` extraction through the `Foldable` instance (used by `StatsOps`/`WindowedMetrics`). */
  @Benchmark
  def toArrayBy_foldable(): Array[Double] =
    window.toArrayBy(identity)

  /** Baseline: median over the extracted array (mirrors `StatsOps.medianBy` used by jump-event handlers). */
  @Benchmark
  def median_overArray(): Double =
    com.colofabrix.scala.beesight.math.Median[Double].median(window.toArrayBy(identity))

}
