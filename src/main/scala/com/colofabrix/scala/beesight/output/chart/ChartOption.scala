package com.colofabrix.scala.beesight.output.chart

import com.colofabrix.scala.beesight.detection.model.*
import io.circe.Json
import io.circe.syntax.*
import java.time.{ Duration, Instant }

/**
 * Builds the ECharts option of the chart: layout, axes, legend, zoom and the series of the traces.
 */
private[chart] object ChartOption {

  def option[A](
    state: ChartState[A],
    metrics: Option[Vector[FlightMetric]],
    swoop: Option[SwoopData],
  ): Json =
    val t0      = state.times.headOption.getOrElse(Instant.EPOCH)
    val xs      = state.times.map(t => Duration.between(t0, t).toMillis / 1000.0)
    val traces  = ChartSeries.traceSpecs(state, metrics)
    val markers = ChartSeries.markerSpecs(state.stages, swoop)
    val xMax    = xs.lastOption.map(last => last + math.max(last * 0.02, 0.1)).getOrElse(1.0)

    Json.obj(
      "animation" -> Json.False,
      "tooltip" -> Json.obj(
        "trigger" -> "axis".asJson,
        "axisPointer" -> Json.obj("type" -> "cross".asJson, "label" -> Json.obj("show" -> Json.False)),
        "extraCssText" -> "font-size: 11px; padding: 4px 8px;".asJson,
      ),
      "legend"    -> ChartSeries.legendJson(traces),
      "grid"      -> Json.obj(
        "left" -> (traces.count(_.on) * 60).asJson,
        "right" -> 30.asJson,
        "top" -> 64.asJson,
        "bottom" -> 36.asJson,
        "containLabel" -> Json.False,
      ),
      "toolbox"   -> Json.obj("show" -> Json.False),
      "xAxis" -> Json.arr(
        Json.obj(
          "type" -> "value".asJson,
          "min" -> 0.0.asJson,
          "max" -> xMax.asJson,
          "axisLabel" -> Json.obj("show" -> Json.False),
          "axisTick" -> Json.obj("show" -> Json.False),
          "axisPointer" -> Json.obj("label" -> Json.obj("show" -> Json.False)),
        ),
        Json.obj(
          "type" -> "value".asJson,
          "position" -> "bottom".asJson,
          "min" -> 0.0.asJson,
          "max" -> xMax.asJson,
          "axisLabel" -> Json.obj("color" -> "#555".asJson, "showMinLabel" -> Json.False),
          "axisTick" -> Json.obj("show" -> Json.False),
          "axisPointer" -> Json.obj("label" -> Json.obj("show" -> Json.False)),
        ),
      ),
      "yAxis" -> Json.arr(
        traces.map { trace =>
          val bound = axisBounds(trace.values)
          Json.obj(
            "type" -> "value".asJson,
            "name" -> trace.unit.asJson,
            "nameLocation" -> "start".asJson,
            "position" -> "left".asJson,
            "offset" -> 0.asJson,
            "show" -> trace.on.asJson,
            "min" -> bound._1.asJson,
            "max" -> bound._2.asJson,
            "axisLine" -> Json.obj("onZero" -> Json.False, "lineStyle" -> Json.obj("color" -> trace.color.asJson)),
            "axisLabel" -> Json.obj("color" -> trace.color.asJson),
            "axisTick" -> Json.obj("show" -> Json.False),
            "axisPointer" -> Json.obj("label" -> Json.obj("show" -> Json.False)),
            "nameTextStyle" -> Json.obj("color" -> trace.color.asJson, "fontSize" -> 10.asJson),
            "splitLine" -> Json.obj("show" -> Json.False),
          )
        }*
      ),
      "dataZoom" -> Json.arr(
        Json.obj(
          "type" -> "inside".asJson,
          "xAxisIndex" -> Json.arr(0.asJson, 1.asJson),
          "moveOnMouseMove" -> Json.False,
          "moveOnMouseWheel" -> "shift".asJson,
        ),
      ),
      "brush" -> Json.obj(
        "xAxisIndex"    -> Json.arr(0.asJson),
        "brushMode"     -> "single".asJson,
        "transformable" -> Json.False,
        "brushStyle"    -> Json.obj(
          "color"       -> "rgba(85, 85, 85, 0.08)".asJson,
          "borderColor" -> "#555".asJson,
          "borderWidth" -> 1.asJson,
        ),
      ),
      "series"   -> Json.fromValues(ChartSeries.seriesJson(traces, markers, xs, t0)),
    )

  def chartDefaultsJson[A](state: ChartState[A], metrics: Option[Vector[FlightMetric]]): Json =
    val t0      = state.times.headOption.getOrElse(Instant.EPOCH)
    val xs      = state.times.map(t => Duration.between(t0, t).toMillis / 1000.0)
    val traced  = ChartSeries.traceSpecs(state, metrics).find(_.unit == "m")
    val exitX   = state.stages.freefall.map(p => Duration.between(t0, p.time).toMillis / 1000.0).getOrElse(0.0)
    val landing = state.stages.landing.map(p => Duration.between(t0, p.time).toMillis / 1000.0)
    val groundY = landing
      .flatMap(lx => traced.map(_.values).flatMap(vals => xs.zip(vals).collectFirst { case (`lx`, y) => y }))
      .getOrElse(0.0)

    Json.obj("exit" -> exitX.asJson, "ground" -> groundY.asJson)

  private def axisBounds(values: Seq[Double]): (Double, Double) =
    if values.isEmpty then (0.0, 1.0)
    else
      val min = values.min
      val max = values.max
      val pad = math.max((max - min) * 0.02, 0.1)
      (min - pad, max + pad)

}
