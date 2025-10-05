import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act, cleanup } from '@testing-library/react';
import { useStats } from './useStats';
import * as api from './api';

// Mock the api module
vi.mock('./api', () => ({
  api: {
    stats: {
      overview: vi.fn(),
    },
  },
}));

describe('useStats', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('should initialize with null state', () => {
    const { result } = renderHook(() => useStats());

    expect(result.current.stats).toBe(null);
    expect(result.current.error).toBe(null);
    expect(result.current.lastUpdate).toBe(null);
  });

  it('should fetch stats on mount', async () => {
    const mockStats = {
      eventsPerMin: 120,
      alertsPerMin: 5,
      uptimeSec: 3600,
    };

    (api.api.stats.overview as any).mockResolvedValue(mockStats);

    const { result } = renderHook(() => useStats());

    // Initial fetch should have been triggered
    expect(api.api.stats.overview).toHaveBeenCalledTimes(1);

    // Wait for the promise to resolve
    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.stats).toEqual(mockStats);
    expect(result.current.error).toBe(null);
    expect(result.current.lastUpdate).toBeInstanceOf(Date);
  });

  it('should handle API errors', async () => {
    const mockError = new Error('API Error');
    (api.api.stats.overview as any).mockRejectedValue(mockError);

    // Mock console.error to avoid test noise
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    const { result } = renderHook(() => useStats());

    // Wait for the promise to reject
    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.stats).toBe(null);
    expect(result.current.error).toBe('API Error');
    expect(consoleSpy).toHaveBeenCalledWith('Stats update error:', mockError);

    consoleSpy.mockRestore();
  });

  it('should poll stats at specified interval', async () => {
    const mockStats1 = {
      eventsPerMin: 100,
      alertsPerMin: 3,
      uptimeSec: 1800,
    };

    const mockStats2 = {
      eventsPerMin: 150,
      alertsPerMin: 7,
      uptimeSec: 1900,
    };

    (api.api.stats.overview as any)
      .mockResolvedValueOnce(mockStats1)
      .mockResolvedValueOnce(mockStats2);

    const { result } = renderHook(() => useStats(1000)); // 1 second interval

    // Initial fetch
    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.stats).toEqual(mockStats1);
    expect(api.api.stats.overview).toHaveBeenCalledTimes(1);

    // Advance time by 1 second
    await act(async () => {
      vi.advanceTimersByTime(1000);
      await Promise.resolve();
    });

    expect(result.current.stats).toEqual(mockStats2);
    expect(api.api.stats.overview).toHaveBeenCalledTimes(2);
  });

  it('should use custom interval', async () => {
    const mockStats = {
      eventsPerMin: 100,
      alertsPerMin: 3,
      uptimeSec: 1800,
    };

    (api.api.stats.overview as any).mockResolvedValue(mockStats);

    const { result } = renderHook(() => useStats(500)); // 500ms interval

    // Initial fetch
    await act(async () => {
      await Promise.resolve();
    });

    expect(api.api.stats.overview).toHaveBeenCalledTimes(1);

    // Advance time by 500ms
    await act(async () => {
      vi.advanceTimersByTime(500);
      await Promise.resolve();
    });

    expect(api.api.stats.overview).toHaveBeenCalledTimes(2);

    // Advance time by another 500ms
    await act(async () => {
      vi.advanceTimersByTime(500);
      await Promise.resolve();
    });

    expect(api.api.stats.overview).toHaveBeenCalledTimes(3);
  });

  it('should clear error on successful fetch after error', async () => {
    const mockError = new Error('API Error');
    const mockStats = {
      eventsPerMin: 100,
      alertsPerMin: 3,
      uptimeSec: 1800,
    };

    (api.api.stats.overview as any)
      .mockRejectedValueOnce(mockError)
      .mockResolvedValueOnce(mockStats);

    // Mock console.error to avoid test noise
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    const { result } = renderHook(() => useStats(1000));

    // Initial fetch should fail
    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.error).toBe('API Error');
    expect(result.current.stats).toBe(null);

    // Second fetch should succeed
    await act(async () => {
      vi.advanceTimersByTime(1000);
      await Promise.resolve();
    });

    expect(result.current.error).toBe(null);
    expect(result.current.stats).toEqual(mockStats);

    consoleSpy.mockRestore();
  });

  it('should clean up interval on unmount', async () => {
    const mockStats = {
      eventsPerMin: 100,
      alertsPerMin: 3,
      uptimeSec: 1800,
    };

    (api.api.stats.overview as any).mockResolvedValue(mockStats);

    const { result, unmount } = renderHook(() => useStats(1000));

    // Initial fetch
    await act(async () => {
      await Promise.resolve();
    });

    expect(api.api.stats.overview).toHaveBeenCalledTimes(1);

    // Unmount the hook
    unmount();

    // Advance time - no additional calls should be made
    await act(async () => {
      vi.advanceTimersByTime(2000);
      await Promise.resolve();
    });

    expect(api.api.stats.overview).toHaveBeenCalledTimes(1);
  });

  it('should update lastUpdate timestamp on successful fetch', async () => {
    const mockStats = {
      eventsPerMin: 100,
      alertsPerMin: 3,
      uptimeSec: 1800,
    };

    (api.api.stats.overview as any).mockResolvedValue(mockStats);

    const { result } = renderHook(() => useStats());

    const initialTime = new Date('2023-12-01T12:00:00Z');
    vi.setSystemTime(initialTime);

    // Initial fetch
    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.lastUpdate).toEqual(initialTime);

    // Advance system time and timers
    const newTime = new Date('2023-12-01T12:01:00Z');
    vi.setSystemTime(newTime);

    // Second fetch - advance timers to trigger interval
    await act(async () => {
      vi.advanceTimersByTime(2000);
      await Promise.resolve();
    });

    // Allow some tolerance for timing precision
    expect(result.current.lastUpdate?.getTime()).toBeGreaterThanOrEqual(newTime.getTime());
  });
});