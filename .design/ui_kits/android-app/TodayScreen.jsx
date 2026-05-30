/* Today — hero recovery, strain, sleep, dense signal grid */
function TodayScreen({ connected, hr, battery }) {
  const d = OW.latest;
  const prev = OW.days[OW.days.length - 2];
  const rc = OW.recoveryColor(d.recovery);
  const strainDelta = d.strain - prev.strain;

  const drivers = [
    { l: 'HRV', v: Math.round(d.avgHrv), u: 'ms', good: d.avgHrv > prev.avgHrv },
    { l: 'Resting HR', v: d.restingHr, u: 'bpm', good: d.restingHr < prev.restingHr },
    { l: 'Resp Rate', v: d.respRateBpm.toFixed(1), u: 'rpm', good: true },
    { l: 'Sleep', v: OW.fmtDur(d.totalSleepMin), u: '', good: d.totalSleepMin > 420 },
  ];
  const signals = [
    { l: 'SpO₂', v: d.spo2Pct.toFixed(1), u: '%', icon: 'water_drop', c: 'var(--teal)' },
    { l: 'Skin Temp', v: (d.skinTempDevC >= 0 ? '+' : '') + d.skinTempDevC.toFixed(1), u: '°C', icon: 'device_thermostat', c: 'var(--yellow)' },
    { l: 'HRV', v: Math.round(d.avgHrv), u: 'ms', icon: 'monitoring', c: 'var(--blue)' },
    { l: 'Resting HR', v: d.restingHr, u: 'bpm', icon: 'favorite', c: 'var(--red)' },
  ];

  return (
    <Screen>
      <ScreenHead title="Today" right={<span style={{ font: '700 13px var(--font-ui)', color: 'var(--fg-3)' }}>{d.label}</span>} />

      {/* connection strip */}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        {connected
          ? <><Chip color="var(--green)" dot>Strap connected</Chip>
              <Chip color="var(--red)" dot>{hr} bpm live</Chip>
              <Chip color="var(--blue)">{battery}%</Chip></>
          : <Chip color="var(--fg-3)" dot>Strap offline · showing synced data</Chip>}
      </div>

      {/* HERO recovery */}
      <Card glow={d.recovery >= 0.66 ? 'green' : null} style={{ padding: '26px 20px 22px' }}>
        <div style={{ display: 'flex', justifyContent: 'center' }}>
          <Ring value={d.recovery} size={206} stroke={15} color={rc}>
            <Stat value={Math.round(d.recovery * 100)} unit="%" size={62} />
            <MicroLabel color={rc} style={{ marginTop: 4 }}>Recovery · {OW.recoveryWord(d.recovery)}</MicroLabel>
          </Ring>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 8, marginTop: 20, paddingTop: 18, borderTop: '1px solid var(--line-1)' }}>
          {drivers.map(x => (
            <div key={x.l} style={{ textAlign: 'center' }}>
              <Stat value={x.v} unit={x.u} size={x.l === 'Sleep' ? 17 : 22} />
              <MicroLabel style={{ marginTop: 6, fontSize: 9, letterSpacing: 0.8 }}>{x.l}</MicroLabel>
            </div>
          ))}
        </div>
      </Card>

      {/* Day strain */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <MicroLabel>Day Strain</MicroLabel>
            <div style={{ font: '500 12px var(--font-ui)', color: 'var(--fg-3)', marginTop: 5 }}>
              {strainDelta >= 0 ? '▲' : '▼'} {Math.abs(strainDelta).toFixed(1)} vs yesterday
            </div>
          </div>
          <Stat value={d.strain.toFixed(1)} size={44} color="var(--blue)" />
        </div>
        <div style={{ marginTop: 14 }}>
          <Bar frac={d.strain / 21} height={10} gradient="linear-gradient(90deg,var(--blue),var(--green))" />
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6 }}>
            <span style={{ font: '500 10px var(--font-mono)', color: 'var(--fg-3)' }}>0</span>
            <span style={{ font: '500 10px var(--font-mono)', color: 'var(--fg-3)' }}>21 · ALL OUT</span>
          </div>
        </div>
      </Card>

      {/* Last night sleep */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ flex: 1 }}>
            <MicroLabel>Last Night</MicroLabel>
            <Stat value={OW.fmtDur(d.totalSleepMin)} size={28} style={{ marginTop: 8, color: 'var(--blue)' }} />
            <div style={{ font: '500 12px var(--font-ui)', color: 'var(--fg-3)', marginTop: 6 }}>
              {Math.round(d.totalSleepMin / d.sleepNeedMin * 100)}% of {OW.fmtDur(d.sleepNeedMin)} needed
            </div>
          </div>
          <Ring value={d.efficiency} size={84} stroke={8} color="var(--blue)">
            <Stat value={Math.round(d.efficiency * 100)} unit="%" size={22} />
            <MicroLabel style={{ marginTop: 3, fontSize: 8 }}>Effic.</MicroLabel>
          </Ring>
        </div>
      </Card>

      {/* dense signals grid */}
      <SectionTitle>Recovery signals</SectionTitle>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        {signals.map(s => (
          <Card key={s.l} style={{ padding: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <MicroLabel style={{ fontSize: 10 }}>{s.l}</MicroLabel>
              <Icon name={s.icon} size={18} color={s.c} />
            </div>
            <Stat value={s.v} unit={s.u} size={28} style={{ marginTop: 10 }} />
          </Card>
        ))}
      </div>
    </Screen>
  );
}
window.TodayScreen = TodayScreen;
