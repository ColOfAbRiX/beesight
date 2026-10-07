package com.colofabrix.scala.beesight.detection

/**
 * Stream processing utilities used by the detection engine: stateful accumulation and IndexedState helpers.
 */
package object utils
  extends StreamAccumulators
    with IndexedStateUtils
