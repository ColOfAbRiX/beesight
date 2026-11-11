var axisSignificantDigits = 4;

function initChart(option) {
  var chartEl = document.getElementById('chart');
  var zoomPill = document.getElementById('pill-zoom');
  var measurePill = document.getElementById('pill-measure');
  var exitPill = document.getElementById('pill-exit');
  var groundPill = document.getElementById('pill-ground');
  var tooltipToggle = document.getElementById('tooltip-toggle');
  var pointsToggle = document.getElementById('points-toggle');
  var heightSelect = document.getElementById('unit-height');
  var speedSelect = document.getElementById('unit-speed');
  var visible = {};
  var tooltipOn = true;
  var mode = 'z';
  var mStats = null;
  var pendingSet = null;

  var defaults = (typeof chartDefaults !== 'undefined') ? chartDefaults : { exit: 0, ground: 0 };
  var defaultExit = defaults.exit;
  var defaultGround = defaults.ground;
  var exitZero = defaultExit;
  var groundOffset = defaultGround;

  function fitHeight() {
    var top = chartEl.getBoundingClientRect().top;
    chartEl.style.height = Math.max(240, window.innerHeight - top - 6) + 'px';
  }

  fitHeight();

  var chart = echarts.init(chartEl);
  chart.setOption(option);

  var selected = (option.legend && option.legend.selected) || {};
  (option.series || []).forEach(function (series) {
    visible[series.name] = selected[series.name] !== false;
  });

  var altitudeData = option.series[0] ? option.series[0].data : [];
  var linePairs = altitudeData.filter(Array.isArray);
  var pairsOf = (option.series || []).map(function (series) {
    return (series.data || []).filter(Array.isArray);
  });
  var pristine = (option.yAxis || []).map(function (axis) { return [axis.min, axis.max]; });
  var axisUnits = (option.yAxis || []).map(function (axis) { return axis.name; });
  var heightUnit = 'ft';
  var speedUnit = 'mph';
  var heightFactors = { m: 1, ft: 3.28084 };
  var speedFactors = { 'm/s': 1, 'km/h': 3.6, mph: 2.23694 };

  function categoryOf(index) {
    var unit = axisUnits[index];
    if (unit === 'm') return 'height';
    if (unit === 'm/s') return 'speed';
    return 'raw';
  }

  function convert(index, value) {
    var category = categoryOf(index);
    if (category === 'height') return (value - groundOffset) * heightFactors[heightUnit];
    if (category === 'speed') return value * speedFactors[speedUnit];
    return value;
  }

  function seriesLabel(index, name) {
    var category = categoryOf(index);
    if (category === 'raw') return name;
    return name.replace(/\s*\([^)]*\)\s*$/, '') + ' (' + unitLabel(index) + ')';
  }

  function edgeLabel(value) {
    if (Math.abs(value) >= 1e5) return compact(value);
    return Math.abs(value) >= 10 ? String(Math.round(value)) : String(Number(value.toFixed(2)));
  }

  function unitLabel(index) {
    var category = categoryOf(index);
    if (category === 'height') return heightUnit;
    if (category === 'speed') return speedUnit;
    return axisUnits[index];
  }

  function compact(value) {
    var abs = Math.abs(value);
    if (abs >= 1e6) return String(Number((value / 1e6).toPrecision(axisSignificantDigits))) + 'M';
    if (abs >= 1e5) return String(Number((value / 1e3).toPrecision(axisSignificantDigits))) + 'k';
    return String(Number(value.toPrecision(axisSignificantDigits)));
  }

  function xFormat(value) {
    return String(Math.round(value));
  }

  function pad2(value) {
    return value < 10 ? '0' + value : String(value);
  }

  function timeFormat(value) {
    var total = Math.round(value);
    var sign = total < 0 ? '-' : '';
    var abs = Math.abs(total);
    var hours = Math.floor(abs / 3600);
    var minutes = Math.floor((abs % 3600) / 60);
    var seconds = abs % 60;
    if (hours > 0) return sign + hours + ':' + pad2(minutes) + ':' + pad2(seconds);
    return sign + pad2(minutes) + ':' + pad2(seconds);
  }

  function timeFormatter(value) {
    return timeFormat(value - exitZero);
  }

  window.valueFormat = compact;
  window.xFormat = xFormat;
  window.timeFormat = timeFormat;
  window.chartValues = function () {
    return {
      exit: exitZero,
      ground: groundOffset,
      defaults: { exit: defaultExit, ground: defaultGround },
      heightUnit: heightUnit,
      speedUnit: speedUnit,
    };
  };

  var seriesIndexByName = {};
  (option.series || []).forEach(function (series, index) {
    seriesIndexByName[series.name] = index;
  });

  function applyXAxisLayout() {
    var rect = chart.getModel().getComponent('grid', 0).coordinateSystem.getRect();
    var bottom = rect.y + rect.height;
    var point = chart.convertToPixel({ xAxisIndex: 0, yAxisIndex: 0 }, [0, groundOffset]);
    var offset = 0;
    if (point && point[1] !== null && point[1] !== undefined) {
      offset = Math.max(-(rect.height - 12), Math.min(0, point[1] - bottom));
    }
    chart.setOption({
      xAxis: [
        { offset: offset },
        { axisLabel: { formatter: timeFormatter }, offset: offset },
      ],
    });
  }

  function updateExitLine() {
    chart.setOption({
      series: [
        {
          markLine: {
            silent: true,
            symbol: 'none',
            label: { show: false },
            lineStyle: { type: 'dashed', color: '#999', width: 1 },
            data: [{ xAxis: exitZero }],
          },
        },
      ],
    });
  }

  function windowOf() {
    var dz = chart.getOption().dataZoom[0];
    var min = option.xAxis[0].min;
    var max = option.xAxis[0].max;
    var start = dz.startValue !== undefined ? dz.startValue : min + (max - min) * (dz.start || 0) / 100;
    var end = dz.endValue !== undefined ? dz.endValue : min + (max - min) * (dz.end === undefined ? 100 : dz.end) / 100;
    return [start, end];
  }

  function windowedBounds(series, win) {
    var lo = Infinity;
    var hi = -Infinity;
    (series.data || []).forEach(function (item) {
      if (!Array.isArray(item) || item[0] < win[0] || item[0] > win[1]) return;
      if (item[1] < lo) lo = item[1];
      if (item[1] > hi) hi = item[1];
    });
    if (lo > hi) return null;
    var pad = Math.max((hi - lo) * 0.02, 0.1);
    return [lo - pad, hi + pad];
  }

  function applyAxes() {
    var live = chart.getOption().legend[0];
    var current = (live && live.selected) || visible;
    var win = windowOf();
    var shown = [];
    (option.series || []).forEach(function (series, index) {
      if (current[series.name] !== false) shown.push(index);
    });
    if (shown.length === 0) shown = [0];

    pristine = (option.yAxis || []).map(function (axis, index) {
      var series = (option.series || [])[index];
      var windowed = series ? windowedBounds(series, win) : null;
      return windowed || [axis.min, axis.max];
    });

    var axes = (option.yAxis || []).map(function (axis, index) {
      var column = shown.indexOf(index);
      return {
        show: column >= 0,
        offset: (column < 0 ? 0 : column) * 60,
        name: unitLabel(index),
        min: pristine[index][0],
        max: pristine[index][1],
        splitLine: { show: column === 0 },
        axisLabel: {
          formatter: (function (i) {
            return function (value) {
              var raw = convert(i, value);
              var edge = Math.abs(value - pristine[i][0]) <= 1e-9 || Math.abs(value - pristine[i][1]) <= 1e-9;
              return edge ? edgeLabel(raw) : compact(raw);
            };
          })(index),
        },
      };
    });

    chart.setOption({
      yAxis: axes,
      grid: { left: shown.length * 60 },
      legend: {
        formatter: function (name) {
          var index = seriesIndexByName[name];
          return index === undefined ? name : seriesLabel(index, name);
        },
      },
    });
    applyXAxisLayout();
  }

  window.applyAxes = applyAxes;

  function applyPoints() {
    chart.setOption({
      series: [{ data: pointsToggle.checked ? altitudeData : linePairs }],
    });
  }

  function selectionStats(x0, x1) {
    var rows = [];
    (option.series || []).forEach(function (series) {
      if (!visible[series.name]) return;
      var ys = (series.data || [])
        .filter(function (item) {
          return Array.isArray(item) && item[0] >= x0 && item[0] <= x1;
        })
        .map(function (item) {
          return item[1];
        });
      if (ys.length === 0) return;
      var sum = ys.reduce(function (total, y) { return total + y; }, 0);
      rows.push([
        series.name,
        ys[0],
        ys[ys.length - 1],
        ys[ys.length - 1] - ys[0],
        Math.min.apply(null, ys),
        Math.max.apply(null, ys),
        sum / ys.length,
      ]);
    });
    return rows;
  }

  function statsHtml() {
    if (!mStats) return '<div>Select an interval</div>';
    var rows = mStats.rows.map(function (row) {
      var index = (option.series || []).findIndex(function (series) { return series.name === row[0]; });
      return '<tr><td>' + seriesLabel(index, row[0]) + '</td>' + row
        .slice(1)
        .map(function (value) { return '<td>' + compact(convert(index, value)) + '</td>'; })
        .join('') + '</tr>';
    }).join('');
    return (
      '<table style="border-collapse:collapse;font-size:11px;">' +
      '<thead><tr><th style="text-align:left;padding:1px 8px 1px 0;">Series</th><th style="padding:1px 8px;">Start</th><th style="padding:1px 8px;">End</th><th style="padding:1px 8px;">Δ</th><th style="padding:1px 8px;">Min</th><th style="padding:1px 8px;">Max</th><th style="padding:1px 8px;">Avg</th></tr></thead>' +
      '<tbody>' + rows + '</tbody></table>' +
      '<div style="margin-top:3px;font-size:11px;">Duration: ' + mStats.duration.toFixed(1) + ' s</div>'
    );
  }

  function axisTooltipHtml(params) {
    if (!params || params.length === 0) return '';
    var xValue = Array.isArray(params[0].value) ? params[0].value[0] : null;
    var header =
      xValue === null
        ? ''
        : '<div>Time (s): ' + xFormat(xValue) + '</div>' +
          '<div>From exit: ' + timeFormat(xValue - exitZero) + '</div>';
    var rows = params.map(function (p) {
      var raw = Array.isArray(p.value) ? p.value[1] : p.value;
      return '<div>' + (p.marker || '') + seriesLabel(p.seriesIndex, p.seriesName) + ': <b>' + compact(convert(p.seriesIndex, raw)) + '</b></div>';
    });
    return header + rows.join('');
  }

  function baseTooltip() {
    return {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      extraCssText: 'font-size: 11px; padding: 4px 8px;',
      formatter: axisTooltipHtml,
    };
  }

  function applyTooltip() {
    if (mode === 'm') {
      var stats = baseTooltip();
      stats.formatter = statsHtml;
      chart.setOption({ tooltip: stats }, { replaceMerge: ['tooltip'] });
    } else {
      var restored = baseTooltip();
      restored.show = tooltipOn;
      chart.setOption({ tooltip: restored }, { replaceMerge: ['tooltip'] });
    }
  }

  function armBrush() {
    chart.dispatchAction({
      type: 'takeGlobalCursor',
      key: 'brush',
      brushOption: { brushType: 'lineX', brushMode: 'single' },
    });
  }

  function clearSelection() {
    chart.dispatchAction({ type: 'brush', areas: [] });
    mStats = null;
  }

  function refreshPills() {
    zoomPill.classList.toggle('active', mode === 'z');
    measurePill.classList.toggle('active', mode === 'm');
    exitPill.classList.toggle('armed', pendingSet === 'exit');
    groundPill.classList.toggle('armed', pendingSet === 'ground');
  }

  function setMode(next) {
    mode = next;
    armBrush();
    chart.setOption({ dataZoom: [{ disabled: next === 'm' }] });
    if (next === 'm') {
      tooltipToggle.checked = true;
      tooltipToggle.disabled = true;
    } else {
      tooltipToggle.disabled = false;
      tooltipToggle.checked = tooltipOn;
    }
    applyTooltip();
    clearSelection();
    refreshPills();
  }

  function armSet(which) {
    pendingSet = which;
    refreshPills();
  }

  function disarmSet() {
    pendingSet = null;
    refreshPills();
  }

  function resetExit() {
    exitZero = defaultExit;
    applyXAxisLayout();
    updateExitLine();
  }

  function resetGround() {
    groundOffset = defaultGround;
    applyAxes();
  }

  function resetAll() {
    exitZero = defaultExit;
    groundOffset = defaultGround;
    updateExitLine();
    applyAxes();
  }

  function applySetFromClick(pixel) {
    var point = chart.convertFromPixel({ xAxisIndex: 0, yAxisIndex: 0 }, pixel);
    if (!point || point[0] === null || point[1] === null) return false;
    if (pendingSet === 'exit') {
      exitZero = point[0];
      applyXAxisLayout();
      updateExitLine();
    } else if (pendingSet === 'ground') {
      groundOffset = point[1];
      applyAxes();
    } else {
      return false;
    }
    disarmSet();
    armBrush();
    return true;
  }

  chart.on('legendselectchanged', function (params) {
    visible = params.selected;
    applyAxes();
  });

  chart.on('dataZoom', function () {
    applyAxes();
  });

  chart.on('brushEnd', function (params) {
    var area = params.areas && params.areas[0];
    if (!area || !area.coordRange) return;
    if (mode === 'm') {
      mStats = {
        rows: selectionStats(area.coordRange[0], area.coordRange[1]),
        duration: area.coordRange[1] - area.coordRange[0],
      };
    } else {
      chart.dispatchAction({
        type: 'dataZoom',
        dataZoomIndex: 0,
        startValue: area.coordRange[0],
        endValue: area.coordRange[1],
      });
      clearSelection();
    }
  });

  chart.getZr().on('click', function (event) {
    if (pendingSet) {
      applySetFromClick([event.offsetX, event.offsetY]);
      return;
    }
    if (mode === 'm') clearSelection();
  });

  var panArmed = false;
  var panning = false;
  var panStart = null;

  function pixelX(event) {
    return event.offsetX !== undefined ? event.offsetX : event.event.offsetX;
  }

  chart.getZr().on('dblclick', function () {
    if (mode !== 'z' || pendingSet) return;
    panArmed = true;
    chart.dispatchAction({ type: 'takeGlobalCursor', key: 'brush', brushOption: { brushType: false } });
    chartEl.style.cursor = 'grab';
  });

  chart.getZr().on('mousedown', function (event) {
    if (!panArmed) return;
    panning = true;
    chartEl.style.cursor = 'grabbing';
    panStart = { dataX: chart.convertFromPixel({ xAxisIndex: 0, yAxisIndex: 0 }, [pixelX(event), 0])[0], win: windowOf() };
  });

  chart.getZr().on('mousemove', function (event) {
    if (!panning || !panStart) return;
    var dataX = chart.convertFromPixel({ xAxisIndex: 0, yAxisIndex: 0 }, [pixelX(event), 0])[0];
    var shift = dataX - panStart.dataX;
    shift = Math.max(option.xAxis[0].min - panStart.win[0], Math.min(shift, option.xAxis[0].max - panStart.win[1]));
    chart.dispatchAction({
      type: 'dataZoom',
      dataZoomIndex: 0,
      startValue: panStart.win[0] + shift,
      endValue: panStart.win[1] + shift,
    });
  });

  function endPan() {
    if (!panArmed) return;
    panArmed = false;
    panning = false;
    panStart = null;
    chartEl.style.cursor = '';
    armBrush();
  }

  chart.getZr().on('mouseup', endPan);
  chart.getZr().on('globalout', endPan);

  function valueAt(pairs, x) {
    if (x <= pairs[0][0]) return pairs[0][1];
    var last = pairs[pairs.length - 1];
    if (x >= last[0]) return last[1];
    var lo = 0;
    var hi = pairs.length - 1;
    while (hi - lo > 1) {
      var mid = (lo + hi) >> 1;
      if (pairs[mid][0] <= x) lo = mid;
      else hi = mid;
    }
    var a = pairs[lo];
    var b = pairs[hi];
    return a[1] + ((x - a[0]) / (b[0] - a[0])) * (b[1] - a[1]);
  }

  var cursorPoint = null;
  var cursorFrame = null;

  function hideCursorDots() {
    cursorPoint = null;
    chart.setOption({ graphic: [] }, { replaceMerge: ['graphic'] });
  }

  function flushCursorDots() {
    cursorFrame = null;
    if (cursorPoint === null) return;
    var dataX = chart.convertFromPixel({ xAxisIndex: 0, yAxisIndex: 0 }, [cursorPoint, 0])[0];
    if (dataX === null || dataX === undefined || Number.isNaN(dataX)) {
      hideCursorDots();
      return;
    }
    dataX = Math.max(option.xAxis[0].min, Math.min(option.xAxis[0].max, dataX));
    var live = chart.getOption().legend[0];
    var current = (live && live.selected) || visible;
    var items = [];
    (option.series || []).forEach(function (series, index) {
      if (current[series.name] === false) return;
      var pairs = pairsOf[index];
      if (!pairs.length || dataX < pairs[0][0] || dataX > pairs[pairs.length - 1][0]) return;
      var y = valueAt(pairs, dataX);
      var point = chart.convertToPixel({ xAxisIndex: 0, yAxisIndex: index }, [dataX, y]);
      if (!point || point[0] === null || point[1] === null) return;
      items.push({
        type: 'circle',
        shape: { cx: point[0], cy: point[1], r: 2.5 },
        style: { fill: (series.lineStyle && series.lineStyle.color) || '#333' },
        silent: true,
      });
    });
    chart.setOption({ graphic: items }, { replaceMerge: ['graphic'] });
  }

  chart.getZr().on('mousemove', function (event) {
    cursorPoint = pixelX(event);
    if (cursorFrame === null) cursorFrame = requestAnimationFrame(flushCursorDots);
  });

  chart.getZr().on('globalout', hideCursorDots);

  zoomPill.addEventListener('click', function () {
    zoomPill.blur();
    setMode('z');
  });

  measurePill.addEventListener('click', function () {
    measurePill.blur();
    setMode('m');
  });

  exitPill.addEventListener('click', function () {
    exitPill.blur();
    if (pendingSet === 'exit') {
      resetExit();
      disarmSet();
    } else {
      armSet('exit');
    }
  });

  groundPill.addEventListener('click', function () {
    groundPill.blur();
    if (pendingSet === 'ground') {
      resetGround();
      disarmSet();
    } else {
      armSet('ground');
    }
  });

  tooltipToggle.addEventListener('change', function () {
    tooltipOn = tooltipToggle.checked;
    applyTooltip();
  });

  pointsToggle.addEventListener('change', applyPoints);

  heightSelect.addEventListener('change', function () {
    heightUnit = heightSelect.value;
    applyAxes();
  });

  speedSelect.addEventListener('change', function () {
    speedUnit = speedSelect.value;
    applyAxes();
  });

  document.addEventListener('keydown', function (event) {
    var key = (event.key || '').toLowerCase();
    if (key === 'z') setMode('z');
    if (key === 'm') setMode('m');
    if (key === 'x') {
      if (pendingSet === 'exit') {
        resetExit();
        disarmSet();
      } else {
        armSet('exit');
      }
    }
    if (key === 'n') {
      if (pendingSet === 'ground') {
        resetGround();
        disarmSet();
      } else {
        armSet('ground');
      }
    }
    if (key === 'r') {
      resetAll();
      disarmSet();
    }
  });

  window.addEventListener('resize', function () {
    fitHeight();
    chart.resize();
  });

  applyPoints();
  applyAxes();
  updateExitLine();
  setMode('z');
}
