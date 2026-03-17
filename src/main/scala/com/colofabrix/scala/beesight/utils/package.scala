package com.colofabrix.scala.beesight

/**
 * Stream processing utilities: bypass piping, stateful accumulation, and IndexedState helpers.
 */
package object utils
  extends StreamBypass
    with IOConfigUtils
    with StreamAccumulators
    with IndexedStateUtils
