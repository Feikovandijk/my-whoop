/* Device — connection, live HR pulse, hardware, server config, commands, log console */
function DeviceScreen({ connected, setConnected, hr, battery, bonded }) {
  const [log, setLog] = useState([
    '10:42:22 frame METADATA HISTORY_START',
    '10:42:21 frame REALTIME_DATA seq=4192 crc_ok hr=58',
    '10:42:19 BLE_BONDED just-works write ok',
    '10:42:18 CONNECT strap 61080001-… rssi -57',
  ]);
  const push = (m) => setLog(l => [`${new Date().toLocaleTimeString('en-US', { hour12: false })} ${m}`, ...l].slice(0, 24));

  return (
    <Screen>
      <ScreenHead title="Device" />

      {/* connection + live pulse */}
      <Card glow={connected ? 'green' : null}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ font: '700 16px var(--font-ui)', color: 'var(--fg-1)' }}>WHOOP 4.0 Strap</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <Chip color={connected ? 'var(--green)' : 'var(--red)'} dot>{connected ? 'Connected' : 'Offline'}</Chip>
            <Chip color={bonded ? 'var(--blue)' : 'var(--yellow)'} dot>{bonded ? 'Bonded' : 'Unbonded'}</Chip>
          </div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'center', padding: '22px 0 10px' }}>
          <div style={{ position: 'relative', width: 150, height: 150, display: 'grid', placeItems: 'center' }}>
            {connected && <div className="ow-pulse" style={{ position: 'absolute', inset: 0, borderRadius: '50%', border: '2px solid var(--green)' }} />}
            <div style={{ position: 'absolute', inset: 18, borderRadius: '50%', background: 'radial-gradient(circle, rgba(43,240,122,.16), transparent 70%)' }} />
            <div style={{ textAlign: 'center' }}>
              <Stat value={connected ? hr : '—'} size={58} color="var(--green)" />
              <MicroLabel style={{ marginTop: 2 }}>Live BPM</MicroLabel>
            </div>
          </div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-around', paddingTop: 14, borderTop: '1px solid var(--line-1)' }}>
          {[['Battery', connected ? `${battery}%` : '—'], ['Last Frame', connected ? 'REALTIME' : '—'], ['Last Event', connected ? 'BLE_BONDED' : '—']].map(([l, v]) => (
            <div key={l} style={{ textAlign: 'center' }}>
              <MicroLabel style={{ fontSize: 9 }}>{l}</MicroLabel>
              <div style={{ font: '700 13px var(--font-ui)', color: 'var(--fg-1)', marginTop: 5 }}>{v}</div>
            </div>
          ))}
        </div>
        <button onClick={() => { setConnected(!connected); push(connected ? 'DISCONNECT user' : 'CONNECT + BOND ok'); }} style={{
          width: '100%', marginTop: 16, padding: 13, borderRadius: 'var(--r-input)', border: 0, cursor: 'pointer',
          font: '700 14px var(--font-ui)', background: connected ? 'var(--bg-2)' : 'var(--green)',
          color: connected ? 'var(--fg-1)' : 'var(--fg-on-accent)', border: connected ? '1px solid var(--line-2)' : 0,
        }}>{connected ? 'Disconnect strap' : 'Connect & bond strap'}</button>
      </Card>

      {/* hardware */}
      <Card style={{ padding: 18 }}>
        <MicroLabel style={{ marginBottom: 12 }}>Hardware</MicroLabel>
        {[['Serial', 'WHOOP 4.0 · 4A210…'], ['Harvard FW', '4.10.1.0'], ['Boylston FW', '4.2.0.0'], ['Local DB', '38,402 samples · 6 raw batches · 1.4 MB']].map(([l, v]) => (
          <div key={l} style={{ display: 'flex', justifyContent: 'space-between', padding: '7px 0', borderBottom: '1px solid var(--line-1)' }}>
            <span style={{ font: '500 13px var(--font-ui)', color: 'var(--fg-3)' }}>{l}</span>
            <span style={{ font: '600 13px var(--font-mono)', color: 'var(--fg-1)' }}>{v}</span>
          </div>
        ))}
      </Card>

      {/* server config */}
      <Card style={{ padding: 18 }}>
        <MicroLabel style={{ marginBottom: 12 }}>Ingest server</MicroLabel>
        <Field label="Server URL" value="https://whoop.example.com" />
        <Field label="API Bearer Key" value="••••••••••••••••" mono />
      </Card>

      {/* commands */}
      <Card style={{ padding: 18 }}>
        <MicroLabel style={{ marginBottom: 12 }}>Curated commands</MicroLabel>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <Cmd label="Sync Strap → Phone" kind="primary" onClick={() => push('SEND_HISTORICAL_DATA 22')} />
          <Cmd label="Sync Server → Phone" kind="secondary" onClick={() => push('GET /v1/streams pull ok')} />
          <Cmd label="Test Haptic Buzz" kind="ghost" onClick={() => push('RUN_HAPTICS_PATTERN id=2')} />
          <Cmd label="Capture IMU · 30s" kind="ghost" onClick={() => push('START_RAW_DATA 1 (30s)')} />
          <Cmd label="Disable Alarm" kind="ghost" onClick={() => push('DISABLE_ALARM 69')} />
          <Cmd label="Wipe Local DB" kind="danger" onClick={() => push('clearAllTables()')} />
        </div>
      </Card>

      {/* log console */}
      <div style={{ background: 'var(--console)', border: '1px solid var(--line-1)', borderRadius: 'var(--r-card)', padding: 16 }}>
        <MicroLabel color="var(--green)" style={{ marginBottom: 10, fontFamily: 'var(--font-mono)' }}>BLE Log Console</MicroLabel>
        <div style={{ height: 150, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 4 }}>
          {log.map((l, i) => <div key={i} style={{ font: '400 11px/1.4 var(--font-mono)', color: i === 0 ? 'var(--green)' : 'var(--fg-2)' }}>{l}</div>)}
        </div>
      </div>
    </Screen>
  );
}

function Field({ label, value, mono }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div style={{ font: '500 10px var(--font-ui)', color: 'var(--fg-3)', marginBottom: 5 }}>{label}</div>
      <div style={{ background: 'var(--bg-2)', border: '1px solid var(--line-2)', borderRadius: 'var(--r-input)', padding: '11px 13px',
        font: `500 13px ${mono ? 'var(--font-mono)' : 'var(--font-ui)'}`, color: 'var(--fg-1)' }}>{value}</div>
    </div>
  );
}

function Cmd({ label, kind, onClick }) {
  const styles = {
    primary: { background: 'var(--green)', color: 'var(--fg-on-accent)', border: 0 },
    secondary: { background: 'var(--blue)', color: '#fff', border: 0 },
    ghost: { background: 'var(--bg-2)', color: 'var(--fg-1)', border: '1px solid var(--line-2)' },
    danger: { background: 'rgba(255,77,106,.16)', color: 'var(--red)', border: '1px solid var(--red)' },
  }[kind];
  return <button onClick={onClick} style={{ padding: '11px 8px', borderRadius: 'var(--r-input)', cursor: 'pointer', font: '700 12px var(--font-ui)', ...styles }}>{label}</button>;
}
window.DeviceScreen = DeviceScreen;
