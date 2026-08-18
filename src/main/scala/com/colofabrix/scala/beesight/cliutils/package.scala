package com.colofabrix.scala.beesight

/**
 * Stream processing utilities for the CLI: bypass piping and IOConfig helpers.
 */
package object cliutils
  extends StreamBypass
    with IOConfigUtils
