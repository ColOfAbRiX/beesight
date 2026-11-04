function initChart(option) {
  var chart = echarts.init(document.getElementById('chart'));
  chart.setOption(option);

  var badge = document.getElementById('badge');
  var stats = document.getElementById('stats');
  var visible = {};
  var mode = 'z';

  var selected = (option.legend && option.legend.selected) || {};
  (option.series || []).forEach(function (series) {
    visible[series.name] = selected[series.name] !== false;
  });

  function setMode(next) {
    mode = next;
    if (next === 'm') {
      badge.textContent = 'Measure (M)';
      chart.dispatchAction({
        type: 'takeGlobalCursor',
        key: 'brush',
        brushOption: { brushType: 'lineX', brushMode: 'single' },
      });
      chart.setOption({ dataZoom: [{ disabled: true }] });
    } else {
      badge.textContent = 'Zoom / pan (Z)';
      chart.dispatchAction({
        type: 'takeGlobalCursor',
        key: 'brush',
        brushOption: { brushType: false },
      });
      chart.setOption({ dataZoom: [{ disabled: false }] });
    }
    clearSelection();
  }

  function clearSelection() {
    chart.dispatchAction({ type: 'brush', areas: [] });
    stats.innerHTML = '';
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

  function renderStats(x0, x1) {
    var rows = selectionStats(x0, x1)
      .map(function (row) {
        return '<tr><td>' + row[0] + '</td>' + row
          .slice(1)
          .map(function (value) { return '<td>' + value.toFixed(2) + '</td>'; })
          .join('') + '</tr>';
      })
      .join('');
    stats.innerHTML =
      '<table><thead><tr><th>Series</th><th>Start</th><th>End</th><th>Δ</th><th>Min</th><th>Max</th><th>Mean</th></tr></thead><tbody>' +
      rows +
      '</tbody></table><p>Duration: ' + (x1 - x0).toFixed(1) + ' s</p>';
  }

  chart.on('legendselectchanged', function (params) {
    visible = params.selected;
  });

  chart.on('brushEnd', function (params) {
    var area = params.areas && params.areas[0];
    if (area && area.coordRange) renderStats(area.coordRange[0], area.coordRange[1]);
  });

  chart.getZr().on('click', function () {
    if (mode === 'm') clearSelection();
  });

  document.addEventListener('keydown', function (event) {
    var key = (event.key || '').toLowerCase();
    if (key === 'z') setMode('z');
    if (key === 'm') setMode('m');
  });

  window.addEventListener('resize', function () {
    chart.resize();
  });

  setMode('z');
}
