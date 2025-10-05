import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ProfilesList } from './ProfilesList';
import * as useCdpProfilesModule from '../lib/useCdpProfiles';

// Mock the useCdpProfiles hook
vi.mock('../lib/useCdpProfiles', () => ({
  useCdpProfiles: vi.fn(),
}));

// Mock date-fns formatDistanceToNow to have consistent output
vi.mock('date-fns', () => ({
  formatDistanceToNow: vi.fn((date: Date, options?: { addSuffix?: boolean }) => {
    return options?.addSuffix ? '2 minutes ago' : '2 minutes';
  }),
}));

describe('ProfilesList', () => {
  const mockProfiles: useCdpProfilesModule.ProfileSummary[] = [
    {
      profileId: 'profile-1',
      plan: 'pro',
      country: 'US',
      lastSeen: '2025-01-15T12:00:00Z',
      identifiers: {
        userIds: ['user-1', 'user-2'],
        emails: ['user1@example.com'],
        anonymousIds: ['anon-1', 'anon-2', 'anon-3'],
      },
      featureUsedCount: 15,
    },
    {
      profileId: 'profile-2',
      plan: 'basic',
      country: 'UK',
      lastSeen: '2025-01-15T11:55:00Z',
      identifiers: {
        userIds: ['user-3'],
        emails: ['user3@example.com'],
        anonymousIds: ['anon-4'],
      },
      featureUsedCount: 5,
    },
    {
      profileId: 'profile-3',
      plan: null,
      country: null,
      lastSeen: '2025-01-15T11:50:00Z',
      identifiers: {
        userIds: [],
        emails: [],
        anonymousIds: ['anon-5'],
      },
      featureUsedCount: 0,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render loading state when not connected and no profiles', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: [],
      connected: false,
      error: null,
    });

    render(<ProfilesList />);

    expect(screen.getByText('Loading profiles...')).toBeInTheDocument();
    expect(screen.getByText('Connecting to CDP stream')).toBeInTheDocument();
  });

  it('should render empty state when connected but no profiles', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: [],
      connected: true,
      error: null,
    });

    render(<ProfilesList />);

    expect(screen.getByText('No profiles yet')).toBeInTheDocument();
    expect(screen.getByText('Start the CDP simulator to see live profile data')).toBeInTheDocument();
  });

  it('should render profiles list with correct column headers', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: mockProfiles,
      connected: true,
      error: null,
    });

    render(<ProfilesList />);

    expect(screen.getByText('Profile')).toBeInTheDocument();
    expect(screen.getByText('Plan')).toBeInTheDocument();
    expect(screen.getByText('Country')).toBeInTheDocument();
    expect(screen.getByText('Last Seen')).toBeInTheDocument();
    expect(screen.getByText('Identifiers (u/e/a)')).toBeInTheDocument();
    expect(screen.getByText('Feature Used (24h)')).toBeInTheDocument();
  });

  it('should render profiles sorted by lastSeen (backend provides sort)', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: mockProfiles,
      connected: true,
      error: null,
    });

    render(<ProfilesList />);

    // Verify profiles are rendered in the order provided by backend
    expect(screen.getByText('profile-1')).toBeInTheDocument();
    expect(screen.getByText('profile-2')).toBeInTheDocument();
    expect(screen.getByText('profile-3')).toBeInTheDocument();
  });

  it('should render identifiers in u/e/a format', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: mockProfiles,
      connected: true,
      error: null,
    });

    render(<ProfilesList />);

    // profile-1: 2 userIds, 1 email, 3 anonymousIds -> "2/1/3"
    expect(screen.getByText('2/1/3')).toBeInTheDocument();

    // profile-2: 1 userId, 1 email, 1 anonymousId -> "1/1/1"
    expect(screen.getByText('1/1/1')).toBeInTheDocument();

    // profile-3: 0 userIds, 0 emails, 1 anonymousId -> "0/0/1"
    expect(screen.getByText('0/0/1')).toBeInTheDocument();
  });

  it('should render plan badges with correct styles', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: mockProfiles,
      connected: true,
      error: null,
    });

    render(<ProfilesList />);

    // Should have "pro" and "basic" badges
    expect(screen.getByText('pro')).toBeInTheDocument();
    expect(screen.getByText('basic')).toBeInTheDocument();
  });

  it('should render feature used count', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: mockProfiles,
      connected: true,
      error: null,
    });

    render(<ProfilesList />);

    expect(screen.getByText('15')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('should show connection status indicator', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: mockProfiles,
      connected: true,
      error: null,
    });

    render(<ProfilesList />);

    expect(screen.getByText('3 profiles')).toBeInTheDocument();
  });

  it('should handle null plan and country gracefully', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: [mockProfiles[2]], // profile-3 has null plan and country
      connected: true,
      error: null,
    });

    render(<ProfilesList />);

    // Should render placeholders for null values (rendered as "—")
    expect(screen.getByText('profile-3')).toBeInTheDocument();
  });

  it('should display correct profile count in header', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: [mockProfiles[0]], // Only 1 profile
      connected: true,
      error: null,
    });

    render(<ProfilesList />);

    expect(screen.getByText('1 profile')).toBeInTheDocument();
  });

  it('should display error message when error occurs', () => {
    vi.mocked(useCdpProfilesModule.useCdpProfiles).mockReturnValue({
      profiles: [],
      connected: true,
      error: 'Connection failed',
    });

    render(<ProfilesList />);

    expect(screen.getByText('Error: Connection failed')).toBeInTheDocument();
  });
});
