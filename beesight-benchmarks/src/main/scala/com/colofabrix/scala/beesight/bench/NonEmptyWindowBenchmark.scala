package com.colofabrix.scala.beesight.bench

import com.colofabrix.scala.beesight.detection.collections.NonEmptyWindow
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*
import scala.compiletime.uninitialized

/**
 * JMH microbenchmarks for NonEmptyWindow operations — measuring the cost of buffer cloning.
 *
 * Run from sbt:
 *   sbt "beesightDetection/Jmh/run -i 10 -wi 5 -f 2 -t 1 .*NonEmptyWindowBenchmark.*"
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
class NonEmptyWindowBenchmark {

  private var filledWindow: NonEmptyWindow.NonEmptyFilled[Int] = uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    var w = NonEmptyWindow[Int](101, 0)
    for i <- 1 until 100 do
      w = w.enqueue(i)
    filledWindow = w.asInstanceOf[NonEmptyWindow.NonEmptyFilled[Int]]

  @Benchmark
  def pushReturning_clone(): (Int, NonEmptyWindow.NonEmptyFilled[Int]) =
    filledWindow.pushReturning(42)

  @Benchmark
  def enqueue_filled(): NonEmptyWindow.NonEmptyFilled[Int] =
    filledWindow.enqueue(42)

  @Benchmark
  def subwindowAt(): NonEmptyWindow.NonEmptyFilled[Int] =
    filledWindow.subwindowAt(10, 50, 40)

  @Benchmark
  def takeNewest(): NonEmptyWindow.NonEmptyFilled[Int] =
    filledWindow.takeNewest(5)

  @Benchmark
  def takeNewest_map(): NonEmptyWindow.NonEmptyFilled[Long] =
    filledWindow.takeNewest(5).map(_.toLong)

  @Benchmark
  def focusAt(): NonEmptyWindow.NonEmptyFilled[Int] =
    filledWindow.focusAt(30)

  @Benchmark
  def left(): Option[NonEmptyWindow.NonEmptyFilled[Int]] =
    filledWindow.left

  @Benchmark
  def right(): Option[NonEmptyWindow.NonEmptyFilled[Int]] =
    filledWindow.right

}
