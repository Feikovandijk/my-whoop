/* OpenWhoop Android — root: tabs, live strap state, explain sheets, tweaks. */
const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "accent": "#2BF07A",
  "density": "regular",
  "sheetStyle": "sheet",
  "uiScale": 1
}/*EDITMODE-END*/;

function App() {
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const [tab, setTab] = useState('today');
  const [connected, setConnected] = useState(true);
  const [hr, setHr] = useState(58);
  const battery = 84;
  const bonded = true;

  useEffect(() => {
    if (!connected) return;
    const id = setInterval(() => setHr(h => Math.max(50, Math.min(72, h + Math.round((Math.random() - 0.5) * 6)))), 1100);
    return () => clearInterval(id);
  }, [connected]);

  const [alarmOn, setAlarmOn] = useState(true);

  const screen = {
    today: <TodayScreen connected={connected} hr={hr} battery={battery} density={t.density} />,
    sleep: <SleepScreen alarmOn={alarmOn} setAlarmOn={setAlarmOn} />,
    trends: <TrendsScreen />,
    workouts: <WorkoutsScreen />,
    device: <DeviceScreen connected={connected} setConnected={setConnected} hr={hr} battery={battery} bonded={bonded} />,
  }[tab];

  const rootVars = {
    '--green': t.accent,
    '--green-dim': `color-mix(in srgb, ${t.accent} 72%, #000)`,
    '--glow-green': `0 0 28px color-mix(in srgb, ${t.accent} 34%, transparent)`,
  };

  return (
    <React.Fragment>
      <AndroidDevice dark width={400} height={860}>
        <div style={{ height: '100%', position: 'relative', display: 'flex', flexDirection: 'column', background: 'var(--bg-0)', ...rootVars }}>
          <ExplainProvider sheetStyle={t.sheetStyle}>
            <div key={tab} className="ow-fade" style={{ flex: 1, overflowY: 'auto', zoom: t.uiScale }}>{screen}</div>
            <BottomNav tab={tab} setTab={setTab} />
          </ExplainProvider>
        </div>
      </AndroidDevice>

      <TweaksPanel>
        <TweakSection label="Appearance" />
        <TweakColor label="Accent" value={t.accent}
          options={['#2BF07A', '#2C9BFF', '#2EE6C6', '#A66BFF']}
          onChange={(v) => setTweak('accent', v)} />
        <TweakRadio label="Card density" value={t.density}
          options={['compact', 'regular', 'comfy']}
          onChange={(v) => setTweak('density', v)} />
        <TweakSlider label="Content scale" value={t.uiScale} min={0.85} max={1.15} step={0.05}
          onChange={(v) => setTweak('uiScale', v)} />
        <TweakSection label="Explain it" />
        <TweakRadio label="Sheet height" value={t.sheetStyle}
          options={['sheet', 'full']}
          onChange={(v) => setTweak('sheetStyle', v)} />
      </TweaksPanel>
    </React.Fragment>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
