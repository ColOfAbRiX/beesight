package com.colofabrix.scala.beesight.utils

private[utils] trait StreamBypass {

  /**
   * Typeclass for splitting an input into a processable part and a bypass part.
   */
  trait Splitter[I, T, B] {
    def split(a: I): (T, B)
  }

  /**
   * Typeclass for combining a processed result with the bypassed part into the final output.
   */
  trait Combiner[V, B, O] {
    def combine(processed: V, bypass: B): O
  }

  extension [F[_], I](self: fs2.Stream[F, I]) {

    /**
     * Processes a stream by splitting elements, processing one part through a pipe, and joining with the bypassed
     * part.
     *
     * This pattern is useful when you need to:
     *   - Extract a processable component `T` from input `I` while keeping a bypass component `B`
     *   - Process `T` through a pipe to produce `V`
     *   - Recombine processed `V` with the unchanged `B` to produce output `O`
     *
     * The bypass component `B` travels through unchanged, synchronized with the processed stream via `zip`.
     *
     * @param process Pipe that transforms the processable part `T` into `V`.
     * @param splitter Typeclass instance that splits input `I` into `(T, B)` (implicit).
     * @param combiner Typeclass instance that combines `V` and `B` into output `O` (implicit).
     * @tparam T The type of the component extracted for processing.
     * @tparam V The type of the processed result.
     * @tparam B The type of the bypass component (unchanged through processing).
     * @tparam O The type of the final output.
     * @return A stream of `O` elements.
     */
    def processBypass[T, V, B, O](
      process: fs2.Pipe[F, T, V],
    )(using
      splitter: Splitter[I, T, B],
      combiner: Combiner[V, B, O],
    ): fs2.Stream[F, O] =
      self
        .map(splitter.split)
        .through { splitStream =>
          process(splitStream.map(_._1))
            .zip(splitStream.map(_._2))
            .map { case (v, b) => combiner.combine(v, b) }
        }
  }

}
