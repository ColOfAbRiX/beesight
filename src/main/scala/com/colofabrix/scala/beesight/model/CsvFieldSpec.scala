package com.colofabrix.scala.beesight.model

import fs2.data.csv.*

/**
 * Describes a single CSV column field with its name, decode/encode logic, and whether it's required for format
 * detection
 *
 * @tparam A The parent data type (e.g. FlysightPoint)
 * @tparam T The decoded value type (e.g. Double, Int, OffsetDateTime)
 * @param name The column header name in the CSV file
 * @param decode Function to decode this field's value from a CSV row
 * @param encode Function to encode this field's value from the parent type to a string
 * @param required Whether this column must be present for format detection (optional fields like gpsFix are false)
 */
final case class CsvFieldSpec[A, T](
  name: String,
  decode: CsvRow[String] => DecoderResult[T],
  encode: A => String,
  required: Boolean = true,
)

object CsvFieldSpec {

  /**
   * Creates a required field spec. The column name is used for both decoding and detection
   */
  def required[A, T](name: String, encode: A => String)(using CellDecoder[T]): CsvFieldSpec[A, T] =
    CsvFieldSpec(name, row => row.as[T](name), encode)

  /**
   * Creates an optional field spec. The column may or may not be present in the CSV file
   */
  def optional[A, T](name: String, encode: A => String)(using CellDecoder[T]): CsvFieldSpec[A, Option[T]] =
    CsvFieldSpec(name, row => row.asOptional[T](name), encode, required = false)

}
