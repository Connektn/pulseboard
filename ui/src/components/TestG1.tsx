import { useState, useEffect } from 'react';
import { api } from '../lib/api';
import { useSSE } from '../lib/useSSE';

export function TestG1() {
  const [apiStatus, setApiStatus] = useState<string>('Testing...');
  const [profile, setProfile] = useState<string>('Unknown');
  const [stats, setStats] = useState<{eventsPerMin: number; alertsPerMin: number; uptimeSec: number} | null>(null);

  // Test SSE hook
  const { alerts, connected, error, lastMessage } = useSSE({ maxAlerts: 5 });

  // Test API client on mount
  useEffect(() => {
    const testApi = async () => {
      try {
        // Test health endpoint
        const health = await api.health();
        console.log('Health:', health);

        // Test profile endpoint
        const profileResponse = await api.profile.get();
        setProfile(profileResponse.profile);
        console.log('Profile:', profileResponse);

        // Test stats endpoint
        const statsResponse = await api.stats.overview();
        setStats(statsResponse);
        console.log('Stats:', statsResponse);

        setApiStatus('✅ API Client Working');
      } catch (error) {
        console.error('API Test Error:', error);
        setApiStatus('❌ API Client Error: ' + (error as Error).message);
      }
    };

    testApi();
  }, []);

  const handleStartSimulator = async () => {
    try {
      const result = await api.simulator.start();
      console.log('Start Simulator:', result);
      setApiStatus('✅ Simulator Started');
    } catch (error) {
      console.error('Start Simulator Error:', error);
      setApiStatus('❌ Simulator Start Failed: ' + (error as Error).message);
    }
  };

  const handleStopSimulator = async () => {
    try {
      const result = await api.simulator.stop();
      console.log('Stop Simulator:', result);
      setApiStatus('✅ Simulator Stopped');
    } catch (error) {
      console.error('Stop Simulator Error:', error);
      setApiStatus('❌ Simulator Stop Failed: ' + (error as Error).message);
    }
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'monospace' }}>
      <h2>G1 API Client & SSE Hook Test</h2>

      {/* API Client Status */}
      <div style={{ marginBottom: '20px', padding: '10px', border: '1px solid #ccc' }}>
        <h3>API Client Status</h3>
        <p><strong>Status:</strong> {apiStatus}</p>
        <p><strong>Profile:</strong> {profile}</p>
        <p><strong>Stats:</strong> {stats ? `Events: ${stats.eventsPerMin}, Alerts: ${stats.alertsPerMin}, Uptime: ${stats.uptimeSec}s` : 'Loading...'}</p>

        <div style={{ marginTop: '10px' }}>
          <button onClick={handleStartSimulator} style={{ marginRight: '10px' }}>
            Start Simulator
          </button>
          <button onClick={handleStopSimulator}>
            Stop Simulator
          </button>
        </div>
      </div>

      {/* SSE Hook Status */}
      <div style={{ marginBottom: '20px', padding: '10px', border: '1px solid #ccc' }}>
        <h3>SSE Hook Status</h3>
        <p><strong>Connected:</strong> {connected ? '✅ Connected' : '❌ Disconnected'}</p>
        <p><strong>Error:</strong> {error || 'None'}</p>
        <p><strong>Alerts Received:</strong> {alerts.length}</p>
        <p><strong>Last Message:</strong> {lastMessage ? `${lastMessage.type} at ${new Date().toLocaleTimeString()}` : 'None'}</p>
      </div>

      {/* Recent Alerts */}
      <div style={{ padding: '10px', border: '1px solid #ccc' }}>
        <h3>Recent Alerts ({alerts.length}/5)</h3>
        {alerts.length === 0 ? (
          <p>No alerts received yet. Start the simulator to see alerts.</p>
        ) : (
          <div style={{ maxHeight: '300px', overflowY: 'auto' }}>
            {alerts.map((alert, index) => (
              <div
                key={alert.id}
                style={{
                  padding: '5px',
                  margin: '5px 0',
                  backgroundColor: index === 0 ? '#e8f5e8' : '#f5f5f5',
                  border: '1px solid #ddd',
                  fontSize: '12px'
                }}
              >
                <div><strong>Rule:</strong> {alert.rule}</div>
                <div><strong>Entity:</strong> {alert.entityId}</div>
                <div><strong>Severity:</strong> {alert.severity}</div>
                <div><strong>Time:</strong> {new Date(alert.ts).toLocaleTimeString()}</div>
                <div><strong>Evidence:</strong> {JSON.stringify(alert.evidence)}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}