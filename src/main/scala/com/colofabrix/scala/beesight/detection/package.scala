package com.colofabrix.scala.beesight

package object detection {

  extension [A](self: Option[A]) {

    def getOrFalse(f: A => Boolean): Boolean =
      self match {
        case Some(value) => f(value)
        case None        => false
      }

    def getOrTrue(f: A => Boolean): Boolean =
      self match {
        case Some(value) => f(value)
        case None        => true
      }

  }

}

