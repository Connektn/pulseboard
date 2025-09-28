import { useState, useEffect } from 'react';
import { api } from '../lib/api';

interface HeaderProps {
  stats: {
    eventsPerMin: number;
    alertsPerMin: number;
    uptimeSec: number;
  } | null;
  onProfileChange?: (profile: 'SASE' | 'IGAMING') => void;
  onSimulatorStart?: () => void;
  onSimulatorStop?: () => void;
}

export function Header({ stats, onProfileChange, onSimulatorStart, onSimulatorStop }: HeaderProps) {
  const [currentProfile, setCurrentProfile] = useState<'SASE' | 'IGAMING'>('SASE');
  const [isSimulatorRunning, setIsSimulatorRunning] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // Load profile from localStorage and backend on mount
  useEffect(() => {
    const loadProfile = async () => {
      try {
        // Try to get saved profile from localStorage
        const savedProfile = localStorage.getItem('pulseboard-profile') as 'SASE' | 'IGAMING' | null;

        // Get current profile from backend
        const response = await api.profile.get();
        const backendProfile = response.profile;

        // If we have a saved profile different from backend, sync it
        if (savedProfile && savedProfile !== backendProfile) {
          await api.profile.set(savedProfile);
          setCurrentProfile(savedProfile);
        } else {
          setCurrentProfile(backendProfile);
          // Save to localStorage if not already saved
          if (!savedProfile) {
            localStorage.setItem('pulseboard-profile', backendProfile);
          }
        }
      } catch (error) {
        console.error('Failed to load profile:', error);
        // Fallback to SASE if error
        setCurrentProfile('SASE');
        localStorage.setItem('pulseboard-profile', 'SASE');
      }
    };

    loadProfile();
  }, []);

  const handleProfileToggle = async () => {
    setIsLoading(true);
    try {
      const newProfile = currentProfile === 'SASE' ? 'IGAMING' : 'SASE';

      // Update backend
      await api.profile.set(newProfile);

      // Update local state
      setCurrentProfile(newProfile);

      // Persist to localStorage
      localStorage.setItem('pulseboard-profile', newProfile);

      // Notify parent component
      onProfileChange?.(newProfile);
    } catch (error) {
      console.error('Failed to update profile:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSimulatorStart = async () => {
    setIsLoading(true);
    try {
      await api.simulator.start();
      setIsSimulatorRunning(true);
      onSimulatorStart?.();
    } catch (error) {
      console.error('Failed to start simulator:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSimulatorStop = async () => {
    setIsLoading(true);
    try {
      await api.simulator.stop();
      setIsSimulatorRunning(false);
      onSimulatorStop?.();
    } catch (error) {
      console.error('Failed to stop simulator:', error);
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
      minHeight: '70px'
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
        alignItems: 'center',
        gap: '1rem',
        flex: 1,
        justifyContent: 'center'
      }}>
        {/* Profile Toggle */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontSize: '0.9rem', opacity: 0.9 }}>Profile:</span>
          <button
            onClick={handleProfileToggle}
            disabled={isLoading}
            style={{
              backgroundColor: currentProfile === 'SASE' ? '#10b981' : '#f59e0b',
              color: 'white',
              border: 'none',
              padding: '0.4rem 0.8rem',
              borderRadius: '0.375rem',
              fontSize: '0.875rem',
              fontWeight: '600',
              cursor: isLoading ? 'not-allowed' : 'pointer',
              opacity: isLoading ? 0.7 : 1,
              minWidth: '80px'
            }}
          >
            {isLoading ? '...' : currentProfile}
          </button>
        </div>

        {/* Simulator Controls */}
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
    </header>
  );
}