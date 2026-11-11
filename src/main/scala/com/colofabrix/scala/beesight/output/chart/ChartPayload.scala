package com.colofabrix.scala.beesight.output.chart

import io.circe.Json
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.math.MathContext
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.{ GZIPInputStream, GZIPOutputStream }

/**
 * Prepares the ECharts option for embedding in the page: rounded numbers, compressed and base64 encoded.
 */
private[chart] object ChartPayload {

  def roundNumbers(json: Json): Json =
    json.asNumber match
      case Some(_) =>
        val value = json.asNumber.map(_.toDouble).getOrElse(0.0)
        Json.fromDoubleOrNull(BigDecimal(value, new MathContext(6)).toDouble)
      case None =>
        json.asArray
          .map(arr => Json.fromValues(arr.map(roundNumbers)))
          .orElse(json.asObject.map(obj => Json.fromFields(obj.toList.map { case (key, value) => key -> roundNumbers(value) })))
          .getOrElse(json)

  def gzipBase64(text: String): String =
    val buffer = new ByteArrayOutputStream()
    val gzip   = new GZIPOutputStream(buffer)
    gzip.write(text.getBytes(StandardCharsets.UTF_8))
    gzip.close()
    Base64.getEncoder.encodeToString(buffer.toByteArray)

  def gunzipBase64(encoded: String): String =
    val gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder.decode(encoded)))
    val out  = Iterator.continually(gzip.read).takeWhile(_ != -1).map(_.toByte).toArray
    new String(out, StandardCharsets.UTF_8)

}
