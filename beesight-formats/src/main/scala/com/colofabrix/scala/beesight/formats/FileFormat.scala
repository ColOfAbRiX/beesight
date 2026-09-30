package com.colofabrix.scala.beesight.formats

/**
 * Supported CSV file formats for GPS flight data.
 *
 *   - `FlySight` — Flysight GPS logger CSV format with velocity columns
 *   - `Airlog` — Airlog One digital altimeter CSV format
 *   - `Dekunu` — Dekunu digital altimeter CSV format
 */
private[beesight] enum FileFormat {
  case FlySight
  case Airlog
  case Dekunu
  case Generic
}
