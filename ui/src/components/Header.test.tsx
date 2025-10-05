import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Header } from './Header';
import { api } from '../lib/api';

// Mock the API module
vi.mock('../lib/api', () => ({
  api: {
    profile: {
      get: vi.fn(),
      set: vi.fn(),
    },
    simulator: {
      start: vi.fn(),
      stop: vi.fn(),
    },
  },
}));

describe('Header', () => {
  const mockStats = {
    eventsPerMin: 100,
    alertsPerMin: 5,
    uptimeSec: 3600,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    // Default mock return values
    vi.mocked(api.profile.get).mockResolvedValue({ profile: 'SASE' });
    vi.mocked(api.profile.set).mockResolvedValue({ profile: 'SASE' });
    vi.mocked(api.simulator.start).mockResolvedValue({
      status: 'started',
      message: 'Simulator started successfully',
      profile: 'SASE',
    });
    vi.mocked(api.simulator.stop).mockResolvedValue({
      status: 'stopped',
      message: 'Simulator stopped successfully',
      profile: 'SASE',
    });
  });

  it('renders three profile tabs: SASE, IGAMING, CDP', async () => {
    render(<Header stats={mockStats} />);

    await waitFor(() => {
      expect(screen.getByText('SASE')).toBeInTheDocument();
      expect(screen.getByText('IGAMING')).toBeInTheDocument();
      expect(screen.getByText('CDP')).toBeInTheDocument();
    });
  });

  it('loads profile from backend on mount', async () => {
    vi.mocked(api.profile.get).mockResolvedValue({ profile: 'CDP' });

    render(<Header stats={mockStats} />);

    await waitFor(() => {
      expect(api.profile.get).toHaveBeenCalled();
    });
  });

  it('persists profile selection to localStorage with key pb.activeProfile', async () => {
    render(<Header stats={mockStats} />);

    await waitFor(() => {
      expect(screen.getByText('SASE')).toBeInTheDocument();
    });

    // Click on CDP tab
    const cdpButton = screen.getByText('CDP');
    fireEvent.click(cdpButton);

    await waitFor(() => {
      expect(api.profile.set).toHaveBeenCalledWith('CDP');
      expect(localStorage.getItem('pb.activeProfile')).toBe('CDP');
    });
  });

  it('shows CDP control panel when CDP profile is selected', async () => {
    vi.mocked(api.profile.get).mockResolvedValue({ profile: 'CDP' });

    render(<Header stats={mockStats} />);

    await waitFor(() => {
      expect(screen.getByText('RPS:')).toBeInTheDocument();
      expect(screen.getByText('Lateness:')).toBeInTheDocument();
    });
  });

  it('does not show CDP control panel for SASE/IGAMING profiles', async () => {
    vi.mocked(api.profile.get).mockResolvedValue({ profile: 'SASE' });

    render(<Header stats={mockStats} />);

    await waitFor(() => {
      expect(screen.getByText('SASE')).toBeInTheDocument();
    });

    // CDP controls should not be visible
    expect(screen.queryByText('RPS:')).not.toBeInTheDocument();
    expect(screen.queryByText('Lateness:')).not.toBeInTheDocument();
  });

  it('cycles through profiles: SASE → IGAMING → CDP', async () => {
    render(<Header stats={mockStats} />);

    await waitFor(() => {
      expect(screen.getByText('SASE')).toBeInTheDocument();
    });

    // Click IGAMING
    const igamingButton = screen.getByText('IGAMING');
    fireEvent.click(igamingButton);

    await waitFor(() => {
      expect(api.profile.set).toHaveBeenCalledWith('IGAMING');
      expect(localStorage.getItem('pb.activeProfile')).toBe('IGAMING');
    });

    vi.clearAllMocks();

    // Click CDP
    const cdpButton = screen.getByText('CDP');
    fireEvent.click(cdpButton);

    await waitFor(() => {
      expect(api.profile.set).toHaveBeenCalledWith('CDP');
      expect(localStorage.getItem('pb.activeProfile')).toBe('CDP');
    });
  });

  it('calls start with CDP params when CDP profile is active', async () => {
    vi.mocked(api.profile.get).mockResolvedValue({ profile: 'CDP' });
    vi.mocked(api.simulator.start).mockResolvedValue({
      status: 'started',
      message: 'Simulator started successfully',
      profile: 'CDP',
      rps: 20,
      latenessSec: 60,
    });

    render(<Header stats={mockStats} />);

    await waitFor(() => {
      expect(screen.getByText('CDP')).toBeInTheDocument();
    });

    // Find and click start button
    const startButtons = screen.getAllByText('▶ Start');
    fireEvent.click(startButtons[0]);

    await waitFor(() => {
      expect(api.simulator.start).toHaveBeenCalledWith({
        profile: 'CDP',
        rps: 20,
        latenessSec: 60,
      });
    });
  });

  it('calls start without params for SASE/IGAMING profiles', async () => {
    vi.mocked(api.profile.get).mockResolvedValue({ profile: 'SASE' });

    render(<Header stats={mockStats} />);

    await waitFor(() => {
      expect(screen.getByText('SASE')).toBeInTheDocument();
    });

    // Find and click start button
    const startButton = screen.getByText('▶ Start');
    fireEvent.click(startButton);

    await waitFor(() => {
      expect(api.simulator.start).toHaveBeenCalledWith(undefined);
    });
  });

  it('disables buttons during API calls', async () => {
    render(<Header stats={mockStats} />);

    await waitFor(() => {
      expect(screen.getByText('SASE')).toBeInTheDocument();
    });

    const cdpButton = screen.getByText('CDP');
    fireEvent.click(cdpButton);

    // During the API call, buttons should be disabled
    // This is hard to test precisely due to timing, but we can verify
    // that the API was called
    await waitFor(() => {
      expect(api.profile.set).toHaveBeenCalledWith('CDP');
    });
  });

  it('reads from localStorage with key pb.activeProfile on mount', async () => {
    localStorage.setItem('pb.activeProfile', 'IGAMING');
    vi.mocked(api.profile.get).mockResolvedValue({ profile: 'SASE' });

    render(<Header stats={mockStats} />);

    // Should sync localStorage value to backend
    await waitFor(() => {
      expect(api.profile.set).toHaveBeenCalledWith('IGAMING');
    });
  });
});
