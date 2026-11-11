package com.colofabrix.scala.beesight.output.chart

import com.colofabrix.scala.beesight.detection.model.*

/**
 * Renders the chart HTML: one figure with all the available traces, a legend to toggle them, and the detected points
 * marked on the plot.
 */
private[beesight] object ChartHtml {

  private def resource(name: String): String = {
    val stream = Option(getClass.getResourceAsStream(name))
      .getOrElse(sys.error(s"$name is missing from the classpath"))
    try scala.io.Source.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()
  }

  private lazy val echartsLib   = resource("/chart/echarts.min.js")
  private lazy val chartInitJs = resource("/chart/chart-init.js")

  def build[A](
    state: ChartState[A],
    metrics: Option[Vector[FlightMetric]],
    swoop: Option[SwoopData],
    fileName: String,
  ): String =
    s"""<!DOCTYPE html>
       |<html>
       |<head>
       |  <meta charset="utf-8">
       |  <title>Flight Analysis - ${fileName}</title>
       |  <style>
       |    body { margin: 0; padding: 10px; font-family: sans-serif; background: #f5f5f5; }
       |    h1 { color: #333; font-size: 20px; line-height: 24px; margin: 0 0 6px; }
       |    #toolbar { display: flex; align-items: center; gap: 16px; margin-bottom: 8px; }
       |    #toolbar label { font-size: 13px; color: #333; display: flex; align-items: center; gap: 4px; }
       |    #toolbar select { font-size: 13px; font-family: inherit; }
       |    .pill { display: inline-block; padding: 4px 12px; border: none; border-radius: 12px; color: white; font-size: 13px; cursor: pointer; font-family: inherit; }
       |    .mode-z { background: #93b7d4; }
       |    .mode-z.active { background: #1f77b4; }
       |    .mode-m { background: #f5c08a; }
       |    .mode-m.active { background: #ff7f0e; }
       |    .set { background: #7f8c8d; }
       |    .set.armed { background: #c0392b; }
       |    #chart { width: 100%; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
       |    .info table { border-collapse: collapse; margin-top: 8px; }
       |    .info th, .info td { border: 1px solid #e3e3e3; padding: 6px 12px; font-size: 13px; text-align: right; color: #333; }
       |    .info th { background: #f7f7f7; font-weight: 600; }
       |    .info th:first-child, .info td:first-child { text-align: left; }
       |    .info { margin-top: 12px; padding: 12px; background: white; border-radius: 8px; }
       |    .info h3 { margin: 0 0 4px; font-size: 15px; color: #333; }
       |  </style>
       |</head>
       |<body>
       |  <h1>Flight Analysis - ${fileName}</h1>
       |  <div id="toolbar">
       |    <button id="pill-zoom" class="pill mode-z">Zoom (Z)</button>
       |    <button id="pill-measure" class="pill mode-m">Measure (M)</button>
       |    <button id="pill-exit" class="pill set">Set Exit time (X)</button>
       |    <button id="pill-ground" class="pill set">Set Ground (N)</button>
       |    <label><input type="checkbox" id="tooltip-toggle" checked> Tooltip</label>
       |    <label><input type="checkbox" id="points-toggle"> Points</label>
       |    <label>Height <select id="unit-height"><option value="m">m</option><option value="ft" selected>ft</option></select></label>
       |    <label>Speed <select id="unit-speed"><option value="m/s">m/s</option><option value="km/h">km/h</option><option value="mph" selected>mph</option></select></label>
       |  </div>
       |  <div id="chart"></div>
       |  <div class="info">
       |${ChartInfoTables.stagesTableJs(state.stages)}
       |${ChartInfoTables.swoopTableJs(swoop)}
       |  </div>
       |  <script>__ECHARTS__</script>
       |  <script>__CHART_INIT__</script>
       |  <script>
       |    var chartDefaults = ${ChartPayload.roundNumbers(ChartOption.chartDefaultsJson(state, metrics)).noSpaces};
       |    var optionGz = "${ChartPayload.gzipBase64(ChartPayload.roundNumbers(ChartOption.option(state, metrics, swoop)).noSpaces)}";
       |    (async function () {
       |      try {
       |        var bytes = Uint8Array.from(atob(optionGz), function (c) { return c.charCodeAt(0); });
       |        var stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'));
       |        var option = JSON.parse(await new Response(stream).text());
       |        initChart(option);
       |      } catch (error) {
       |        document.getElementById('chart').textContent = 'Failed to load chart data: ' + error;
       |      }
       |    })();
       |  </script>
       |</body>
       |</html>
       |""".stripMargin.replace("__ECHARTS__", echartsLib).replace("__CHART_INIT__", chartInitJs)

}
