// API Client for Pulseboard backend
import type { Profile, SimulatorResponse, StatsOverview } from './types';

// Re-export types for backward compatibility
export type { Profile, SimulatorResponse, StatsOverview } from './types';

const BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

// Type definitions for API responses
export interface HealthResponse {
  status: string;
}

export interface ProfileResponse {
  profile: Profile;
}

export interface ProfileRequest {
  profile: Profile;
}

// API client class
export class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string = BASE_URL) {
    this.baseUrl = baseUrl;
  }

  private async request<T>(
    endpoint: string,
    options?: RequestInit
  ): Promise<T> {
    const url = `${this.baseUrl}${endpoint}`;
    const response = await fetch(url, {
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
      ...options,
    });

    if (!response.ok) {
      throw new Error(`API Error: ${response.status} ${response.statusText}`);
    }

    return response.json();
  }

  // Health endpoint
  async getHealth(): Promise<HealthResponse> {
    return this.request<HealthResponse>('/health');
  }

  // Profile endpoints
  async getProfile(): Promise<ProfileResponse> {
    return this.request<ProfileResponse>('/profile');
  }

  async setProfile(profile: Profile): Promise<ProfileResponse> {
    return this.request<ProfileResponse>('/profile', {
      method: 'POST',
      body: JSON.stringify({ profile }),
    });
  }

  // Simulator endpoints
  async startSimulator(params?: { profile?: string; rps?: number; latenessSec?: number }): Promise<SimulatorResponse> {
    const queryParams = new URLSearchParams();
    if (params?.profile) queryParams.append('profile', params.profile);
    if (params?.rps !== undefined) queryParams.append('rps', params.rps.toString());
    if (params?.latenessSec !== undefined) queryParams.append('latenessSec', params.latenessSec.toString());

    const query = queryParams.toString();
    const endpoint = query ? `/sim/start?${query}` : '/sim/start';

    return this.request<SimulatorResponse>(endpoint, {
      method: 'POST',
    });
  }

  async stopSimulator(): Promise<SimulatorResponse> {
    return this.request<SimulatorResponse>('/sim/stop', {
      method: 'POST',
    });
  }

  async getSimulatorStatus(): Promise<{ running: boolean; profile: string; status: string }> {
    return this.request<{ running: boolean; profile: string; status: string }>('/sim/status');
  }

  async updateSimulatorConfig(params: { rps?: number; latenessSec?: number }): Promise<{ status: string; message: string; rps: number; latenessSec: number }> {
    const queryParams = new URLSearchParams();
    if (params.rps !== undefined) queryParams.append('rps', params.rps.toString());
    if (params.latenessSec !== undefined) queryParams.append('latenessSec', params.latenessSec.toString());

    const query = queryParams.toString();
    const endpoint = query ? `/sim/config?${query}` : '/sim/config';

    return this.request<{ status: string; message: string; rps: number; latenessSec: number }>(endpoint, {
      method: 'POST',
    });
  }

  // Stats endpoint
  async getStatsOverview(): Promise<StatsOverview> {
    return this.request<StatsOverview>('/stats/overview');
  }
}

// Default API client instance
export const apiClient = new ApiClient();

// Convenience functions
export const api = {
  health: () => apiClient.getHealth(),
  profile: {
    get: () => apiClient.getProfile(),
    set: (profile: Profile) => apiClient.setProfile(profile),
  },
  simulator: {
    start: (params?: { profile?: string; rps?: number; latenessSec?: number }) => apiClient.startSimulator(params),
    stop: () => apiClient.stopSimulator(),
    status: () => apiClient.getSimulatorStatus(),
    updateConfig: (params: { rps?: number; latenessSec?: number }) => apiClient.updateSimulatorConfig(params),
  },
  stats: {
    overview: () => apiClient.getStatsOverview(),
  },
};