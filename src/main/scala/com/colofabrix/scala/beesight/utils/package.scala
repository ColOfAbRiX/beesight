package com.colofabrix.scala.beesight

import cats.Foldable
import cats.data.*
import com.colofabrix.scala.beesight.config.*
import com.colofabrix.scala.beesight.model.*

package object utils
  extends StreamBypass
    with StreamAccumulators
    with StreamUtils
    with IndexedStateUtils
