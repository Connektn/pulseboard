import { useState, useEffect } from 'react';
import { api } from '../lib/api';

interface HeaderProps {
  stats: {
    eventsPerMin: number;
    alertsPerMin: number;
    uptimeSec: number;
  } | null;
  onProfileChange?: (profile: 'SASE' | 'IGAMING' | 'CDP') => void;
  onSimulatorStart?: () => void;
  onSimulatorStop?: () => void;
  onSimulatorStatusChange?: (running: boolean) => void;
}

export function Header({ stats, onProfileChange, onSimulatorStart, onSimulatorStop, onSimulatorStatusChange }: HeaderProps) {
  const [currentProfile, setCurrentProfile] = useState<'SASE' | 'IGAMING' | 'CDP'>('SASE');
  const [isSimulatorRunning, setIsSimulatorRunning] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [rps, setRps] = useState(20);
  const [latenessSec, setLatenessSec] = useState(60);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  // Load profile and simulator status from backend on mount
  useEffect(() => {
    const loadInitialState = async () => {
      try {
        // Try to get saved profile from localStorage with new key
        const savedProfile = localStorage.getItem('pb.activeProfile') as 'SASE' | 'IGAMING' | 'CDP' | null;

        // Get current profile from backend
        const response = await api.profile.get();
        const backendProfile = response.profile;

        // Determine which profile to use
        let finalProfile: 'SASE' | 'IGAMING' | 'CDP';

        // If we have a saved profile different from backend, sync it
        if (savedProfile && savedProfile !== backendProfile) {
          await api.profile.set(savedProfile);
          finalProfile = savedProfile;
        } else {
          finalProfile = backendProfile;
          // Save to localStorage if not already saved
          if (!savedProfile) {
            localStorage.setItem('pb.activeProfile', backendProfile);
          }
        }

        // Update local state
        setCurrentProfile(finalProfile);

        // Notify parent component
        onProfileChange?.(finalProfile);

        // Check simulator status
        const simStatus = await api.simulator.status();
        setIsSimulatorRunning(simStatus.running);

        // Notify parent of initial simulator status
        onSimulatorStatusChange?.(simStatus.running);
      } catch (error) {
        console.error('Failed to load initial state:', error);
        // Fallback to SASE if error
        setCurrentProfile('SASE');
        localStorage.setItem('pb.activeProfile', 'SASE');
        onProfileChange?.('SASE');
      }
    };

    loadInitialState();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // Only run on mount

  // Toast auto-dismiss
  useEffect(() => {
    if (toast) {
      const timer = setTimeout(() => setToast(null), 3000);
      return () => clearTimeout(timer);
    }
  }, [toast]);

  const handleProfileSelect = async (profile: 'SASE' | 'IGAMING' | 'CDP') => {
    if (profile === currentProfile) return;

    setIsLoading(true);
    try {
      // Update backend
      await api.profile.set(profile);

      // Update local state
      setCurrentProfile(profile);

      // Persist to localStorage with new key
      localStorage.setItem('pb.activeProfile', profile);

      // Notify parent component
      onProfileChange?.(profile);

      setToast({ message: `Switched to ${profile} profile`, type: 'success' });
    } catch (error) {
      console.error('Failed to update profile:', error);
      setToast({ message: 'Failed to update profile', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleSimulatorStart = async () => {
    setIsLoading(true);
    try {
      const params = currentProfile === 'CDP'
        ? { profile: 'CDP', rps, latenessSec }
        : undefined;

      const response = await api.simulator.start(params);

      if (response.status === 'started' || response.status === 'already_running') {
        setIsSimulatorRunning(true);
        onSimulatorStart?.();
        onSimulatorStatusChange?.(true);
        setToast({ message: 'Simulator started successfully', type: 'success' });
      } else if (response.status === 'error') {
        setToast({ message: response.message || 'Failed to start simulator', type: 'error' });
      }
    } catch (error) {
      console.error('Failed to start simulator:', error);
      setToast({ message: 'Failed to start simulator', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleSimulatorStop = async () => {
    setIsLoading(true);
    try {
      const response = await api.simulator.stop();

      if (response.status === 'stopped' || response.status === 'already_stopped') {
        setIsSimulatorRunning(false);
        onSimulatorStop?.();
        onSimulatorStatusChange?.(false);
        setToast({ message: 'Simulator stopped successfully', type: 'success' });
      }
    } catch (error) {
      console.error('Failed to stop simulator:', error);
      setToast({ message: 'Failed to stop simulator', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const formatUptime = (seconds: number): string => {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hours > 0) {
      return `${hours}h ${minutes}m ${secs}s`;
    } else if (minutes > 0) {
      return `${minutes}m ${secs}s`;
    } else {
      return `${secs}s`;
    }
  };

  return (
    <header style={{
      backgroundColor: '#1e40af',
      color: 'white',
      padding: '1rem 2rem',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
      minHeight: currentProfile === 'CDP' ? '120px' : '70px',
      transition: 'min-height 0.3s ease'
    }}>
      {/* Left: App Name */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        <h1 style={{
          margin: 0,
          fontSize: '1.5rem',
          fontWeight: 'bold',
          color: 'white'
        }}>
          Pulseboard
        </h1>
      </div>

      {/* Center: Controls */}
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '1rem',
        flex: 1
      }}>
        {/* Profile Tabs */}
        <div style={{ display: 'flex', gap: '0.25rem' }}>
          {(['SASE', 'IGAMING', 'CDP'] as const).map((profile) => (
            <button
              key={profile}
              onClick={() => handleProfileSelect(profile)}
              disabled={isLoading}
              style={{
                backgroundColor: currentProfile === profile ? '#10b981' : 'rgba(255,255,255,0.1)',
                color: 'white',
                border: currentProfile === profile ? 'none' : '1px solid rgba(255,255,255,0.3)',
                padding: '0.5rem 1rem',
                borderRadius: '0.375rem',
                fontSize: '0.875rem',
                fontWeight: currentProfile === profile ? '600' : '500',
                cursor: isLoading ? 'not-allowed' : 'pointer',
                opacity: isLoading ? 0.7 : 1,
                minWidth: '80px',
                transition: 'all 0.2s ease'
              }}
            >
              {profile}
            </button>
          ))}
        </div>

        {/* CDP Control Panel */}
        {currentProfile === 'CDP' && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '1rem',
            backgroundColor: 'rgba(255,255,255,0.1)',
            padding: '0.75rem 1rem',
            borderRadius: '0.375rem'
          }}>
            {/* RPS Control */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <label style={{ fontSize: '0.75rem', opacity: 0.9 }}>RPS:</label>
              <input
                type="range"
                min="1"
                max="200"
                value={rps}
                onChange={(e) => setRps(Number(e.target.value))}
                disabled={isLoading}
                style={{ width: '100px' }}
              />
              <span style={{ fontSize: '0.75rem', minWidth: '30px' }}>{rps}</span>
            </div>

            {/* Lateness Control */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <label style={{ fontSize: '0.75rem', opacity: 0.9 }}>Lateness:</label>
              <input
                type="range"
                min="0"
                max="120"
                value={latenessSec}
                onChange={(e) => setLatenessSec(Number(e.target.value))}
                disabled={isLoading}
                style={{ width: '100px' }}
              />
              <span style={{ fontSize: '0.75rem', minWidth: '30px' }}>{latenessSec}s</span>
            </div>

            {/* Start/Stop Buttons */}
            <div style={{ display: 'flex', gap: '0.5rem', marginLeft: '0.5rem' }}>
              <button
                onClick={handleSimulatorStart}
                disabled={isLoading || isSimulatorRunning}
                style={{
                  backgroundColor: isSimulatorRunning ? '#6b7280' : '#10b981',
                  color: 'white',
                  border: 'none',
                  padding: '0.4rem 0.8rem',
                  borderRadius: '0.375rem',
                  fontSize: '0.875rem',
                  fontWeight: '500',
                  cursor: (isLoading || isSimulatorRunning) ? 'not-allowed' : 'pointer',
                  opacity: (isLoading || isSimulatorRunning) ? 0.7 : 1
                }}
              >
                ▶ Start
              </button>
              <button
                onClick={handleSimulatorStop}
                disabled={isLoading || !isSimulatorRunning}
                style={{
                  backgroundColor: !isSimulatorRunning ? '#6b7280' : '#ef4444',
                  color: 'white',
                  border: 'none',
                  padding: '0.4rem 0.8rem',
                  borderRadius: '0.375rem',
                  fontSize: '0.875rem',
                  fontWeight: '500',
                  cursor: (isLoading || !isSimulatorRunning) ? 'not-allowed' : 'pointer',
                  opacity: (isLoading || !isSimulatorRunning) ? 0.7 : 1
                }}
              >
                ⏹ Stop
              </button>
            </div>
          </div>
        )}

        {/* Non-CDP Simulator Controls */}
        {currentProfile !== 'CDP' && (
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              onClick={handleSimulatorStart}
              disabled={isLoading || isSimulatorRunning}
              style={{
                backgroundColor: isSimulatorRunning ? '#6b7280' : '#10b981',
                color: 'white',
                border: 'none',
                padding: '0.4rem 0.8rem',
                borderRadius: '0.375rem',
                fontSize: '0.875rem',
                fontWeight: '500',
                cursor: (isLoading || isSimulatorRunning) ? 'not-allowed' : 'pointer',
                opacity: (isLoading || isSimulatorRunning) ? 0.7 : 1
              }}
            >
              ▶ Start
            </button>
            <button
              onClick={handleSimulatorStop}
              disabled={isLoading || !isSimulatorRunning}
              style={{
                backgroundColor: !isSimulatorRunning ? '#6b7280' : '#ef4444',
                color: 'white',
                border: 'none',
                padding: '0.4rem 0.8rem',
                borderRadius: '0.375rem',
                fontSize: '0.875rem',
                fontWeight: '500',
                cursor: (isLoading || !isSimulatorRunning) ? 'not-allowed' : 'pointer',
                opacity: (isLoading || !isSimulatorRunning) ? 0.7 : 1
              }}
            >
              ⏹ Stop
            </button>
          </div>
        )}
      </div>

      {/* Right: Stats Badges */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: '1rem'
      }}>
        {stats ? (
          <>
            <div style={{
              backgroundColor: 'rgba(255,255,255,0.1)',
              padding: '0.4rem 0.8rem',
              borderRadius: '0.375rem',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              minWidth: '70px'
            }}>
              <div style={{ fontSize: '1.2rem', fontWeight: 'bold', lineHeight: 1 }}>
                {stats.eventsPerMin}
              </div>
              <div style={{ fontSize: '0.7rem', opacity: 0.8, lineHeight: 1 }}>
                events/min
              </div>
            </div>

            <div style={{
              backgroundColor: 'rgba(255,255,255,0.1)',
              padding: '0.4rem 0.8rem',
              borderRadius: '0.375rem',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              minWidth: '70px'
            }}>
              <div style={{
                fontSize: '1.2rem',
                fontWeight: 'bold',
                lineHeight: 1,
                color: stats.alertsPerMin > 0 ? '#fbbf24' : 'white'
              }}>
                {stats.alertsPerMin}
              </div>
              <div style={{ fontSize: '0.7rem', opacity: 0.8, lineHeight: 1 }}>
                alerts/min
              </div>
            </div>

            <div style={{
              backgroundColor: 'rgba(255,255,255,0.1)',
              padding: '0.4rem 0.8rem',
              borderRadius: '0.375rem',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              minWidth: '70px'
            }}>
              <div style={{ fontSize: '1.2rem', fontWeight: 'bold', lineHeight: 1 }}>
                {formatUptime(stats.uptimeSec)}
              </div>
              <div style={{ fontSize: '0.7rem', opacity: 0.8, lineHeight: 1 }}>
                uptime
              </div>
            </div>
          </>
        ) : (
          <div style={{
            fontSize: '0.875rem',
            opacity: 0.8,
            padding: '0.4rem 0.8rem',
            backgroundColor: 'rgba(255,255,255,0.1)',
            borderRadius: '0.375rem'
          }}>
            Loading stats...
          </div>
        )}
      </div>

      {/* Toast Notification */}
      {toast && (
        <div style={{
          position: 'fixed',
          top: '5rem',
          right: '2rem',
          backgroundColor: toast.type === 'success' ? '#10b981' : '#ef4444',
          color: 'white',
          padding: '0.75rem 1.25rem',
          borderRadius: '0.375rem',
          boxShadow: '0 4px 6px rgba(0,0,0,0.2)',
          fontSize: '0.875rem',
          fontWeight: '500',
          zIndex: 1000,
          animation: 'slideIn 0.3s ease'
        }}>
          {toast.message}
        </div>
      )}
    </header>
  );
}