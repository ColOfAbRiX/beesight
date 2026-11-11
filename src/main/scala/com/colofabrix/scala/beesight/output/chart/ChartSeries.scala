package com.colofabrix.scala.beesight.output.chart

import com.colofabrix.scala.beesight.detection.model.*
import io.circe.Json
import io.circe.syntax.*
import java.time.{ Duration, Instant }

/**
 * Builds the series of the chart: one line for each trace and one marker for each detected point.
 */
private[chart] object ChartSeries {

  final private[chart] case class TraceSpec(
    name: String,
    values: Vector[Double],
    unit: String,
    color: String,
    on: Boolean,
  )

  final private[chart] case class MarkerSpec(
    name: String,
    time: Instant,
    altitude: Double,
    symbol: String,
    color: String,
    groups: Set[String],
  )

  private val colors = Vector(
    "rgb(31, 119, 180)",
    "rgb(255, 127, 14)",
    "rgb(44, 160, 44)",
    "rgb(214, 39, 40)",
    "rgb(148, 103, 189)",
    "rgb(140, 86, 75)",
    "rgb(227, 119, 194)",
    "rgb(127, 127, 127)",
    "rgb(188, 189, 34)",
    "rgb(23, 190, 207)",
    "rgb(102, 51, 153)",
    "rgb(255, 215, 0)",
    "rgb(0, 128, 128)",
  )

  def traceSpecs[A](state: ChartState[A], metrics: Option[Vector[FlightMetric]]): List[TraceSpec] =
    metrics match
      case Some(m) =>
        val pick = (f: FlightMetric => Double) => m.map(f)
        List(
          TraceSpec("Altitude (m)", pick(_.altitude), "m", colors(0), on = true),
          TraceSpec("Vertical speed (m/s)", pick(_.verticalSpeed), "m/s", colors(1), on = true),
          TraceSpec("Horizontal speed (m/s)", pick(_.horizontalSpeed), "m/s", colors(2), on = false),
          TraceSpec("Total speed (m/s)", pick(_.totalSpeed), "m/s", colors(3), on = false),
          TraceSpec("Glide ratio", pick(_.glideRatio), "ratio / coeff", colors(4), on = false),
          TraceSpec("Lift coefficient", pick(_.liftCoefficient), "ratio / coeff", colors(5), on = false),
          TraceSpec("Drag coefficient", pick(_.dragCoefficient), "ratio / coeff", colors(6), on = false),
          TraceSpec("Dive angle (deg)", pick(_.diveAngle), "deg", colors(7), on = false),
          TraceSpec("Dive rate (deg/s)", pick(_.diveRate), "deg/s", colors(8), on = false),
          TraceSpec("Turn rate (deg/s)", pick(_.turnRate), "deg/s", colors(9), on = false),
          TraceSpec("Acceleration H (m/s²)", pick(_.acceleration.horizontal), "m/s²", colors(10), on = false),
          TraceSpec("Acceleration V (m/s²)", pick(_.acceleration.vertical), "m/s²", colors(11), on = false),
          TraceSpec("Total energy (J/kg)", pick(_.totalEnergy), "J/kg", colors(12), on = false),
        )
      case None =>
        List(
          TraceSpec("Altitude (m)", state.altitudes, "m", colors(0), on = true),
          TraceSpec("Vertical speed (m/s)", state.velDs, "m/s", colors(1), on = true),
        )

  def markerSpecs(stages: FlightEvents, swoop: Option[SwoopData]): List[MarkerSpec] =
    val jump = List(
      stages.takeoff.map(p => MarkerSpec("Takeoff", p.time, p.altitude, "diamond", "rgb(44, 160, 44)", Set("jump"))),
      stages.freefall.map(p => MarkerSpec("Exit", p.time, p.altitude, "diamond", "rgb(214, 39, 40)", Set("jump"))),
      stages.canopy.map(p => MarkerSpec("Canopy", p.time, p.altitude, "diamond", "rgb(148, 103, 189)", Set("jump"))),
      stages.landing.map(p => MarkerSpec("Landing", p.time, p.altitude, "diamond", "rgb(140, 86, 75)", Set("jump", "swoop"))),
    ).flatten

    val swoopMarkers = swoop.toList.flatMap { data =>
      val msl = (p: EventPoint) => p.altitude + data.ground.value
      List(
        MarkerSpec("Dive point", data.divePoint.time, msl(data.divePoint), "pin", "rgb(255, 127, 14)", Set("swoop")),
        MarkerSpec(
          "Turn initiation",
          data.turnInitiation.time,
          msl(data.turnInitiation),
          "pin",
          "rgb(214, 39, 40)",
          Set("swoop"),
        ),
        MarkerSpec(
          "Rollout start",
          data.rolloutStart.time,
          msl(data.rolloutStart),
          "pin",
          "rgb(44, 160, 44)",
          Set("swoop"),
        ),
        MarkerSpec(
          "Bottom of turn",
          data.bottomOfTurn.time,
          msl(data.bottomOfTurn),
          "pin",
          "rgb(148, 103, 189)",
          Set("swoop"),
        ),
      )
    }

    jump ++ swoopMarkers

  def legendJson(traces: List[TraceSpec]): Json =
    Json.obj(
      "data"     -> Json.fromValues(traces.map(_.name.asJson)),
      "selected" -> Json.fromFields(traces.map(t => t.name -> t.on.asJson)),
    )

  def seriesJson(
    traces: List[TraceSpec],
    markers: List[MarkerSpec],
    xs: Vector[Double],
    t0: Instant,
  ): List[Json] =
    traces.zipWithIndex.map { case (t, index) =>
      val data =
        if t.unit == "m" then altitudeDataJson(t, markers, xs, t0)
        else Json.fromValues(xs.zip(t.values).map((x, y) => Json.arr(x.asJson, y.asJson)))

      Json.obj(
        "name"       -> t.name.asJson,
        "type"       -> "line".asJson,
        "symbol"     -> "none".asJson,
        "yAxisIndex" -> index.asJson,
        "color"      -> t.color.asJson,
        "itemStyle"  -> Json.obj("color" -> t.color.asJson),
        "lineStyle"  -> Json.obj("color" -> t.color.asJson, "width" -> 1.5.asJson),
        "data"       -> data,
      )
    }

  private def altitudeDataJson(
    trace: TraceSpec,
    markers: List[MarkerSpec],
    xs: Vector[Double],
    t0: Instant,
  ): Json =
    val pairs = xs.zip(trace.values)
    val lines = pairs.map((x, y) => x -> Json.arr(x.asJson, y.asJson))
    val marks = markers.map { m =>
      val x = Duration.between(t0, m.time).toMillis / 1000.0
      val y = pairs.collectFirst { case (`x`, altitude) => altitude }.getOrElse(m.altitude)
      x -> Json.obj(
        "name"       -> m.name.asJson,
        "value"      -> Json.arr(x.asJson, y.asJson),
        "symbol"     -> m.symbol.asJson,
        "symbolSize" -> 12.asJson,
        "itemStyle"  -> Json.obj("color" -> m.color.asJson),
        "groups"     -> Json.fromValues(m.groups.toList.sorted.map(_.asJson)),
        "label"      -> Json.obj("show" -> Json.True, "formatter" -> "{b}".asJson, "position" -> "bottom".asJson),
      )
    }

    Json.fromValues((lines ++ marks).sortBy(_._1).map(_._2))

}
