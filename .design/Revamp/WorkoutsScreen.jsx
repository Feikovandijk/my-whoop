/* Workouts — weekly summary + expandable workout cards with HR-zone breakdown */
function fmtClock(ts) {
  return new Date(ts * 1000).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })
    + ', ' + new Date(ts * 1000).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
}

function strainColor(s) { return s >= 14 ? 'var(--yellow)' : s >= 10 ? 'var(--green)' : 'var(--blue)'; }

const KIND_ICON = { Running: 'directions_run', Strength: 'fitness_center', Cycling: 'directions_bike', Swimming: 'pool', Walking: 'directions_walk' };

function WorkoutCard({ w }) {
  const [open, setOpen] = useState(false);
  const sc = strainColor(w.strain);
  const mins = Math.round(w.durationS / 60);
  const kcal = w.caloriesKcal ? `${w.caloriesKcal}` : '—';
  return (
    <Card style={{ padding: 16 }} onClick={() => setOpen(!open)}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 38, height: 38, borderRadius: 12, background: `color-mix(in srgb, ${sc} 14%, transparent)`, display: 'grid', placeItems: 'center' }}>
              <Icon name={KIND_ICON[w.kind] || 'exercise'} size={20} color={sc} />
            </div>
            <div>
              <div style={{ font: '700 16px var(--font-ui)', color: 'var(--fg-1)' }}>{w.kind}</div>
              <div style={{ font: '500 11px var(--font-ui)', color: 'var(--fg-3)', marginTop: 2 }}>{fmtClock(w.startTs)}</div>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 22, marginTop: 14 }}>
            {[['Duration', `${mins}m`], ['Avg HR', `${w.avgHr}`], ['Calories', kcal]].map(([l, v]) => (
              <div key={l}>
                <MicroLabel style={{ fontSize: 9, letterSpacing: 0.5 }}>{l}</MicroLabel>
                <Stat value={v} size={18} style={{ marginTop: 5 }} />
              </div>
            ))}
          </div>
        </div>
        <div style={{ borderRadius: 999, padding: '8px 14px', background: `color-mix(in srgb, ${sc} 15%, transparent)`, border: `1px solid ${sc}` }}>
          <Stat value={w.strain.toFixed(1)} size={16} color={sc} />
        </div>
      </div>
      {open && (
        <div style={{ marginTop: 16, paddingTop: 14, borderTop: '1px solid var(--line-1)' }}>
          <MicroLabel style={{ marginBottom: 10 }}>HR zone breakdown</MicroLabel>
          <ZoneRail zones={w.zones} />
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12 }}>
            {[['Avg %HRR', `${w.avgHrrPct}%`], ['Peak HR', `${w.peakHr}`], ['HRmax', `${w.hrmax}`]].map(([l, v]) => (
              <div key={l} style={{ textAlign: 'center' }}>
                <MicroLabel style={{ fontSize: 9 }}>{l}</MicroLabel>
                <Stat value={v} size={16} style={{ marginTop: 5 }} />
              </div>
            ))}
          </div>
        </div>
      )}
    </Card>
  );
}

function WorkoutsScreen() {
  const ws = OW.workouts;
  const totalMin = Math.round(ws.reduce((a, w) => a + w.durationS, 0) / 60);
  const totalKcal = ws.reduce((a, w) => a + (w.caloriesKcal || 0), 0);
  const avgStrain = ws.reduce((a, w) => a + w.strain, 0) / ws.length;
  const summary = [
    { l: 'Activities', v: ws.length, c: 'var(--fg-1)' },
    { l: 'Avg Strain', v: avgStrain.toFixed(1), c: 'var(--blue)' },
    { l: 'Total Time', v: `${Math.floor(totalMin/60)}h ${totalMin%60}m`, c: 'var(--fg-1)' },
    { l: 'Calories', v: totalKcal, c: 'var(--green)' },
  ];
  return (
    <Screen>
      <ScreenHead title="Workouts" right={<span style={{ font: '700 13px var(--font-ui)', color: 'var(--fg-3)' }}>This week</span>} />
      <Card>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px 8px' }}>
          {summary.map(s => (
            <div key={s.l}>
              <MicroLabel style={{ fontSize: 10 }}>{s.l}</MicroLabel>
              <Stat value={s.v} size={28} color={s.c} style={{ marginTop: 8 }} />
            </div>
          ))}
        </div>
      </Card>
      <SectionTitle>Auto-detected · tap to expand</SectionTitle>
      {ws.map((w, i) => <WorkoutCard key={i} w={w} />)}
      <div style={{ font: '500 11px var(--font-ui)', color: 'var(--fg-3)', textAlign: 'center', padding: '4px 20px', lineHeight: 1.5 }}>
        Strain is cardiovascular load only until calibrated against WHOOP reference data.
      </div>
    </Screen>
  );
}
window.WorkoutsScreen = WorkoutsScreen;
