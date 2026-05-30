/* Today — enriched: hero recovery, strain w/ recovery ceiling, sleep,
   and a dense signals grid where every tile explains itself on tap. */
function TodayScreen({ connected, hr, battery, density = 'regular' }) {
  const d = OW.latest;
  const prev = OW.days[OW.days.length - 2];
  const last7 = OW.days.slice(-7);
  const rc = OW.recoveryColor(d.recovery);
  const strainDelta = d.strain - prev.strain;
  const ceiling = d.recovery >= 0.66 ? 16 : d.recovery >= 0.34 ? 12.5 : 8;

  const gap = density === 'compact' ? 11 : density === 'comfy' ? 18 : 14;
  const showSpark = density !== 'compact';

  const drivers = [
    { l: 'HRV', v: Math.round(d.avgHrv), u: 'ms' },
    { l: 'Resting HR', v: d.restingHr, u: 'bpm' },
    { l: 'Resp', v: d.respRateBpm.toFixed(1), u: 'rpm' },
    { l: 'Sleep', v: OW.fmtDur(d.totalSleepMin), u: '' },
  ];

  const signals = [
    { k: 'hrv', l: 'HRV', v: Math.round(d.avgHrv), u: 'ms', icon: 'monitoring', c: 'var(--teal)', series: last7.map(x => x.avgHrv), delta: Math.round(d.avgHrv - prev.avgHrv), goodUp: true },
    { k: 'rhr', l: 'Resting HR', v: d.restingHr, u: 'bpm', icon: 'ecg_heart', c: 'var(--red)', series: last7.map(x => x.restingHr), delta: d.restingHr - prev.restingHr, goodUp: false },
    { k: 'resp', l: 'Resp Rate', v: d.respRateBpm.toFixed(1), u: 'rpm', icon: 'pulmonology', c: 'var(--purple)', series: last7.map(x => x.respRateBpm), delta: +(d.respRateBpm - prev.respRateBpm).toFixed(1), goodUp: false },
    { k: 'spo2', l: 'SpO₂', v: d.spo2Pct.toFixed(1), u: '%', icon: 'water_drop', c: 'var(--teal)', series: last7.map(x => x.spo2Pct), delta: +(d.spo2Pct - prev.spo2Pct).toFixed(1), goodUp: true },
    { k: 'skintemp', l: 'Skin Temp', v: (d.skinTempDevC >= 0 ? '+' : '') + d.skinTempDevC.toFixed(1), u: '°C', icon: 'device_thermostat', c: 'var(--yellow)', series: last7.map(x => x.skinTempDevC), delta: +(d.skinTempDevC - prev.skinTempDevC).toFixed(1), goodUp: null },
    { k: 'efficiency', l: 'Sleep Effic.', v: Math.round(d.efficiency * 100), u: '%', icon: 'bedtime', c: 'var(--blue)', series: last7.map(x => x.efficiency * 100), delta: Math.round((d.efficiency - prev.efficiency) * 100), goodUp: true },
  ];

  return (
    <Screen style={{ gap }}>
      <ScreenHead title="Today" right={<span style={{ font: '700 13px var(--font-ui)', color: 'var(--fg-3)' }}>{d.label}</span>} />

      {/* connection strip */}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        {connected
          ? <><Chip color="var(--green)" dot>Strap connected</Chip>
              <Chip color="var(--red)" dot>{hr} bpm live</Chip>
              <Chip color="var(--blue)">{battery}%</Chip></>
          : <Chip color="var(--fg-3)" dot>Strap offline · showing synced data</Chip>}
      </div>

      {/* HERO recovery */}
      <Card glow={d.recovery >= 0.66 ? 'green' : null} style={{ padding: '22px 20px 20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <MicroLabel color={rc}>Recovery · {OW.recoveryWord(d.recovery)}</MicroLabel>
          <ExplainButton metric="recovery" color={rc} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 6 }}>
          <Ring value={d.recovery} size={196} stroke={15} color={rc}>
            <Stat value={Math.round(d.recovery * 100)} unit="%" size={60} />
            <MicroLabel style={{ marginTop: 4, color: 'var(--fg-3)' }}>readiness</MicroLabel>
          </Ring>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 8, marginTop: 18, paddingTop: 16, borderTop: '1px solid var(--line-1)' }}>
          {drivers.map(x => (
            <div key={x.l} style={{ textAlign: 'center' }}>
              <Stat value={x.v} unit={x.u} size={x.l === 'Sleep' ? 16 : 21} />
              <MicroLabel style={{ marginTop: 6, fontSize: 9, letterSpacing: 0.8, color: 'var(--fg-3)' }}>{x.l}</MicroLabel>
            </div>
          ))}
        </div>
      </Card>

      {/* Day strain w/ recovery ceiling */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <MicroLabel>Day Strain</MicroLabel>
            <ExplainButton metric="strain" color="var(--blue)" />
          </div>
          <div style={{ textAlign: 'right' }}>
            <Stat value={d.strain.toFixed(1)} size={40} color="var(--blue)" />
            <div style={{ font: '500 11px var(--font-ui)', color: 'var(--fg-3)', marginTop: 2 }}>
              {strainDelta >= 0 ? '▲' : '▼'} {Math.abs(strainDelta).toFixed(1)} vs yesterday
            </div>
          </div>
        </div>
        <div style={{ marginTop: 16, position: 'relative' }}>
          <Bar frac={d.strain / 21} height={10} gradient="linear-gradient(90deg,var(--blue),var(--green))" />
          {/* recovery ceiling marker */}
          <div style={{ position: 'absolute', top: -5, left: `${ceiling / 21 * 100}%`, transform: 'translateX(-50%)', width: 2, height: 20, background: 'var(--fg-1)', borderRadius: 2 }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8 }}>
            <span style={{ font: '500 10px var(--font-mono)', color: 'var(--fg-3)' }}>0</span>
            <span style={{ font: '600 10px var(--font-ui)', color: 'var(--fg-2)' }}>ceiling {ceiling.toFixed(1)}</span>
            <span style={{ font: '500 10px var(--font-mono)', color: 'var(--fg-3)' }}>21</span>
          </div>
        </div>
      </Card>

      {/* Last night sleep */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <MicroLabel>Last Night</MicroLabel>
              <ExplainButton metric="sleep" color="var(--blue)" />
            </div>
            <Stat value={OW.fmtDur(d.totalSleepMin)} size={28} style={{ marginTop: 10, color: 'var(--blue)' }} />
            <div style={{ font: '500 12px var(--font-ui)', color: 'var(--fg-3)', marginTop: 6 }}>
              {Math.round(d.totalSleepMin / d.sleepNeedMin * 100)}% of {OW.fmtDur(d.sleepNeedMin)} needed
            </div>
          </div>
          <Ring value={d.efficiency} size={84} stroke={8} color="var(--blue)">
            <Stat value={Math.round(d.efficiency * 100)} unit="%" size={22} />
            <MicroLabel style={{ marginTop: 3, fontSize: 8, color: 'var(--fg-3)' }}>Effic.</MicroLabel>
          </Ring>
        </div>
      </Card>

      {/* dense signals grid — every tile explains itself */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '2px 2px 0' }}>
        <SectionTitle style={{ margin: 0 }}>Recovery signals</SectionTitle>
        <span style={{ font: '500 11px var(--font-ui)', color: 'var(--fg-3)' }}>tap any to explain</span>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap }}>
        {signals.map(s => {
          const deltaGood = s.goodUp == null ? null : (s.delta === 0 ? null : (s.delta > 0) === s.goodUp);
          const dc = deltaGood == null ? 'var(--fg-3)' : deltaGood ? 'var(--green-dim)' : 'var(--yellow)';
          return (
            <Explainable key={s.k} metric={s.k}>
              <Card style={{ padding: 15 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Icon name={s.icon} size={17} color={s.c} />
                  <MicroLabel style={{ fontSize: 9.5, letterSpacing: 0.6 }}>{s.l}</MicroLabel>
                </div>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 7, marginTop: 11 }}>
                  <Stat value={s.v} unit={s.u} size={26} />
                </div>
                <div style={{ font: '600 11px var(--font-ui)', color: dc, marginTop: 4 }}>
                  {s.delta === 0 ? '±0' : (s.delta > 0 ? '▲ ' : '▼ ') + Math.abs(s.delta) + ' ' + s.u} <span style={{ color: 'var(--fg-3)', fontWeight: 500 }}>7d</span>
                </div>
                {showSpark && <div style={{ marginTop: 8 }}><Sparkline points={s.series} color={s.c} height={26} /></div>}
              </Card>
            </Explainable>
          );
        })}
      </div>
    </Screen>
  );
}
window.TodayScreen = TodayScreen;
