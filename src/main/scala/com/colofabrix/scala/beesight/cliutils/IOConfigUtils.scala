package com.colofabrix.scala.beesight.cliutils

import cats.effect.implicits.given
import cats.implicits.given
import cats.syntax.all.*
import com.colofabrix.scala.beesight.IOConfig
import com.colofabrix.scala.beesight.config.Parallelism

/**
 * Provides utilities for IOConfig
 */
private[cliutils] trait IOConfigUtils {

  extension [A](self: List[A]) {

    def configTraverse[B](f: A => IOConfig[B]): IOConfig[List[B]] =
      IOConfig.ask.flatMap { config =>
        config.global.parallelism match {
          case Some(Parallelism(1)) => self.traverse(f)
          case Some(Parallelism(n)) => self.parTraverseN(n)(f)
          case None                 => self.parTraverse(f)
        }
      }

  }

}
