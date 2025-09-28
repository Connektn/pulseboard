import './App.css'
import { Header } from './components/Header'
import { AlertsTable } from './components/AlertsTable'
import { KPIPanel } from './components/KPIPanel'
import { useStats } from './lib/useStats'
import { useSSE } from './lib/useSSE'

function App() {
  const { stats } = useStats(2000); // Poll every 2 seconds
  const { alerts, connected, error } = useSSE({ maxAlerts: 100 });

  const handleProfileChange = (profile: 'SASE' | 'IGAMING') => {
    console.log('Profile changed to:', profile);
  };

  const handleSimulatorStart = () => {
    console.log('Simulator started');
  };

  const handleSimulatorStop = () => {
    console.log('Simulator stopped');
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      minHeight: '100vh',
      backgroundColor: '#f8fafc',
      fontFamily: 'system-ui, -apple-system, sans-serif'
    }}>
      {/* G2 Header */}
      <Header
        stats={stats}
        onProfileChange={handleProfileChange}
        onSimulatorStart={handleSimulatorStart}
        onSimulatorStop={handleSimulatorStop}
      />

      {/* Main Content Area */}
      <main style={{
        flex: 1,
        padding: '2rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '2rem'
      }}>
        {/* SSE Connection Status */}
        <div style={{
          backgroundColor: 'white',
          borderRadius: '0.5rem',
          padding: '1rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          border: '1px solid #e2e8f0'
        }}>
          <h3 style={{
            margin: '0 0 0.5rem 0',
            fontSize: '1rem',
            fontWeight: '600',
            color: '#374151'
          }}>
            Real-time Connection Status
          </h3>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '1rem',
            fontSize: '0.875rem',
            color: '#6b7280'
          }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem'
            }}>
              <div style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: connected ? '#10b981' : '#ef4444'
              }}></div>
              <span>
                SSE: {connected ? 'Connected' : 'Disconnected'}
              </span>
            </div>
            {error && (
              <span style={{ color: '#ef4444' }}>
                Error: {error}
              </span>
            )}
            <span>
              Alerts received: {alerts.length}
            </span>
          </div>
        </div>

        {/* G4 KPI Panel with Sparkline */}
        <KPIPanel stats={stats} alerts={alerts} />

        {/* G3 Live Alerts Table */}
        <AlertsTable alerts={alerts} />

        {/* System Status Footer */}
        <div style={{
          backgroundColor: 'white',
          borderRadius: '0.5rem',
          padding: '1rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          border: '1px solid #e2e8f0',
          fontSize: '0.75rem',
          color: '#6b7280',
          textAlign: 'center'
        }}>
          Pulseboard MVP - Real-time anomaly detection for live event streams
          {stats && (
            <span style={{ marginLeft: '1rem' }}>
              • System uptime: {Math.floor(stats.uptimeSec / 60)}m {stats.uptimeSec % 60}s
            </span>
          )}
        </div>
      </main>
    </div>
  )
}

export default App
