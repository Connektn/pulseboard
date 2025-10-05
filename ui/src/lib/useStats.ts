import { useState, useEffect } from 'react';
import { api, type StatsOverview } from './api';

export function useStats(enabled: boolean = true, intervalMs: number = 2000) {
  const [stats, setStats] = useState<StatsOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);

  useEffect(() => {
    // Skip if disabled
    if (!enabled) {
      return;
    }

    const updateStats = async () => {
      try {
        const response = await api.stats.overview();
        setStats(response);
        setLastUpdate(new Date());
        setError(null);
      } catch (err) {
        console.error('Stats update error:', err);
        setError((err as Error).message);
      }
    };

    // Initial fetch
    updateStats();

    // Set up polling
    const interval = setInterval(updateStats, intervalMs);

    return () => clearInterval(interval);
  }, [enabled, intervalMs]);

  return {
    stats,
    error,
    lastUpdate
  };
}