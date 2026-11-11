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

  private def swoopContent(swoop: Option[SwoopData]): String =
    if swoop.isDefined then ChartInfoTables.swoopTableJs(swoop)
    else """    <p class="placeholder">No swoop detected for this flight.</p>"""

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
       |    body { margin: 0; padding: 6px 10px; font-family: sans-serif; background: #f5f5f5; }
       |    #header { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: end; margin: 0 0 6px; }
       |    #tabs { display: flex; gap: 2px; justify-self: start; min-width: 0; overflow: hidden; }
       |    h1 { grid-column: 2; color: #333; font-size: 26px; line-height: 32px; margin: 0; text-align: center; }
       |    .tab { background: transparent; border: none; border-bottom: 2px solid transparent; padding: 4px 12px; font-size: 14px; font-family: inherit; color: #666; cursor: pointer; }
       |    .tab:hover { color: #333; }
       |    .tab.active { color: #1f77b4; border-bottom-color: #1f77b4; font-weight: 600; }
       |    .tab-panel { display: none; }
       |    .tab-panel.active { display: block; }
       |    .placeholder { margin: 0; padding: 24px; background: white; border-radius: 8px; color: #888; font-size: 14px; }
       |    .card { background: white; border-radius: 10px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); max-width: 760px; overflow: hidden; }
       |    .card h3 { margin: 0; padding: 12px 18px; font-size: 12px; letter-spacing: 0.06em; text-transform: uppercase; color: #555; background: #fafafa; border-bottom: 1px solid #eee; }
       |    .card table { width: 100%; border-collapse: collapse; font-size: 13px; }
       |    .card th { padding: 8px 18px; text-align: right; font-size: 11px; letter-spacing: 0.04em; text-transform: uppercase; color: #888; background: #fff; border-bottom: 1px solid #eee; }
       |    .card td { padding: 9px 18px; text-align: right; color: #333; border-bottom: 1px solid #f4f4f4; font-variant-numeric: tabular-nums; }
       |    .card tr:last-child td { border-bottom: none; }
       |    .card th:first-child, .card td:first-child { text-align: left; }
       |    .card tbody tr:hover td { background: #f5f9fd; }
       |    .card td.muted { color: #aaa; font-style: italic; }
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
       |  </style>
       |</head>
       |<body>
       |  <div id="header">
       |    <nav id="tabs">
       |      <button class="tab active" data-tab="flight">Flight Data</button>
       |      <button class="tab" data-tab="maps">Maps</button>
       |      <button class="tab" data-tab="swoop">Swoop</button>
       |      <button class="tab" data-tab="wind">Wind</button>
       |      <button class="tab" data-tab="tables">Tables</button>
       |    </nav>
       |    <h1>Flight Analysis - ${fileName}</h1>
       |  </div>
       |  <section id="tab-flight" class="tab-panel active">
       |  <div id="toolbar">
       |    <button id="pill-zoom" class="pill mode-z">Zoom (Z)</button>
       |    <button id="pill-measure" class="pill mode-m">Measure (M)</button>
       |    <button id="pill-exit" class="pill set">Set Exit time (X)</button>
       |    <button id="pill-ground" class="pill set">Set Ground (N)</button>
       |    <label><input type="checkbox" id="tooltip-toggle" checked> Tooltip</label>
       |    <label><input type="checkbox" id="points-toggle"> Jump Points</label>
       |    <label><input type="checkbox" id="swoop-points-toggle"> Swoop Points</label>
       |    <label>Height <select id="unit-height"><option value="m">m</option><option value="ft">ft</option></select></label>
       |    <label>Speed <select id="unit-speed"><option value="m/s">m/s</option><option value="km/h">km/h</option><option value="mph">mph</option></select></label>
       |  </div>
       |  <div id="chart"></div>
       |  </section>
       |  <section id="tab-maps" class="tab-panel"><p class="placeholder">No map content yet.</p></section>
       |  <section id="tab-swoop" class="tab-panel">
       |${swoopContent(swoop)}
       |  </section>
       |  <section id="tab-wind" class="tab-panel"><p class="placeholder">No wind content yet.</p></section>
       |  <section id="tab-tables" class="tab-panel">
       |${ChartInfoTables.stagesTableJs(state.stages)}
       |  </section>
       |  <script>
       |    (function () {
       |      var tabs = document.querySelectorAll('.tab');
       |      tabs.forEach(function (tab) {
       |        tab.addEventListener('click', function () {
       |          tabs.forEach(function (other) { other.classList.toggle('active', other === tab); });
       |          document.querySelectorAll('.tab-panel').forEach(function (panel) {
       |            panel.classList.toggle('active', panel.id === 'tab-' + tab.dataset.tab);
       |          });
       |          tab.blur();
       |          window.dispatchEvent(new Event('resize'));
       |        });
       |      });
       |    })();
       |  </script>
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
