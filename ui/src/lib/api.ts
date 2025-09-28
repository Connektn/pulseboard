// API Client for Pulseboard backend
const BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

// Type definitions for API responses
export interface HealthResponse {
  status: string;
}

export interface ProfileResponse {
  profile: 'SASE' | 'IGAMING';
}

export interface ProfileRequest {
  profile: 'SASE' | 'IGAMING';
}

export interface SimulatorResponse {
  status: 'started' | 'stopped' | 'running' | 'not_running';
  message: string;
  profile: 'SASE' | 'IGAMING';
}

export interface StatsOverview {
  eventsPerMin: number;
  alertsPerMin: number;
  uptimeSec: number;
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

  async setProfile(profile: 'SASE' | 'IGAMING'): Promise<ProfileResponse> {
    return this.request<ProfileResponse>('/profile', {
      method: 'POST',
      body: JSON.stringify({ profile }),
    });
  }

  // Simulator endpoints
  async startSimulator(): Promise<SimulatorResponse> {
    return this.request<SimulatorResponse>('/sim/start', {
      method: 'POST',
    });
  }

  async stopSimulator(): Promise<SimulatorResponse> {
    return this.request<SimulatorResponse>('/sim/stop', {
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
    set: (profile: 'SASE' | 'IGAMING') => apiClient.setProfile(profile),
  },
  simulator: {
    start: () => apiClient.startSimulator(),
    stop: () => apiClient.stopSimulator(),
  },
  stats: {
    overview: () => apiClient.getStatsOverview(),
  },
};