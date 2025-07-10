package com.colofabrix.scala.beesight.csv

import java.time.*
import scala.compiletime.constValueTuple
import scala.compiletime.erasedValue
import scala.deriving.Mirror
import com.colofabrix.scala.stats.Peak

object Encoders {

  /**
   * Encodes a Product into a list of (String, String) pairs.
   */
  def productRowEncoder[A <: Product](prefix: String, value: A): List[(String, String)] =
    (0 until value.productArity).toList.flatMap { i =>
      val name    = value.productElementName(i)
      val aPrefix = if (prefix.isEmpty) "" else prefix + "_"
      value.productElement(i) match {
        case x: String         => List((aPrefix + name, x))
        case x: Double         => List((aPrefix + name, formatDouble(x, 3)))
        case x: Int            => List((aPrefix + name, x.toString))
        case x: OffsetDateTime => List((aPrefix + name, formatOffsetDateTime(x, 3)))
        case x: Instant        => List((aPrefix + name, formatInstant(x, 3)))
        case Peak.PositivePeak => List((aPrefix + name, "1"))
        case Peak.Stable       => List((aPrefix + name, "0"))
        case Peak.NegativePeak => List((aPrefix + name, "-1"))
        case _                 => List.empty
      }
    }

  /**
   * Encodes a Product into a list of (String, String) pairs with an empty prefix.
   */
  def productRowEncoder[A <: Product](value: A): List[(String, String)] =
    productRowEncoder("", value)

  /**
   * Formats a Double value to a string with the specified precision.
   */
  def formatDouble(value: Double, precision: Int): String =
    BigDecimal(value)
      .setScale(precision, BigDecimal.RoundingMode.HALF_UP)
      .toString()

  /**
   * Formats an OffsetDateTime value to a string with the specified precision.
   */
  def formatOffsetDateTime(value: OffsetDateTime, precision: Int): String =
    formatDouble(value.toInstant.toEpochMilli / 1000.0, precision)

  def formatInstant(value: Instant, precision: Int): String =
    formatOffsetDateTime(OffsetDateTime.ofInstant(value, ZoneOffset.UTC), precision)

  /**
   * Generates a list of (String, String) pairs for an empty product type.
   */
  inline def emptyProductRowEncoder[A](prefix: String)(using m: Mirror.ProductOf[A]): List[(String, String)] =
    val labels   = constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]]
    val keepMask = filterTypes[m.MirroredElemTypes]
    val aPrefix  = if (prefix.isEmpty) "" else prefix + "_"

    labels
      .zip(keepMask)
      .filter(_._2)
      .map { case (label, _) => (aPrefix + label, "") }

  private inline def isMathType[T]: Boolean =
    inline erasedValue[T] match {
      case _: Double         => true
      case _: Int            => true
      case _: String         => true
      case _: OffsetDateTime => true
      case _: Instant        => true
      case _: Peak           => true
      case _                 => false
    }

  private inline def filterTypes[Types <: Tuple]: List[Boolean] =
    inline erasedValue[Types] match {
      case _: (t *: ts)  => isMathType[t] :: filterTypes[ts]
      case _: EmptyTuple => Nil
    }

}
