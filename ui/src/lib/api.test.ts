import { describe, it, expect, beforeEach } from 'vitest';
import { ApiClient, api } from './api';

describe('ApiClient', () => {
  let apiClient: ApiClient;
  const mockFetch = vi.fn();

  beforeEach(() => {
    global.fetch = mockFetch;
    apiClient = new ApiClient('http://test-api');
  });

  describe('request method', () => {
    it('should make a GET request with correct headers', async () => {
      const mockResponse = {
        ok: true,
        json: vi.fn().mockResolvedValue({ status: 'UP' }),
      };
      mockFetch.mockResolvedValue(mockResponse);

      const result = await apiClient.getHealth();

      expect(mockFetch).toHaveBeenCalledWith('http://test-api/health', {
        headers: {
          'Content-Type': 'application/json',
        },
      });
      expect(result).toEqual({ status: 'UP' });
    });

    it('should make a POST request with correct body and headers', async () => {
      const mockResponse = {
        ok: true,
        json: vi.fn().mockResolvedValue({ profile: 'SASE' }),
      };
      mockFetch.mockResolvedValue(mockResponse);

      const result = await apiClient.setProfile('SASE');

      expect(mockFetch).toHaveBeenCalledWith('http://test-api/profile', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ profile: 'SASE' }),
      });
      expect(result).toEqual({ profile: 'SASE' });
    });

    it('should throw error when response is not ok', async () => {
      const mockResponse = {
        ok: false,
        status: 404,
        statusText: 'Not Found',
      };
      mockFetch.mockResolvedValue(mockResponse);

      await expect(apiClient.getHealth()).rejects.toThrow('API Error: 404 Not Found');
    });

    it('should handle network errors', async () => {
      mockFetch.mockRejectedValue(new Error('Network error'));

      await expect(apiClient.getHealth()).rejects.toThrow('Network error');
    });
  });

  describe('health endpoint', () => {
    it('should call correct endpoint', async () => {
      const mockResponse = {
        ok: true,
        json: vi.fn().mockResolvedValue({ status: 'UP' }),
      };
      mockFetch.mockResolvedValue(mockResponse);

      await apiClient.getHealth();

      expect(mockFetch).toHaveBeenCalledWith('http://test-api/health', {
        headers: {
          'Content-Type': 'application/json',
        },
      });
    });
  });

  describe('profile endpoints', () => {
    it('should get profile', async () => {
      const mockResponse = {
        ok: true,
        json: vi.fn().mockResolvedValue({ profile: 'IGAMING' }),
      };
      mockFetch.mockResolvedValue(mockResponse);

      const result = await apiClient.getProfile();

      expect(mockFetch).toHaveBeenCalledWith('http://test-api/profile', {
        headers: {
          'Content-Type': 'application/json',
        },
      });
      expect(result).toEqual({ profile: 'IGAMING' });
    });

    it('should set profile', async () => {
      const mockResponse = {
        ok: true,
        json: vi.fn().mockResolvedValue({ profile: 'SASE' }),
      };
      mockFetch.mockResolvedValue(mockResponse);

      const result = await apiClient.setProfile('SASE');

      expect(mockFetch).toHaveBeenCalledWith('http://test-api/profile', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ profile: 'SASE' }),
      });
      expect(result).toEqual({ profile: 'SASE' });
    });
  });

  describe('simulator endpoints', () => {
    it('should start simulator', async () => {
      const mockResponse = {
        ok: true,
        json: vi.fn().mockResolvedValue({
          status: 'started',
          message: 'Simulator started',
          profile: 'SASE',
        }),
      };
      mockFetch.mockResolvedValue(mockResponse);

      const result = await apiClient.startSimulator();

      expect(mockFetch).toHaveBeenCalledWith('http://test-api/sim/start', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      });
      expect(result).toEqual({
        status: 'started',
        message: 'Simulator started',
        profile: 'SASE',
      });
    });

    it('should stop simulator', async () => {
      const mockResponse = {
        ok: true,
        json: vi.fn().mockResolvedValue({
          status: 'stopped',
          message: 'Simulator stopped',
          profile: 'SASE',
        }),
      };
      mockFetch.mockResolvedValue(mockResponse);

      const result = await apiClient.stopSimulator();

      expect(mockFetch).toHaveBeenCalledWith('http://test-api/sim/stop', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      });
      expect(result).toEqual({
        status: 'stopped',
        message: 'Simulator stopped',
        profile: 'SASE',
      });
    });
  });

  describe('stats endpoint', () => {
    it('should get stats overview', async () => {
      const mockResponse = {
        ok: true,
        json: vi.fn().mockResolvedValue({
          eventsPerMin: 120,
          alertsPerMin: 5,
          uptimeSec: 3600,
        }),
      };
      mockFetch.mockResolvedValue(mockResponse);

      const result = await apiClient.getStatsOverview();

      expect(mockFetch).toHaveBeenCalledWith('http://test-api/stats/overview', {
        headers: {
          'Content-Type': 'application/json',
        },
      });
      expect(result).toEqual({
        eventsPerMin: 120,
        alertsPerMin: 5,
        uptimeSec: 3600,
      });
    });
  });
});

describe('convenience api object', () => {
  const mockFetch = vi.fn();

  beforeEach(() => {
    global.fetch = mockFetch;
  });

  it('should provide health method', async () => {
    const mockResponse = {
      ok: true,
      json: vi.fn().mockResolvedValue({ status: 'UP' }),
    };
    mockFetch.mockResolvedValue(mockResponse);

    const result = await api.health();

    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/health',
      expect.objectContaining({
        headers: {
          'Content-Type': 'application/json',
        },
      })
    );
    expect(result).toEqual({ status: 'UP' });
  });

  it('should provide profile methods', async () => {
    const mockResponse = {
      ok: true,
      json: vi.fn().mockResolvedValue({ profile: 'SASE' }),
    };
    mockFetch.mockResolvedValue(mockResponse);

    const result = await api.profile.get();

    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/profile',
      expect.objectContaining({
        headers: {
          'Content-Type': 'application/json',
        },
      })
    );
    expect(result).toEqual({ profile: 'SASE' });
  });

  it('should provide simulator methods', async () => {
    const mockResponse = {
      ok: true,
      json: vi.fn().mockResolvedValue({
        status: 'started',
        message: 'Simulator started',
        profile: 'SASE',
      }),
    };
    mockFetch.mockResolvedValue(mockResponse);

    const result = await api.simulator.start();

    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/sim/start',
      expect.objectContaining({
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      })
    );
    expect(result).toEqual({
      status: 'started',
      message: 'Simulator started',
      profile: 'SASE',
    });
  });

  it('should provide stats methods', async () => {
    const mockResponse = {
      ok: true,
      json: vi.fn().mockResolvedValue({
        eventsPerMin: 100,
        alertsPerMin: 3,
        uptimeSec: 1800,
      }),
    };
    mockFetch.mockResolvedValue(mockResponse);

    const result = await api.stats.overview();

    expect(mockFetch).toHaveBeenCalledWith(
      'http://localhost:8080/stats/overview',
      expect.objectContaining({
        headers: {
          'Content-Type': 'application/json',
        },
      })
    );
    expect(result).toEqual({
      eventsPerMin: 100,
      alertsPerMin: 3,
      uptimeSec: 1800,
    });
  });
});