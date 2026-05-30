/* OpenWhoop shared UI primitives — exported to window for the screen files. */
const { useState, useEffect, useRef } = React;

function Icon({ name, size = 22, color, style }) {
  return <span className="material-symbols-rounded" style={{ fontSize: size, color: color || 'inherit', lineHeight: 1, ...style }}>{name}</span>;
}

function Card({ children, style, onClick, glow }) {
  return (
    <div onClick={onClick} style={{
      background: 'var(--bg-1)', border: '1px solid var(--line-1)', borderRadius: 'var(--r-card)',
      padding: 20, boxShadow: glow ? `var(--glow-${glow})` : 'var(--shadow-card)',
      cursor: onClick ? 'pointer' : 'default', ...style,
    }}>{children}</div>
  );
}

function MicroLabel({ children, color, style }) {
  return <div style={{ font: '700 11px/1 var(--font-ui)', letterSpacing: 1.4, textTransform: 'uppercase', color: color || 'var(--fg-2)', ...style }}>{children}</div>;
}

function Stat({ value, unit, size = 30, color = 'var(--fg-1)', style }) {
  return (
    <div style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: size, lineHeight: 1, color, fontVariantNumeric: 'tabular-nums', ...style }}>
      {value}{unit && <span style={{ fontSize: size * 0.42, color: 'var(--fg-2)' }}> {unit}</span>}
    </div>
  );
}

/* Animated SVG arc ring */
function Ring({ value, size = 220, stroke = 16, color, track = '#1F262C', children, animate = true }) {
  const r = (size - stroke) / 2;
  const circ = 2 * Math.PI * r;
  const [p, setP] = useState(animate ? 0 : value);
  useEffect(() => { const t = setTimeout(() => setP(value), 60); return () => clearTimeout(t); }, [value]);
  return (
    <div style={{ position: 'relative', width: size, height: size, display: 'grid', placeItems: 'center' }}>
      <svg width={size} height={size} style={{ position: 'absolute', transform: 'rotate(-90deg)' }}>
        <circle cx={size/2} cy={size/2} r={r} fill="none" stroke={track} strokeWidth={stroke} />
        <circle cx={size/2} cy={size/2} r={r} fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round"
          strokeDasharray={circ} strokeDashoffset={circ * (1 - p)}
          style={{ transition: 'stroke-dashoffset 1200ms cubic-bezier(0.16,1,0.3,1)' }} />
      </svg>
      <div style={{ position: 'absolute', textAlign: 'center' }}>{children}</div>
    </div>
  );
}

function Bar({ frac, color, height = 8, track = 'rgba(255,255,255,0.06)', gradient }) {
  return (
    <div style={{ height, borderRadius: height/2, background: track, overflow: 'hidden' }}>
      <div style={{ width: `${Math.max(0, Math.min(1, frac)) * 100}%`, height: '100%', borderRadius: height/2,
        background: gradient || color, transition: 'width 900ms cubic-bezier(0.16,1,0.3,1)' }} />
    </div>
  );
}

function ZoneRail({ zones, height = 12 }) {
  const total = zones.reduce((a, b) => a + b, 0) || 1;
  return (
    <div style={{ display: 'flex', height, borderRadius: height/2, overflow: 'hidden', background: 'rgba(255,255,255,.05)' }}>
      {zones.map((z, i) => z > 0 && <div key={i} style={{ width: `${z/total*100}%`, background: OW.zoneColors[i] }} />)}
    </div>
  );
}

function Chip({ children, color, dot, fill = true }) {
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 6, borderRadius: 'var(--r-chip)',
      padding: '6px 12px', font: '700 12px var(--font-ui)', letterSpacing: 0.3, color,
      background: fill ? `color-mix(in srgb, ${color} 15%, transparent)` : 'transparent',
      border: `1px solid color-mix(in srgb, ${color} 50%, transparent)`,
    }}>
      {dot && <span style={{ width: 7, height: 7, borderRadius: '50%', background: color }} />}
      {children}
    </span>
  );
}

/* Line chart with gradient fill */
function LineChart({ points, color, height = 170, min, max, dots = true, fill = true }) {
  const w = 600;
  if (!points.length) return null;
  const lo = min != null ? min : Math.min(...points);
  const hi = max != null ? max : Math.max(...points);
  const span = hi - lo || 1;
  const stepX = w / (points.length - 1 || 1);
  const xy = points.map((v, i) => [i * stepX, height - ((v - lo) / span) * (height - 14) - 7]);
  const line = xy.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
  const area = `${line} L${w} ${height} L0 ${height} Z`;
  const gid = 'lcg' + color.replace(/[^a-z0-9]/gi, '');
  return (
    <svg width="100%" height={height} viewBox={`0 0 ${w} ${height}`} preserveAspectRatio="none" style={{ overflow: 'visible' }}>
      <defs><linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor={color} stopOpacity="0.22" /><stop offset="1" stopColor={color} stopOpacity="0" />
      </linearGradient></defs>
      {[0.25, 0.5, 0.75].map(g => <line key={g} x1="0" y1={height*g} x2={w} y2={height*g} stroke="var(--line-3)" strokeWidth="1" />)}
      {fill && <path d={area} fill={`url(#${gid})`} />}
      <path d={line} fill="none" stroke={color} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
      {dots && xy.filter((_, i) => i % Math.ceil(points.length / 8) === 0 || i === points.length - 1).map(([x, y], i) => (
        <g key={i}><circle cx={x} cy={y} r="4" fill={color} /><circle cx={x} cy={y} r="2" fill="var(--bg-1)" /></g>
      ))}
    </svg>
  );
}

function Sparkline({ points, color = 'var(--green)', height = 44 }) {
  const w = 300;
  const lo = Math.min(...points), hi = Math.max(...points), span = hi - lo || 1;
  const stepX = w / (points.length - 1 || 1);
  const d = points.map((v, i) => `${i ? 'L' : 'M'}${(i*stepX).toFixed(1)} ${(height - ((v-lo)/span)*(height*0.7) - height*0.15).toFixed(1)}`).join(' ');
  return <svg width="100%" height={height} viewBox={`0 0 ${w} ${height}`} preserveAspectRatio="none"><path d={d} fill="none" stroke={color} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" /></svg>;
}

function Screen({ children, style }) {
  return <div style={{ display: 'flex', flexDirection: 'column', gap: 14, padding: '14px 16px 28px', ...style }}>{children}</div>;
}

function ScreenHead({ title, right }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 4 }}>
      <h1 style={{ font: '800 30px/1.05 var(--font-ui)', letterSpacing: -0.4, color: 'var(--fg-1)', margin: 0 }}>{title}</h1>
      {right}
    </div>
  );
}

function SectionTitle({ children, style }) {
  return <div style={{ font: '700 13px/1 var(--font-ui)', letterSpacing: 1.2, textTransform: 'uppercase', color: 'var(--fg-3)', margin: '4px 2px', ...style }}>{children}</div>;
}

const NAV = [
  { id: 'today', label: 'Today', icon: 'ecg_heart' },
  { id: 'sleep', label: 'Sleep', icon: 'bedtime' },
  { id: 'trends', label: 'Trends', icon: 'monitoring' },
  { id: 'workouts', label: 'Workouts', icon: 'exercise' },
  { id: 'device', label: 'Device', icon: 'watch' },
];

function BottomNav({ tab, setTab }) {
  return (
    <div style={{ display: 'flex', background: 'var(--bg-1)', borderTop: '1px solid var(--line-1)', padding: '8px 4px 6px' }}>
      {NAV.map(n => {
        const on = tab === n.id;
        return (
          <button key={n.id} onClick={() => setTab(n.id)} style={{
            flex: 1, background: 'none', border: 0, cursor: 'pointer', display: 'flex', flexDirection: 'column',
            alignItems: 'center', gap: 3, padding: '4px 0', color: on ? 'var(--green)' : 'var(--fg-3)',
          }}>
            <div style={{ background: on ? 'rgba(43,240,122,.14)' : 'transparent', borderRadius: 999, padding: '3px 16px' }}>
              <Icon name={n.icon} size={22} />
            </div>
            <span style={{ font: '700 10px var(--font-ui)', letterSpacing: 0.3 }}>{n.label}</span>
          </button>
        );
      })}
    </div>
  );
}

Object.assign(window, { Icon, Card, MicroLabel, Stat, Ring, Bar, ZoneRail, Chip, LineChart, Sparkline, Screen, ScreenHead, SectionTitle, BottomNav, NAV });
