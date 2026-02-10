package com.colofabrix.scala.beesight

import cats.data.State
import com.colofabrix.scala.beesight.collections.NEW
import monocle.Iso

package object utils {

  extension [F[_], A](self: fs2.Stream[F, A]) {

    /**
     * Like `mapAccumulate`, but the initial state is computed from the first element of the stream.
     *
     * This is useful when the initial state depends on the first element, rather than being a fixed value. The first
     * element is processed by the `init` function to produce both the initial state and the first output. Subsequent
     * elements are processed by `f`, which receives the current state and element, returning the new state and output.
     *
     * Unlike standard `mapAccumulate`, this emits `(S, B)` tuples, exposing both the state and output for each element.
     *
     * @param init Function that receives the first element and returns the initial state and first output.
     * @param f Function that receives the current state and element, returning the new state and output.
     * @tparam S The type of the state.
     * @tparam B The type of the output.
     * @return A stream of `(S, B)` tuples, one for each input element.
     */
    def mapAccumulate1[S, B](init: A => (S, B))(f: (S, A) => (S, B)): fs2.Stream[F, (S, B)] =
      self
        .pull
        .uncons1
        .flatMap {
          case Some((head, tail)) =>
            val (initial, first) = init(head)
            val firstStream      = fs2.Stream.emit((initial, first))
            val tailStream       = tail.mapAccumulate(initial)(f)
            (firstStream ++ tailStream).pull.echo
          case None =>
            fs2.Pull.done
        }
        .stream

    /**
     * Creates a sliding window pipe that fills a window and then emits filled windows with their oldest pushed-out
     * elements.
     *
     * This pipe buffers elements until the window reaches capacity. Once filled, each new element pushes out the oldest
     * element, and the pipe emits a tuple of the filled window and the pushed-out element.
     *
     * During the filling phase, no elements are emitted. Once the window is full, every subsequent element causes an
     * emission.
     *
     * @param windowSize The capacity of the sliding window.
     * @return A pipe that transforms a stream of elements into a stream of `(FilledWindow, OldestElement)` tuples.
     */
    def fillWindow(windowSize: Int): fs2.Pipe[F, A, (NEW[A], A)] =
      _.mapAccumulate1(first => (NEW(windowSize, first), None)) {
        case (window: NEW.NonEmptyFilling[A], point) =>
          window.push(point).swap
        case (window: NEW.NonEmptyFilled[A], point) =>
          window.push(point).swap
      }
        .collect {
          case (window: NEW.NonEmptyFilled[A], Some(point)) =>
            (window, point)
        }

    /**
     * Processes a stream by splitting elements, processing one part through a pipe, and joining with the bypassed
     * part.
     *
     * This pattern is useful when you need to:
     *   - Extract a processable component `B` from input `A` while keeping a bypass component `C`
     *   - Process `B` through a pipe to produce `D`
     *   - Recombine `D` with the unchanged `C` to produce output `E`
     *
     * The bypass component `C` travels through unchanged, synchronized with the processed stream via `zip`.
     *
     * @param combine Isomorphism between output `E` and `(D, C)` - used to join processed and bypassed parts.
     * @param split Isomorphism between input `A` and `(B, C)` - used to split into processable and bypass parts (implicit).
     * @param process Pipe that transforms the processable part `B` into `D` (implicit).
     * @tparam B The type of the component to be processed.
     * @tparam C The type of the bypass component (unchanged through processing).
     * @tparam D The type of the processed result.
     * @tparam E The type of the final output.
     * @return A pipe that transforms `A` into `E`.
     */
    def processBypass[B, C, D, E](
      combine: Iso[E, (D, C)],
    )(using
      split: Iso[A, (B, C)],
      process: fs2.Pipe[F, B, D],
    ): fs2.Pipe[F, A, E] =
      stream =>
        stream
          .map(split.get)
          .through { splitStream =>
            process(splitStream.map(_._1))
              .zip(splitStream.map(_._2))
              .map { case (d, c) => combine.reverseGet((d, c)) }
          }

    /**
     * Stateful stream transformation with optional output filtering.
     *
     * Similar to `mapAccumulate`, but uses `State` monad for state transitions and filters outputs using `Option`. Only
     * `Some` values are emitted; `None` values are discarded.
     *
     * @param init The initial state.
     * @param f A function that returns a `State` computation producing an optional output.
     * @tparam S The type of the state.
     * @tparam B The type of the output.
     * @return A stream of `B` values, with `None` results filtered out.
     */
    def mapStateCollect[S, B](init: S)(f: A => State[S, Option[B]]): fs2.Stream[F, B] =
      self
        .mapAccumulate1[S, Option[B]](a => f(a).run(init).value) { (s, a) =>
          f(a).run(s).value
        }
        .collect {
          case (_, Some(output)) => output
        }

    /**
     * Stateful stream transformation where the initial state is computed from the first element, with optional output
     * filtering.
     *
     * Combines `mapAccumulate1` semantics (first element initializes state) with `State` monad processing and `Option`
     * filtering. The first element is processed by `init` to produce initial state and optional first output.
     * Subsequent elements use `f`. Only `Some` outputs are emitted.
     *
     * @param init Function that receives the first element and returns the initial state and optional first output.
     * @param f A function that returns a `State` computation producing an optional output.
     * @tparam S The type of the state.
     * @tparam B The type of the output.
     * @return A stream of `B` values, with `None` results filtered out.
     */
    def mapStateCollect1[S, B](init: A => (S, Option[B]))(f: A => State[S, Option[B]]): fs2.Stream[F, B] =
      self
        .mapAccumulate1[S, Option[B]](init) { (s, a) =>
          f(a).run(s).value
        }
        .collect {
          case (_, Some(output)) => output
        }

  }

}
