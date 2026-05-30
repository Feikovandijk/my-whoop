// OpenWhoop mock data — mirrors DailyMetricEntity + workouts from the source app.
// Attached to window.OW for all screen components.
(function () {
  // deterministic pseudo-random
  let seed = 42;
  const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
  const r = (lo, hi) => lo + rnd() * (hi - lo);

  const DAYS = 30;
  const today = new Date('2026-05-29T07:00:00');
  const days = [];
  for (let i = DAYS - 1; i >= 0; i--) {
    const d = new Date(today.getTime() - i * 86400000);
    const day = d.toISOString().slice(0, 10);
    // weekly rhythm: weekends recover more, mid-week strain higher
    const dow = d.getDay();
    const hard = dow === 1 || dow === 3 || dow === 5;
    const recovery = Math.max(0.12, Math.min(0.97, r(0.42, 0.9) + (dow === 0 || dow === 6 ? 0.08 : -0.05) - (hard ? 0.06 : 0)));
    const strain = Math.max(4, Math.min(20.5, r(8, 14) + (hard ? 4.5 : 0)));
    const totalSleepMin = r(370, 480);
    const deep = totalSleepMin * r(0.16, 0.24);
    const rem = totalSleepMin * r(0.18, 0.27);
    const light = totalSleepMin - deep - rem;
    days.push({
      day,
      label: d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' }),
      short: d.toLocaleDateString('en-US', { weekday: 'short' })[0],
      recovery,
      strain,
      totalSleepMin,
      efficiency: r(0.84, 0.96),
      deepMin: deep, remMin: rem, lightMin: light,
      disturbances: Math.round(r(2, 9)),
      restingHr: Math.round(r(46, 56) - recovery * 6),
      avgHrv: Math.round(r(48, 86) + (recovery - 0.5) * 40),
      spo2Pct: r(95.2, 98.4),
      skinTempDevC: r(-0.6, 0.7),
      respRateBpm: r(13.5, 16.2),
      sleepNeedMin: r(460, 510),
      // bed/wake clock for charts
      bedHour: r(22.4, 23.9),
      wakeHour: r(6.3, 7.8),
    });
  }
  const latest = days[days.length - 1];

  // hypnogram for last night — array of {stage, mins}
  const stageSeq = [
    ['light', 18], ['deep', 42], ['light', 22], ['rem', 28], ['light', 30],
    ['deep', 34], ['awake', 6], ['light', 26], ['rem', 38], ['light', 24],
    ['deep', 18], ['rem', 30], ['light', 20], ['awake', 5], ['rem', 22], ['light', 26],
  ];

  const workouts = [
    { kind: 'Running', startTs: today.getTime()/1000 - 86400*0 - 3600*11, durationS: 2940, avgHr: 152, peakHr: 178, strain: 14.6, caloriesKcal: 612, avgHrrPct: 71, hrmax: 191, zones: [6, 10, 22, 34, 20, 8] },
    { kind: 'Strength', startTs: today.getTime()/1000 - 86400*1 - 3600*9, durationS: 3300, avgHr: 121, peakHr: 156, strain: 10.8, caloriesKcal: 388, avgHrrPct: 52, hrmax: 191, zones: [18, 28, 30, 16, 6, 2] },
    { kind: 'Cycling', startTs: today.getTime()/1000 - 86400*2 - 3600*17, durationS: 4500, avgHr: 138, peakHr: 169, strain: 13.1, caloriesKcal: 540, avgHrrPct: 63, hrmax: 191, zones: [10, 16, 30, 28, 12, 4] },
    { kind: 'Swimming', startTs: today.getTime()/1000 - 86400*3 - 3600*7, durationS: 2400, avgHr: 134, peakHr: 161, strain: 11.9, caloriesKcal: null, avgHrrPct: 58, hrmax: 191, zones: [8, 20, 34, 26, 10, 2] },
    { kind: 'Walking', startTs: today.getTime()/1000 - 86400*4 - 3600*18, durationS: 3000, avgHr: 98, peakHr: 122, strain: 6.4, caloriesKcal: 210, avgHrrPct: 34, hrmax: 191, zones: [40, 38, 16, 4, 2, 0] },
  ];

  // a 24h HR stream (1-min-ish, downsampled to ~120 pts)
  const hrStream = [];
  for (let i = 0; i < 120; i++) {
    const t = i / 120;
    let bpm = 52 + Math.sin(t * Math.PI * 2 - 1) * 6;
    if (t > 0.46 && t < 0.49) bpm = 150 + Math.sin(i) * 14; // workout spike
    bpm += rnd() * 8;
    hrStream.push(Math.round(bpm));
  }

  window.OW = {
    days, latest, stageSeq, workouts, hrStream,
    recoveryColor(v) { return v >= 0.66 ? 'var(--green)' : v >= 0.34 ? 'var(--yellow)' : 'var(--red)'; },
    recoveryWord(v) { return v >= 0.66 ? 'High' : v >= 0.34 ? 'Moderate' : 'Low'; },
    fmtDur(min) { const h = Math.floor(min / 60), m = Math.round(min % 60); return `${h}h ${m}m`; },
    zoneColors: ['var(--zone-0)','var(--zone-1)','var(--zone-2)','var(--zone-3)','var(--zone-4)','var(--zone-5)'],
  };
})();
