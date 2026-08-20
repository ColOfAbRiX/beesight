package com.colofabrix.scala.beesight

import java.io.File
import scala.io.Source

object Tools {

  def simpleCsvRead[A](file: File)(f: (Map[String, Int], Array[String]) => A): List[A] =
    val lines =
      Source
        .fromFile(file)
        .getLines()
        .toList
        .filterNot(_.isBlank)

    val header =
      lines
        .head
        .split(",")
        .zipWithIndex
        .toMap

    lines
      .tail
      .filter(_.trim.nonEmpty)
      .map { line =>
        val cols = line.split(",", -1)
        f(header, cols)
      }

}
