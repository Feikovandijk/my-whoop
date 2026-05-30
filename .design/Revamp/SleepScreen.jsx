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
    { k: 'rhr', l: 'Resting HR', v: d.restingHr, u: 'bpm' }, { k: 'hrv', l: 'HRV', v: Math.round(d.avgHrv), u: 'ms' },
    { k: 'resp', l: 'Resp Rate', v: d.respRateBpm.toFixed(1), u: 'rpm' }, { k: 'spo2', l: 'SpO₂', v: d.spo2Pct.toFixed(1), u: '%' },
    { k: 'skintemp', l: 'Skin Temp', v: (d.skinTempDevC >= 0 ? '+' : '') + d.skinTempDevC.toFixed(1), u: '°C' },
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
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <MicroLabel>Time Asleep</MicroLabel>
              <ExplainButton metric="sleep" color="var(--blue)" />
            </div>
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
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
          <MicroLabel style={{ margin: 0 }}>Sleep Stages</MicroLabel>
          <ExplainButton metric="sleep" color="var(--blue)" />
        </div>
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
            s.k
              ? <Explainable key={s.l} metric={s.k} style={{ borderRadius: 12 }}>
                  <MicroLabel style={{ fontSize: 9, letterSpacing: 0.6 }}>{s.l}</MicroLabel>
                  <Stat value={s.v} unit={s.u} size={22} style={{ marginTop: 7 }} />
                </Explainable>
              : <div key={s.l}>
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

      {/* smart alarm — real, editable setting */}
      <SmartAlarm />
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

/* ---- Smart Alarm: a real, editable, persisted setting ------------------ */
const pad2 = (n) => String(n).padStart(2, '0');
const DAY_LETTERS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

function loadAlarm() {
  try {
    const raw = localStorage.getItem('ow_alarm');
    if (raw) return { on: true, h: 7, m: 0, window: 30, days: [1, 2, 3, 4, 5], ...JSON.parse(raw) };
  } catch (e) {}
  return { on: true, h: 7, m: 0, window: 30, days: [1, 2, 3, 4, 5] };
}

function Stepper({ value, onUp, onDown, label }) {
  const btn = {
    width: 44, height: 30, borderRadius: 10, cursor: 'pointer', display: 'grid', placeItems: 'center',
    background: 'var(--bg-2)', border: '1px solid var(--line-2)', color: 'var(--fg-2)',
  };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
      <button onClick={onUp} style={btn} aria-label={`${label} up`}><Icon name="keyboard_arrow_up" size={22} /></button>
      <span style={{ font: '700 46px var(--font-display)', color: 'var(--fg-1)', fontVariantNumeric: 'tabular-nums', lineHeight: 1, minWidth: 64, textAlign: 'center' }}>{value}</span>
      <button onClick={onDown} style={btn} aria-label={`${label} down`}><Icon name="keyboard_arrow_down" size={22} /></button>
    </div>
  );
}

function AlarmSeg({ options, value, set }) {
  return (
    <div style={{ display: 'flex', gap: 4, background: 'var(--bg-2)', border: '1px solid var(--line-1)', borderRadius: 'var(--r-input)', padding: 4 }}>
      {options.map(o => {
        const on = value === o.v;
        return (
          <button key={o.v} onClick={() => set(o.v)} style={{
            flex: 1, padding: '9px 0', borderRadius: 8, border: on ? '1px solid var(--green)' : '1px solid transparent',
            background: on ? 'color-mix(in srgb, var(--green) 13%, transparent)' : 'transparent',
            color: on ? 'var(--green)' : 'var(--fg-3)', font: '700 12px var(--font-ui)', cursor: 'pointer',
          }}>{o.label}</button>
        );
      })}
    </div>
  );
}

function SmartAlarm() {
  const [a, setA] = useState(loadAlarm);
  useEffect(() => {
    try { localStorage.setItem('ow_alarm', JSON.stringify(a)); } catch (e) {}
  }, [a]);
  const set = (patch) => setA(prev => ({ ...prev, ...patch }));

  const bumpH = (d) => set({ h: (a.h + d + 24) % 24 });
  const bumpM = (d) => set({ m: (a.m + d + 60) % 60 });
  const toggleDay = (i) => set({ days: a.days.includes(i) ? a.days.filter(x => x !== i) : [...a.days, i].sort() });

  const wakeStart = (() => {
    let total = a.h * 60 + a.m - a.window;
    total = (total + 1440) % 1440;
    return `${pad2(Math.floor(total / 60))}:${pad2(total % 60)}`;
  })();
  const wakeEnd = `${pad2(a.h)}:${pad2(a.m)}`;
  const dayText = a.days.length === 7 ? 'Every day'
    : a.days.length === 0 ? 'Once'
    : a.days.length === 5 && a.days.every(d => d >= 1 && d <= 5) ? 'Weekdays'
    : a.days.length === 2 && a.days.includes(0) && a.days.includes(6) ? 'Weekends'
    : a.days.map(d => DAY_LETTERS[d]).join(' ');

  return (
    <Card>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon name="alarm" size={20} color={a.on ? 'var(--green)' : 'var(--fg-3)'} />
          <span style={{ font: '700 16px var(--font-ui)', color: 'var(--fg-1)' }}>Smart Alarm</span>
        </div>
        <Toggle on={a.on} set={(v) => set({ on: v })} />
      </div>

      <div style={{ opacity: a.on ? 1 : 0.4, pointerEvents: a.on ? 'auto' : 'none', transition: 'opacity 200ms' }}>
        {/* time editor */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 14, marginTop: 18 }}>
          <Stepper value={pad2(a.h)} onUp={() => bumpH(1)} onDown={() => bumpH(-1)} label="Hour" />
          <span style={{ font: '700 40px var(--font-display)', color: 'var(--fg-3)', lineHeight: 1, paddingBottom: 4 }}>:</span>
          <Stepper value={pad2(a.m)} onUp={() => bumpM(5)} onDown={() => bumpM(-5)} label="Minute" />
        </div>

        {/* smart wake window */}
        <div style={{ marginTop: 20 }}>
          <MicroLabel style={{ marginBottom: 10, color: 'var(--fg-3)' }}>Smart wake window</MicroLabel>
          <AlarmSeg
            options={[{ v: 0, label: 'Exact' }, { v: 15, label: '15 min' }, { v: 30, label: '30 min' }]}
            value={a.window} set={(v) => set({ window: v })} />
        </div>

        {/* repeat days */}
        <div style={{ marginTop: 20 }}>
          <MicroLabel style={{ marginBottom: 10, color: 'var(--fg-3)' }}>Repeat · {dayText}</MicroLabel>
          <div style={{ display: 'flex', gap: 7 }}>
            {DAY_LETTERS.map((ltr, i) => {
              const on = a.days.includes(i);
              return (
                <button key={i} onClick={() => toggleDay(i)} style={{
                  flex: 1, aspectRatio: '1', borderRadius: 999, cursor: 'pointer',
                  font: '700 13px var(--font-ui)',
                  background: on ? 'color-mix(in srgb, var(--green) 16%, transparent)' : 'var(--bg-2)',
                  border: on ? '1px solid var(--green)' : '1px solid var(--line-1)',
                  color: on ? 'var(--green)' : 'var(--fg-3)',
                }}>{ltr}</button>
              );
            })}
          </div>
        </div>

        {/* dynamic summary */}
        <div style={{ display: 'flex', gap: 10, marginTop: 18, paddingTop: 16, borderTop: '1px solid var(--line-1)' }}>
          <Icon name="watch" size={18} color="var(--fg-3)" style={{ flex: '0 0 auto', marginTop: 1 }} />
          <div style={{ font: '500 12px/1.5 var(--font-ui)', color: 'var(--fg-3)', textWrap: 'pretty' }}>
            {a.window > 0
              ? <>Wakes you with a silent wrist buzz between <b style={{ color: 'var(--fg-1)', fontWeight: 700 }}>{wakeStart}</b> and <b style={{ color: 'var(--fg-1)', fontWeight: 700 }}>{wakeEnd}</b>, at your lightest sleep.</>
              : <>Silent wrist buzz at exactly <b style={{ color: 'var(--fg-1)', fontWeight: 700 }}>{wakeEnd}</b>.</>}
            {' '}Fires from strap firmware even if the app is closed.
          </div>
        </div>
      </div>
    </Card>
  );
}
window.SleepScreen = SleepScreen;
