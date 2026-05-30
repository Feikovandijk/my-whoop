/* OpenWhoop — the "explain it" system.
   A tappable affordance on any metric opens a bottom sheet that reads the
   metric in plain language, shows what moved it, then the Method / Inputs /
   Limit the design system mandates. Honest, technical, no hype. */
const ExplainCtx = React.createContext(null);
const useExplain = () => React.useContext(ExplainCtx);

/* Provider — owns which metric is open + sheet style. Renders the sheet,
   absolutely positioned to cover the phone screen it's mounted in. */
function ExplainProvider({ children, sheetStyle = 'sheet' }) {
  const [key, setKey] = useState(null);
  const open = (k) => setKey(k);
  const close = () => setKey(null);
  return (
    <ExplainCtx.Provider value={{ open, close }}>
      {children}
      <ExplainSheet metricKey={key} onClose={close} sheetStyle={sheetStyle} />
    </ExplainCtx.Provider>
  );
}

/* Small round (i) button to drop into a card header. */
function ExplainButton({ metric, color = 'var(--fg-3)' }) {
  const { open } = useExplain();
  return (
    <button onClick={(e) => { e.stopPropagation(); open(metric); }} aria-label="Explain this metric" style={{
      width: 26, height: 26, borderRadius: 999, flex: '0 0 auto', cursor: 'pointer',
      display: 'grid', placeItems: 'center', padding: 0,
      background: 'var(--bg-2)', border: '1px solid var(--line-2)', color,
    }}>
      <Icon name="info" size={15} />
    </button>
  );
}

/* Whole-element tap target that opens the sheet (used on signal tiles). */
function Explainable({ metric, children, style }) {
  const { open } = useExplain();
  return (
    <div onClick={() => open(metric)} style={{ cursor: 'pointer', position: 'relative', ...style }}>
      {children}
      <span style={{ position: 'absolute', top: 12, right: 12, color: 'var(--fg-3)', display: 'grid', placeItems: 'center' }}>
        <Icon name="info" size={15} />
      </span>
    </div>
  );
}

/* ---- visualizations ---------------------------------------------------- */

/* Diverging contributing-factor bars: how each input pushed the score. */
function FactorBars({ caption, factors }) {
  return (
    <div>
      <MicroLabel style={{ marginBottom: 14, color: 'var(--fg-3)' }}>{caption}</MicroLabel>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {factors.map((f, i) => {
          const pos = f.contrib >= 0;
          const mag = Math.min(1, Math.abs(f.contrib)) * 50; // % of half-track
          return (
            <div key={i}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 7 }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 8, font: '600 13px var(--font-ui)', color: 'var(--fg-1)' }}>
                  <span style={{ width: 9, height: 9, borderRadius: 2, background: f.col }} />{f.l}
                </span>
                <span style={{ font: '700 14px var(--font-display)', color: 'var(--fg-1)', fontVariantNumeric: 'tabular-nums' }}>{f.v}</span>
              </div>
              {/* diverging track with centre line */}
              <div style={{ position: 'relative', height: 8, borderRadius: 4, background: 'rgba(255,255,255,0.06)', overflow: 'hidden' }}>
                <div style={{ position: 'absolute', left: '50%', top: 0, bottom: 0, width: 1, background: 'var(--line-2)' }} />
                <div style={{ position: 'absolute', top: 0, bottom: 0, borderRadius: 4, background: f.col,
                  left: pos ? '50%' : `${50 - mag}%`, width: `${mag}%`,
                  transition: 'width 700ms var(--ease-out), left 700ms var(--ease-out)' }} />
              </div>
              <div style={{ font: '500 11px var(--font-ui)', color: pos ? 'var(--green-dim)' : 'var(--fg-3)', marginTop: 6 }}>
                {f.note}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* Zone gauge: a segmented scale with a marker at the current value. */
function ZoneGauge({ caption, zones, value, min = 0, max }) {
  const lo = min, hi = max;
  const pct = (v) => Math.max(0, Math.min(1, (v - lo) / (hi - lo))) * 100;
  const span = hi - lo;
  return (
    <div>
      <MicroLabel style={{ marginBottom: 14, color: 'var(--fg-3)' }}>{caption}</MicroLabel>
      <div style={{ position: 'relative', paddingTop: 22 }}>
        {/* marker */}
        <div style={{ position: 'absolute', top: 0, left: `${pct(value)}%`, transform: 'translateX(-50%)', textAlign: 'center', transition: 'left 800ms var(--ease-out)' }}>
          <div style={{ font: '700 13px var(--font-display)', color: 'var(--fg-1)', fontVariantNumeric: 'tabular-nums' }}>
            {typeof value === 'number' ? (value % 1 ? value.toFixed(1) : value) : value}
          </div>
        </div>
        <div style={{ display: 'flex', height: 12, borderRadius: 6, overflow: 'hidden', gap: 2 }}>
          {zones.map((z, i) => (
            <div key={i} style={{ flex: (z.to - z.from) / span, background: `color-mix(in srgb, ${z.col} 38%, transparent)` }} />
          ))}
        </div>
        {/* needle */}
        <div style={{ position: 'absolute', top: 22, left: `${pct(value)}%`, transform: 'translateX(-50%)', width: 3, height: 12, borderRadius: 2, background: 'var(--fg-1)', boxShadow: '0 0 0 2px var(--bg-1)', transition: 'left 800ms var(--ease-out)' }} />
      </div>
      <div style={{ display: 'flex', marginTop: 10, gap: 2 }}>
        {zones.map((z, i) => (
          <div key={i} style={{ flex: (z.to - z.from) / span, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ font: '700 9px var(--font-ui)', letterSpacing: 0.6, textTransform: 'uppercase', color: z.col }}>{z.l}</span>
            <span style={{ font: '500 10px var(--font-mono)', color: 'var(--fg-3)' }}>{z.from}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* Stage breakdown for sleep — stacked rail + legend. */
function StageBreakdown() {
  const d = OW.latest;
  const awakeMin = d.disturbances * 3;
  const segs = [
    { l: 'Deep', m: d.deepMin, col: 'var(--stage-deep)' },
    { l: 'REM', m: d.remMin, col: 'var(--stage-rem)' },
    { l: 'Light', m: d.lightMin, col: 'var(--stage-light)' },
    { l: 'Awake', m: awakeMin, col: 'var(--stage-awake)' },
  ];
  const tot = segs.reduce((a, s) => a + s.m, 0);
  return (
    <div>
      <MicroLabel style={{ marginBottom: 14, color: 'var(--fg-3)' }}>Stage breakdown</MicroLabel>
      <div style={{ display: 'flex', height: 14, borderRadius: 7, overflow: 'hidden', gap: 2, marginBottom: 16 }}>
        {segs.map((s, i) => <div key={i} style={{ flex: s.m / tot, background: s.col }} />)}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px 16px' }}>
        {segs.map((s, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: 8, font: '600 13px var(--font-ui)', color: 'var(--fg-1)' }}>
              <span style={{ width: 9, height: 9, borderRadius: 2, background: s.col }} />{s.l}
            </span>
            <span style={{ font: '700 13px var(--font-display)', color: 'var(--fg-2)' }}>{OW.fmtDur(s.m)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ---- the sheet --------------------------------------------------------- */
function ExplainSheet({ metricKey, onClose, sheetStyle }) {
  const [render, setRender] = useState(false);  // mounted
  const [shown, setShown] = useState(false);     // animated-in

  useEffect(() => {
    if (metricKey) {
      setRender(true);
      const t = setTimeout(() => setShown(true), 20);
      return () => clearTimeout(t);
    } else {
      setShown(false);
      const t = setTimeout(() => setRender(false), 280);
      return () => clearTimeout(t);
    }
  }, [metricKey]);

  if (!render) return null;
  const e = OW.explain[metricKey];
  if (!e) return null;

  const full = sheetStyle === 'full';

  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 50, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end' }}>
      {/* backdrop */}
      <div onClick={onClose} style={{
        position: 'absolute', inset: 0, background: 'rgba(3,5,7,0.66)',
        backdropFilter: 'blur(2px)', WebkitBackdropFilter: 'blur(2px)',
        opacity: shown ? 1 : 0, transition: 'opacity 260ms var(--ease-std)',
      }} />
      {/* sheet */}
      <div style={{
        position: 'relative', background: 'var(--bg-1)', borderTop: '1px solid var(--line-2)',
        borderTopLeftRadius: 28, borderTopRightRadius: 28, boxShadow: 'var(--shadow-pop)',
        maxHeight: full ? '94%' : '88%', display: 'flex', flexDirection: 'column',
        transform: shown ? 'translateY(0)' : 'translateY(101%)',
        transition: 'transform 320ms var(--ease-out)',
      }}>
        {/* drag handle + close */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '12px 0 4px', position: 'relative', flex: '0 0 auto' }}>
          <span style={{ width: 38, height: 5, borderRadius: 999, background: 'var(--bg-3)' }} />
          <button onClick={onClose} aria-label="Close" style={{ position: 'absolute', right: 16, top: 10, width: 30, height: 30, borderRadius: 999, background: 'var(--bg-2)', border: '1px solid var(--line-1)', color: 'var(--fg-2)', cursor: 'pointer', display: 'grid', placeItems: 'center' }}>
            <Icon name="close" size={17} />
          </button>
        </div>

        <div style={{ overflowY: 'auto', padding: '8px 20px 26px', display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* header */}
          <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 12 }}>
            <div>
              <MicroLabel color={e.color}>{e.label}</MicroLabel>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginTop: 10 }}>
                <Stat value={e.value} unit={e.unit} size={48} color={e.color} />
              </div>
            </div>
            <Chip color={e.band.color} dot>{e.band.text}</Chip>
          </div>

          {/* plain-language status */}
          <p style={{ font: '400 15px/1.55 var(--font-ui)', color: 'var(--fg-1)', margin: 0, textWrap: 'pretty' }}>{e.status}</p>

          {/* visualization */}
          <div style={{ background: 'var(--bg-2)', border: '1px solid var(--line-1)', borderRadius: 'var(--r-tile)', padding: 18 }}>
            {e.viz.kind === 'factors' && <FactorBars caption={e.viz.caption} factors={e.viz.factors} />}
            {e.viz.kind === 'gauge' && <ZoneGauge caption={e.viz.caption} zones={e.viz.zones} value={e.viz.value} min={e.viz.min} max={e.viz.max} />}
            {e.viz.kind === 'stages' && <StageBreakdown />}
          </div>

          {/* how it's calculated */}
          <div>
            <SectionTitle style={{ margin: '0 0 12px' }}>How it's calculated</SectionTitle>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <CalcRow label="Method" color="var(--blue)" text={e.method} />
              <CalcRow label="Inputs" color="var(--teal)" text={e.inputs} />
              <CalcRow label="Limit" color="var(--yellow)" text={e.limit} />
            </div>
          </div>

          {/* guidance */}
          {e.guidance && (
            <div style={{ display: 'flex', gap: 12, background: `color-mix(in srgb, ${e.color} 9%, var(--bg-2))`, border: `1px solid color-mix(in srgb, ${e.color} 34%, transparent)`, borderRadius: 'var(--r-tile)', padding: 16 }}>
              <Icon name="lightbulb" size={20} color={e.color} style={{ flex: '0 0 auto', marginTop: 1 }} />
              <div>
                <MicroLabel color={e.color} style={{ marginBottom: 6 }}>What you can do</MicroLabel>
                <div style={{ font: '400 14px/1.5 var(--font-ui)', color: 'var(--fg-1)' }}>{e.guidance}</div>
              </div>
            </div>
          )}

          <div style={{ font: '500 11px/1.5 var(--font-ui)', color: 'var(--fg-3)', textAlign: 'center', textWrap: 'pretty' }}>
            Computed on-device from your own strap data · not medical advice
          </div>
        </div>
      </div>
    </div>
  );
}

function CalcRow({ label, color, text }) {
  return (
    <div style={{ display: 'flex', gap: 12 }}>
      <div style={{ flex: '0 0 64px' }}>
        <span style={{ font: '700 10px var(--font-ui)', letterSpacing: 1, textTransform: 'uppercase', color }}>{label}</span>
      </div>
      <div style={{ flex: 1, font: '500 12.5px/1.55 var(--font-mono)', color: 'var(--fg-2)', textWrap: 'pretty' }}>{text}</div>
    </div>
  );
}

Object.assign(window, { ExplainCtx, useExplain, ExplainProvider, ExplainButton, Explainable, ExplainSheet, FactorBars, ZoneGauge, StageBreakdown });
