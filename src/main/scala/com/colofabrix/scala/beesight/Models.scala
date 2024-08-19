package com.colofabrix.scala.beesight

import java.time.*

/**
 * Flysight Row
 *
 * @param time Time in ISO8601 format
 * @param lat Latitude (degrees)
 * @param lon Longitude (degrees)
 * @param hMSL Height above sea level (m)
 * @param velN Velocity north (m/s)
 * @param velE Velocity east (m/s)
 * @param velD Velocity down (m/s)
 * @param hAcc Horizontal accuracy (m)
 * @param vAcc Vertical accuracy (m)
 * @param sAcc Speed accuracy (m/s)
 * @param heading Heading
 * @param cAcc Not sure
 * @param gpsFix GPS fix type (3 = 3D)
 * @param numSV Number of satellites used in fix
 */
final case class FlysightRow(
  time: OffsetDateTime,
  lat: Double,
  lon: Double,
  hMSL: Double,
  velN: Double,
  velE: Double,
  velD: Double,
  hAcc: Double,
  vAcc: Double,
  sAcc: Double,
  heading: Double,
  cAcc: Double,
  gpsFix: Int,
  numSV: Int,
  more: List[String] = List.empty
)
