/* Sleep — hypnogram, stages, in-sleep signals, 7-night chart, smart alarm */
function Hypnogram({ seq, height = 120 }) {
  const levels = { awake: 0, rem: 1, light: 2, deep: 3 };
  const colors = { awake: 'var(--stage-awake)', rem: 'var(--stage-rem)', light: 'var(--stage-light)', deep: 'var(--stage-deep)' };
  const total = seq.reduce((a, [, m]) => a + m, 0);
  const rowH = height / 4;
  const w = 600;
  let x = 0;
  const rects = seq.map(([stage, mins], i) => {
    const segW = (mins / total) * w;
    const rect = <rect key={i} x={x} y={levels[stage] * rowH + 3} width={Math.max(1, segW - 1.5)} height={rowH - 6} rx="3" fill={colors[stage]} />;
    x += segW;
    return rect;
  });
  return (
    <div>
      <svg width="100%" height={height} viewBox={`0 0 ${w} ${height}`} preserveAspectRatio="none">{rects}</svg>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8 }}>
        <span style={{ font: '500 10px var(--font-mono)', color: 'var(--fg-3)' }}>23:18</span>
        <span style={{ font: '500 10px var(--font-mono)', color: 'var(--fg-3)' }}>02:30</span>
        <span style={{ font: '500 10px var(--font-mono)', color: 'var(--fg-3)' }}>05:00</span>
        <span style={{ font: '500 10px var(--font-mono)', color: 'var(--fg-3)' }}>07:06</span>
      </div>
    </div>
  );
}

function StageRow({ label, dur, frac, color }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: 8, font: '600 13px var(--font-ui)', color: 'var(--fg-1)' }}>
          <span style={{ width: 9, height: 9, borderRadius: 2, background: color }} />{label}
        </span>
        <span style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 15, color: 'var(--fg-1)' }}>{dur}</span>
      </div>
      <Bar frac={frac} color={color} height={6} />
    </div>
  );
}

function SleepScreen({ alarmOn, setAlarmOn }) {
  const d = OW.latest;
  const last7 = OW.days.slice(-7);
  const signals = [
    { l: 'Resting HR', v: d.restingHr, u: 'bpm' }, { l: 'HRV', v: Math.round(d.avgHrv), u: 'ms' },
    { l: 'Resp Rate', v: d.respRateBpm.toFixed(1), u: 'rpm' }, { l: 'SpO₂', v: d.spo2Pct.toFixed(1), u: '%' },
    { l: 'Skin Temp', v: (d.skinTempDevC >= 0 ? '+' : '') + d.skinTempDevC.toFixed(1), u: '°C' },
    { l: 'Disturb.', v: d.disturbances, u: '×' },
  ];
  const maxSleep = Math.max(...last7.map(x => x.totalSleepMin));

  return (
    <Screen>
      <ScreenHead title="Sleep" right={<span style={{ font: '700 13px var(--font-ui)', color: 'var(--fg-3)' }}>{d.label}</span>} />

      {/* headline */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div>
            <MicroLabel>Time Asleep</MicroLabel>
            <Stat value={OW.fmtDur(d.totalSleepMin)} size={40} style={{ marginTop: 8, color: 'var(--blue)' }} />
            <div style={{ font: '500 12px var(--font-ui)', color: 'var(--fg-3)', marginTop: 8 }}>
              23:18 → 07:06 · {Math.round(d.totalSleepMin / d.sleepNeedMin * 100)}% of need
            </div>
          </div>
          <Ring value={d.efficiency} size={92} stroke={8} color="var(--blue)">
            <Stat value={Math.round(d.efficiency * 100)} unit="%" size={24} />
            <MicroLabel style={{ marginTop: 3, fontSize: 8 }}>Effic.</MicroLabel>
          </Ring>
        </div>
        <div style={{ marginTop: 18 }}><Hypnogram seq={OW.stageSeq} /></div>
      </Card>

      {/* stage breakdown */}
      <Card>
        <MicroLabel style={{ marginBottom: 16 }}>Sleep Stages</MicroLabel>
        <StageRow label="Deep (SWS)" dur={OW.fmtDur(d.deepMin)} frac={d.deepMin / d.totalSleepMin} color="var(--stage-deep)" />
        <StageRow label="REM" dur={OW.fmtDur(d.remMin)} frac={d.remMin / d.totalSleepMin} color="var(--stage-rem)" />
        <StageRow label="Light" dur={OW.fmtDur(d.lightMin)} frac={d.lightMin / d.totalSleepMin} color="var(--stage-light)" />
        <StageRow label="Awake" dur={`${d.disturbances * 3}m (${d.disturbances}×)`} frac={(d.disturbances * 3) / d.totalSleepMin} color="var(--stage-awake)" />
      </Card>

      {/* in-sleep signals */}
      <SectionTitle>In-sleep signals</SectionTitle>
      <Card style={{ padding: 16 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: '18px 8px' }}>
          {signals.map(s => (
            <div key={s.l}>
              <MicroLabel style={{ fontSize: 9, letterSpacing: 0.6 }}>{s.l}</MicroLabel>
              <Stat value={s.v} unit={s.u} size={22} style={{ marginTop: 7 }} />
            </div>
          ))}
        </div>
      </Card>

      {/* 7-night chart */}
      <Card>
        <MicroLabel style={{ marginBottom: 14 }}>Last 7 Nights</MicroLabel>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, height: 110 }}>
          {last7.map((n, i) => (
            <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
              <span style={{ font: '700 10px var(--font-display)', color: 'var(--fg-2)' }}>{(n.totalSleepMin/60).toFixed(1)}</span>
              <div style={{ width: '100%', height: (n.totalSleepMin / maxSleep) * 78, borderRadius: 5,
                background: i === last7.length - 1 ? 'var(--blue)' : 'rgba(44,155,255,.32)' }} />
              <span style={{ font: '600 10px var(--font-ui)', color: 'var(--fg-3)' }}>{n.short}</span>
            </div>
          ))}
        </div>
      </Card>

      {/* smart alarm */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 14 }}>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Icon name="alarm" size={20} color="var(--green)" />
              <span style={{ font: '700 16px var(--font-ui)', color: 'var(--fg-1)' }}>Smart Alarm</span>
            </div>
            <div style={{ font: '500 12px var(--font-ui)', color: 'var(--fg-3)', marginTop: 6 }}>
              Silent wrist buzz at 07:00 · fires from strap firmware even if the app is closed.
            </div>
          </div>
          <Toggle on={alarmOn} set={setAlarmOn} />
        </div>
      </Card>
    </Screen>
  );
}

function Toggle({ on, set }) {
  return (
    <button onClick={() => set(!on)} style={{
      width: 50, height: 30, borderRadius: 999, border: 0, cursor: 'pointer', position: 'relative',
      background: on ? 'rgba(43,240,122,.4)' : 'var(--bg-3)', transition: 'background 200ms',
    }}>
      <span style={{ position: 'absolute', top: 3, left: on ? 23 : 3, width: 24, height: 24, borderRadius: '50%',
        background: on ? 'var(--green)' : 'var(--fg-3)', transition: 'left 200ms' }} />
    </button>
  );
}
window.SleepScreen = SleepScreen;
