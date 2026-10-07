package com.colofabrix.scala.beesight.formats

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
final private[beesight] case class CsvFieldSpec[A, T](
  name: String,
  decode: CsvRow[String] => DecoderResult[T],
  encode: A => String,
  required: Boolean = true,
)

/**
 * Builder of [[CsvFieldSpec]] columns for a CSV-backed point type.
 */
final class CsvSpecBuilder[A] {

  /**
   * Creates a required field spec. The column name is used for both decoding and detection
   */
  def required[T](name: String, encode: A => String)(using CellDecoder[T]): CsvFieldSpec[A, T] =
    CsvFieldSpec(name, row => row.as[T](name), encode)

  /**
   * Creates an optional field spec. The column may or may not be present in the CSV file; a missing column, as well as
   * an empty cell, decodes to None
   */
  def optional[T](name: String, encode: A => String)(using CellDecoder[T]): CsvFieldSpec[A, Option[T]] =
    CsvFieldSpec(name, row => row.asOptional[T](name, missing = _ => Right(None)), encode, required = false)

  /**
   * Creates a lenient field spec. The column participates in format detection, but a missing column, an empty cell, or
   * a cell that fails to decode yields None instead of failing the whole row
   */
  def lenient[T](name: String, encode: A => String)(using CellDecoder[T]): CsvFieldSpec[A, Option[T]] =
    CsvFieldSpec(name, row => Right(row.asOptional[T](name).toOption.flatten), encode, required = true)

}

object CsvSpecBuilder {

  /** Creates a new builder for the given point type. */
  def forFormat[A]: CsvSpecBuilder[A] = new CsvSpecBuilder[A]

}
