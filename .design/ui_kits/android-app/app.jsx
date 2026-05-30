/* OpenWhoop Android — root: tabs, live strap state, device frame */
function App() {
  const [tab, setTab] = useState('today');
  const [connected, setConnected] = useState(true);
  const [hr, setHr] = useState(58);
  const battery = 84;
  const bonded = true;

  // live HR wander while connected
  useEffect(() => {
    if (!connected) return;
    const id = setInterval(() => setHr(h => Math.max(50, Math.min(72, h + Math.round((Math.random() - 0.5) * 6)))), 1100);
    return () => clearInterval(id);
  }, [connected]);

  const [alarmOn, setAlarmOn] = useState(true);

  const screen = {
    today: <TodayScreen connected={connected} hr={hr} battery={battery} />,
    sleep: <SleepScreen alarmOn={alarmOn} setAlarmOn={setAlarmOn} />,
    trends: <TrendsScreen />,
    workouts: <WorkoutsScreen />,
    device: <DeviceScreen connected={connected} setConnected={setConnected} hr={hr} battery={battery} bonded={bonded} />,
  }[tab];

  return (
    <AndroidDevice dark width={400} height={860}>
      <div style={{ height: '100%', display: 'flex', flexDirection: 'column', background: 'var(--bg-0)' }}>
        <div key={tab} className="ow-fade" style={{ flex: 1, overflowY: 'auto' }}>{screen}</div>
        <BottomNav tab={tab} setTab={setTab} />
      </div>
    </AndroidDevice>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
