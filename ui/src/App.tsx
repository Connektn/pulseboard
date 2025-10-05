import './App.css'
import { useState, useCallback } from 'react'
import { Header } from './components/Header'
import { AlertsTable } from './components/AlertsTable'
import { KPIPanel } from './components/KPIPanel'
import { ProfilesList } from './components/ProfilesList'
import { useStats } from './lib/useStats'
import { useSSE } from './lib/useSSE'

function App() {
  const [currentProfile, setCurrentProfile] = useState<'SASE' | 'IGAMING' | 'CDP'>('SASE');
  const [isSimulatorRunning, setIsSimulatorRunning] = useState(false);

  // Only fetch stats and alerts for non-CDP profiles when simulator is running
  const shouldFetchData = currentProfile !== 'CDP' && isSimulatorRunning;
  const { stats } = useStats(shouldFetchData, 2000);
  const { alerts, connected, error } = useSSE({ maxAlerts: 100, enabled: shouldFetchData });

  const handleProfileChange = useCallback((profile: 'SASE' | 'IGAMING' | 'CDP') => {
    console.log('Profile changed to:', profile);
    setCurrentProfile(profile);
  }, []);

  const handleSimulatorStart = useCallback(() => {
    console.log('Simulator started');
    setIsSimulatorRunning(true);
  }, []);

  const handleSimulatorStop = useCallback(() => {
    console.log('Simulator stopped');
    setIsSimulatorRunning(false);
  }, []);

  const handleSimulatorStatusChange = useCallback((running: boolean) => {
    console.log('Simulator status changed:', running);
    setIsSimulatorRunning(running);
  }, []);

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
        onSimulatorStatusChange={handleSimulatorStatusChange}
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

        {/* Conditional Content Based on Profile */}
        {currentProfile === 'CDP' ? (
          /* CDP View */
          <ProfilesList isSimulatorRunning={isSimulatorRunning} />
        ) : (
          /* SASE/IGAMING View */
          <>
            {/* G4 KPI Panel with Sparkline */}
            <KPIPanel stats={stats} alerts={alerts} />

            {/* G3 Live Alerts Table */}
            <AlertsTable alerts={alerts} />
          </>
        )}

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
