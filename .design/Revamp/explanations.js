// OpenWhoop — metric explanation registry.
// The "how this is calculated" content surfaced when a user taps a metric.
// Voice: precise, technical, honest, never hype. Every uncertain metric is
// labelled uncertain. Numbers carry units. No emoji, no exclamation marks.
// Attached to window.OW.explain — built against OW.latest + the prior day.
(function () {
  const d = OW.latest;
  const prev = OW.days[OW.days.length - 2];
  const rWord = OW.recoveryWord(d.recovery);
  const rCol = OW.recoveryColor(d.recovery);

  // small helpers
  const sgn = (n, dp = 0) => (n >= 0 ? '+' : '−') + Math.abs(n).toFixed(dp);
  const hrvDelta = Math.round(d.avgHrv - prev.avgHrv);
  const rhrDelta = d.restingHr - prev.restingHr;

  // Each entry:
  //   label   — UPPERCASE micro-label
  //   title   — sheet title
  //   value/unit/color — hero readout in the sheet header
  //   band    — { text, color } status chip
  //   status  — plain-language read of the number (the one allowed warmth)
  //   viz     — { kind:'factors', factors:[{l,v,note,contrib,col}] } | {kind:'stages'} | {kind:'gauge',...}
  //   method  — mono formula line
  //   inputs  — mono inputs line
  //   limit   — honesty caveat
  //   guidance— what you can do (optional)
  const E = {
    recovery: {
      label: 'Recovery',
      title: 'Recovery',
      value: Math.round(d.recovery * 100), unit: '%', color: rCol,
      band: { text: rWord, color: rCol },
      status: rWord === 'High'
        ? 'Your autonomic markers cleared overnight. Your body is primed to take on strain today.'
        : rWord === 'Moderate'
        ? 'Partially recovered. You can train, but keep an eye on how you respond to load.'
        : 'Your nervous system is still under load. Favour rest or light activity today.',
      viz: {
        kind: 'factors',
        caption: 'What moved your score',
        factors: [
          { l: 'HRV', v: `${Math.round(d.avgHrv)} ms`, note: hrvDelta >= 0 ? `${sgn(hrvDelta)} ms vs baseline` : `${sgn(hrvDelta)} ms vs baseline`, contrib: Math.max(-1, Math.min(1, hrvDelta / 18)), col: 'var(--teal)' },
          { l: 'Resting HR', v: `${d.restingHr} bpm`, note: `${sgn(rhrDelta)} bpm vs baseline`, contrib: Math.max(-1, Math.min(1, -rhrDelta / 6)), col: 'var(--red)' },
          { l: 'Sleep', v: OW.fmtDur(d.totalSleepMin), note: `${Math.round(d.totalSleepMin / d.sleepNeedMin * 100)}% of need`, contrib: Math.max(-1, Math.min(1, (d.totalSleepMin / d.sleepNeedMin - 0.85) * 3)), col: 'var(--blue)' },
          { l: 'Resp Rate', v: `${d.respRateBpm.toFixed(1)} rpm`, note: 'within normal range', contrib: 0.08, col: 'var(--purple)' },
        ],
      },
      method: 'weighted(HRV·0.5 + RHR·0.25 + sleep·0.15 + resp·0.10) → 0–100',
      inputs: `${(8420).toLocaleString()} HR samples · ${OW.fmtDur(d.totalSleepMin)} sleep · 1 night`,
      limit: 'Recovery is relative to your own 30-day baseline, not a population norm. The first 30 days read “Pending” while the baseline builds.',
      guidance: rWord === 'High'
        ? 'A good day to target a higher strain. Your suggested strain ceiling is 16.0.'
        : rWord === 'Moderate'
        ? 'Moderate training is fine. Suggested strain ceiling is 12.5.'
        : 'Prioritise recovery. Keep strain under 8.0 and protect tonight’s sleep.',
    },

    strain: {
      label: 'Day Strain',
      title: 'Day Strain',
      value: d.strain.toFixed(1), unit: '/ 21', color: 'var(--blue)',
      band: { text: d.strain >= 14 ? 'Strenuous' : d.strain >= 10 ? 'Moderate' : 'Light', color: 'var(--blue)' },
      status: `You have accumulated ${d.strain.toFixed(1)} of cardiovascular load today. Strain rises logarithmically — each point higher is harder to reach than the last.`,
      viz: {
        kind: 'gauge',
        caption: 'Strain scale (0–21)',
        zones: [
          { l: 'Light', from: 0, to: 10, col: 'var(--zone-1)' },
          { l: 'Moderate', from: 10, to: 14, col: 'var(--zone-3)' },
          { l: 'Strenuous', from: 14, to: 18, col: 'var(--zone-4)' },
          { l: 'All out', from: 18, to: 21, col: 'var(--zone-5)' },
        ],
        value: d.strain, max: 21,
      },
      method: 'HRR + Edwards TRIMP → logarithmic 0–21 scale',
      inputs: '8,420 HR samples · 1 exercise session · time in zones Z0–Z5',
      limit: 'Strain is cardiovascular load only until calibrated against WHOOP reference data. It does not yet account for muscular or metabolic load.',
      guidance: `Your recovery suggests a ceiling near ${d.recovery >= 0.66 ? '16.0' : d.recovery >= 0.34 ? '12.5' : '8.0'}. You are ${d.strain > (d.recovery >= 0.66 ? 16 : d.recovery >= 0.34 ? 12.5 : 8) ? 'above' : 'below'} that target.`,
    },

    sleep: {
      label: 'Last Night',
      title: 'Sleep Performance',
      value: OW.fmtDur(d.totalSleepMin), unit: '', color: 'var(--blue)',
      band: { text: `${Math.round(d.totalSleepMin / d.sleepNeedMin * 100)}% of need`, color: 'var(--blue)' },
      status: d.totalSleepMin / d.sleepNeedMin > 0.9
        ? 'You met most of your sleep need. Deep and REM stages were well represented.'
        : 'You fell short of your sleep need. Consider an earlier bedtime tonight to close the debt.',
      viz: { kind: 'stages' },
      method: 'staged from movement + HR + HRV; need = baseline + strain debt + sleep debt',
      inputs: `${OW.fmtDur(d.totalSleepMin)} asleep · ${d.disturbances} disturbances · 23:18 → 07:06`,
      limit: 'Stage classification is derived from wrist motion and cardiac signals, not EEG. Treat stage minutes as estimates.',
      guidance: `To fully meet need you would target ${OW.fmtDur(d.sleepNeedMin)} in bed. Tonight’s suggested bedtime is 22:54.`,
    },

    efficiency: {
      label: 'Sleep Efficiency',
      title: 'Sleep Efficiency',
      value: Math.round(d.efficiency * 100), unit: '%', color: 'var(--blue)',
      band: { text: d.efficiency >= 0.9 ? 'Highly efficient' : 'Efficient', color: 'var(--blue)' },
      status: 'Efficiency is the share of time in bed that you spent asleep. Higher means less time awake or restless.',
      viz: {
        kind: 'factors',
        caption: 'Time in bed',
        factors: [
          { l: 'Asleep', v: OW.fmtDur(d.totalSleepMin), note: `${Math.round(d.efficiency * 100)}% of time in bed`, contrib: 1, col: 'var(--blue)' },
          { l: 'Awake', v: `${d.disturbances * 3}m`, note: `${d.disturbances} disturbances`, contrib: -0.4, col: 'var(--stage-awake)' },
          { l: 'Latency', v: '11m', note: 'time to fall asleep', contrib: -0.2, col: 'var(--fg-3)' },
        ],
      },
      method: 'time asleep ÷ time in bed × 100',
      inputs: `${OW.fmtDur(d.totalSleepMin)} asleep ÷ ${OW.fmtDur(d.totalSleepMin + d.disturbances * 3 + 11)} in bed`,
      limit: 'Brief micro-wakings under 90s may not be detected and are not counted as disturbances.',
    },

    hrv: {
      label: 'HRV',
      title: 'Heart Rate Variability',
      value: Math.round(d.avgHrv), unit: 'ms', color: 'var(--teal)',
      band: { text: hrvDelta >= 0 ? `${sgn(hrvDelta)} ms` : `${sgn(hrvDelta)} ms`, color: hrvDelta >= 0 ? 'var(--green)' : 'var(--yellow)' },
      status: hrvDelta >= 0
        ? 'HRV is above your recent baseline — a sign your nervous system is well recovered.'
        : 'HRV is below your recent baseline, which can follow strain, alcohol, illness, or short sleep.',
      viz: {
        kind: 'gauge',
        caption: 'vs 30-day baseline',
        zones: [
          { l: 'Low', from: 30, to: 55, col: 'var(--yellow)' },
          { l: 'Normal', from: 55, to: 85, col: 'var(--teal)' },
          { l: 'High', from: 85, to: 120, col: 'var(--green)' },
        ],
        value: d.avgHrv, max: 120, min: 30,
      },
      method: 'rMSSD of RR intervals during slow-wave sleep',
      inputs: 'RR intervals from deepest 5 min of SWS · PPG-derived',
      limit: 'HRV is highly individual. Compare it to your own baseline, never to another person’s.',
    },

    rhr: {
      label: 'Resting HR',
      title: 'Resting Heart Rate',
      value: d.restingHr, unit: 'bpm', color: 'var(--red)',
      band: { text: rhrDelta <= 0 ? `${sgn(rhrDelta)} bpm` : `${sgn(rhrDelta)} bpm`, color: rhrDelta <= 0 ? 'var(--green)' : 'var(--yellow)' },
      status: rhrDelta <= 0
        ? 'Resting HR is at or below baseline — a positive recovery signal.'
        : 'Resting HR is elevated, which often accompanies incomplete recovery or oncoming illness.',
      viz: {
        kind: 'gauge',
        caption: 'vs 30-day baseline',
        zones: [
          { l: 'Low', from: 38, to: 50, col: 'var(--green)' },
          { l: 'Normal', from: 50, to: 62, col: 'var(--teal)' },
          { l: 'Elevated', from: 62, to: 75, col: 'var(--yellow)' },
        ],
        value: d.restingHr, max: 75, min: 38,
      },
      method: 'lowest sustained HR during slow-wave sleep',
      inputs: 'continuous PPG HR · floor over the night',
      limit: 'A late meal, alcohol, or a warm room can raise RHR independent of fitness.',
    },

    resp: {
      label: 'Resp Rate',
      title: 'Respiratory Rate',
      value: d.respRateBpm.toFixed(1), unit: 'rpm', color: 'var(--purple)',
      band: { text: 'Stable', color: 'var(--green)' },
      status: 'Respiratory rate is one of the most stable nightly signals. A sudden rise of more than 1 rpm can be an early illness marker.',
      viz: {
        kind: 'gauge',
        caption: 'breaths per minute',
        zones: [
          { l: 'Typical', from: 12, to: 18, col: 'var(--purple)' },
          { l: 'Elevated', from: 18, to: 22, col: 'var(--yellow)' },
        ],
        value: d.respRateBpm, max: 22, min: 12,
      },
      method: 'cardiac modulation of PPG waveform during sleep',
      inputs: 'PPG waveform · averaged across the night',
      limit: 'Derived from the optical pulse signal, not a chest band. Accuracy degrades with poor skin contact.',
    },

    spo2: {
      label: 'SpO₂',
      title: 'Blood Oxygen',
      value: d.spo2Pct.toFixed(1), unit: '%', color: 'var(--teal)',
      band: { text: 'Normal', color: 'var(--green)' },
      status: 'Overnight blood-oxygen saturation. Sustained readings under 90% can indicate disturbed breathing and are worth discussing with a clinician.',
      viz: {
        kind: 'gauge',
        caption: 'overnight saturation',
        zones: [
          { l: 'Low', from: 88, to: 94, col: 'var(--yellow)' },
          { l: 'Normal', from: 94, to: 100, col: 'var(--teal)' },
        ],
        value: d.spo2Pct, max: 100, min: 88,
      },
      method: 'red/infrared PPG ratio-of-ratios during sleep',
      inputs: 'dual-wavelength PPG · sampled through the night',
      limit: 'Experimental. Wrist SpO₂ is not a medical pulse oximeter and should not be used for diagnosis.',
    },

    skintemp: {
      label: 'Skin Temp',
      title: 'Skin Temperature',
      value: (d.skinTempDevC >= 0 ? '+' : '') + d.skinTempDevC.toFixed(1), unit: '°C', color: 'var(--yellow)',
      band: { text: 'Deviation from baseline', color: 'var(--yellow)' },
      status: 'Shown as a deviation from your personal baseline rather than an absolute. Larger swings can track illness, cycle phase, or a warm sleep environment.',
      viz: {
        kind: 'gauge',
        caption: 'deviation from baseline',
        zones: [
          { l: 'Cooler', from: -2, to: -0.5, col: 'var(--blue)' },
          { l: 'Baseline', from: -0.5, to: 0.5, col: 'var(--teal)' },
          { l: 'Warmer', from: 0.5, to: 2, col: 'var(--yellow)' },
        ],
        value: d.skinTempDevC, max: 2, min: -2,
      },
      method: 'wrist thermistor − 30-day baseline mean',
      inputs: 'skin thermistor · averaged over the night',
      limit: 'Skin temperature is not core body temperature. Ambient conditions strongly influence it.',
    },
  };

  OW.explain = E;
})();
