package com.colofabrix.scala.stats

import breeze.linalg.*
import breeze.stats.*
import cats.effect.IO
import fs2.*
import java.lang.Math.*
import scala.collection.immutable.Queue

class CumSum(lag: Int):

  private final case class ScanState(
    window: Queue[Double] = Queue.empty,
    cumsum: Double = 0.0
  )

  private val safeLag: Int =
    Math.max(lag, 1)

  def detectStats[A](f: A => Double): Pipe[IO, A, String] =
    data =>
      data
        .scan(ScanState()) {
          case (state @ ScanState(queue, _), a) if queue.length + 1 < safeLag =>
            state.copy(window = queue.enqueue(f(a)))
        }
        .collect {
          case ScanState(_, stats, Some(a)) => (a, stats.peak, stats)
        }
