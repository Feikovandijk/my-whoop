/* Trends — range + metric selector, big chart, stats strip, small multiples, day list */
const METRICS = {
  Recovery: { color: 'var(--green)', unit: '%', get: d => d.recovery * 100, fmt: v => Math.round(v), min: 0, max: 100 },
  Strain:   { color: 'var(--blue)', unit: '', get: d => d.strain, fmt: v => v.toFixed(1) },
  HRV:      { color: 'var(--teal)', unit: 'ms', get: d => d.avgHrv, fmt: v => Math.round(v) },
  RHR:      { color: 'var(--red)', unit: 'bpm', get: d => d.restingHr, fmt: v => Math.round(v) },
};

function Seg({ options, value, set, accent = 'var(--green)' }) {
  return (
    <div style={{ display: 'flex', gap: 4, background: 'var(--bg-1)', border: '1px solid var(--line-1)', borderRadius: 'var(--r-input)', padding: 4 }}>
      {options.map(o => {
        const on = value === o;
        return (
          <button key={o} onClick={() => set(o)} style={{
            flex: 1, padding: '8px 0', borderRadius: 8, border: on ? `1px solid ${accent}` : '1px solid transparent',
            background: on ? `color-mix(in srgb, ${accent} 13%, transparent)` : 'transparent',
            color: on ? accent : 'var(--fg-3)', font: '700 12px var(--font-ui)', cursor: 'pointer',
          }}>{o}{typeof o === 'number' ? 'D' : ''}</button>
        );
      })}
    </div>
  );
}

function TrendsScreen() {
  const [range, setRange] = useState(30);
  const [metric, setMetric] = useState('Recovery');
  const M = METRICS[metric];
  const slice = OW.days.slice(-range);
  const pts = slice.map(M.get);
  const avg = pts.reduce((a, b) => a + b, 0) / pts.length;
  const stats = [
    { l: 'Avg', v: M.fmt(avg) }, { l: 'Min', v: M.fmt(Math.min(...pts)) },
    { l: 'Max', v: M.fmt(Math.max(...pts)) }, { l: 'Latest', v: M.fmt(pts[pts.length - 1]) },
  ];

  return (
    <Screen>
      <ScreenHead title="Trends" />
      <Seg options={[7, 30, 90]} value={range} set={setRange} accent={M.color} />
      <Seg options={Object.keys(METRICS)} value={metric} set={setMetric} accent={M.color} />

      {/* big chart */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 14 }}>
          <div>
            <MicroLabel>{metric} · {range} days</MicroLabel>
            <Stat value={M.fmt(avg)} unit={M.unit} size={32} color={M.color} style={{ marginTop: 8 }} />
            <div style={{ font: '500 11px var(--font-ui)', color: 'var(--fg-3)', marginTop: 4 }}>average</div>
          </div>
          <Icon name="monitoring" size={22} color="var(--fg-3)" />
        </div>
        <LineChart points={pts} color={M.color} height={170} min={M.min} max={M.max} />
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 16, paddingTop: 14, borderTop: '1px solid var(--line-1)' }}>
          {stats.map(s => (
            <div key={s.l} style={{ textAlign: 'center', flex: 1 }}>
              <MicroLabel style={{ fontSize: 9 }}>{s.l}</MicroLabel>
              <Stat value={s.v} size={20} style={{ marginTop: 6 }} />
            </div>
          ))}
        </div>
      </Card>

      {/* small multiples */}
      <SectionTitle>All metrics · {range}d</SectionTitle>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        {Object.entries(METRICS).map(([name, m]) => {
          const p = slice.map(m.get);
          return (
            <Card key={name} style={{ padding: 14 }} onClick={() => setMetric(name)}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                <MicroLabel style={{ fontSize: 10 }}>{name}</MicroLabel>
                <Stat value={m.fmt(p[p.length - 1])} unit={m.unit} size={18} color={m.color} />
              </div>
              <div style={{ marginTop: 10 }}><Sparkline points={p} color={m.color} height={36} /></div>
            </Card>
          );
        })}
      </div>

      {/* rolling HR */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <MicroLabel>Heart Rate · last 24h</MicroLabel>
          <Chip color="var(--red)">peak 152</Chip>
        </div>
        <LineChart points={OW.hrStream} color="var(--red)" height={90} dots={false} />
      </Card>

      {/* day list */}
      <SectionTitle>Daily log</SectionTitle>
      {slice.slice().reverse().slice(0, 8).map(m => {
        const rc = OW.recoveryColor(m.recovery);
        return (
          <Card key={m.day} style={{ padding: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                <Ring value={m.recovery} size={46} stroke={4} color={rc} animate={false}>
                  <span style={{ font: '700 13px var(--font-display)', color: 'var(--fg-1)' }}>{Math.round(m.recovery * 100)}</span>
                </Ring>
                <div>
                  <div style={{ font: '700 14px var(--font-ui)', color: 'var(--fg-1)' }}>{m.label}</div>
                  <div style={{ font: '500 11px var(--font-ui)', color: 'var(--fg-3)', marginTop: 2 }}>
                    HRV {Math.round(m.avgHrv)} · RHR {m.restingHr} · Resp {m.respRateBpm.toFixed(1)}
                  </div>
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <Stat value={m.strain.toFixed(1)} size={16} color="var(--blue)" />
                <div style={{ font: '500 11px var(--font-ui)', color: 'var(--fg-3)', marginTop: 3 }}>{OW.fmtDur(m.totalSleepMin)}</div>
              </div>
            </div>
          </Card>
        );
      })}
    </Screen>
  );
}
window.TrendsScreen = TrendsScreen;
