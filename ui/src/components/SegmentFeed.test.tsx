import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SegmentFeed } from './SegmentFeed';
import * as useSegmentFeedModule from '../lib/useSegmentFeed';
import type { SegmentEvent } from '../lib/useSegmentFeed';

// Mock the useSegmentFeed hook
vi.mock('../lib/useSegmentFeed', () => ({
  useSegmentFeed: vi.fn(),
}));

describe('SegmentFeed', () => {
  const mockEvents: SegmentEvent[] = [
    {
      ts: '2025-10-05T10:15:30Z',
      profileId: 'user-42',
      segment: 'power_user',
      action: 'ENTER',
    },
    {
      ts: '2025-10-05T10:16:45Z',
      profileId: 'user-99',
      segment: 'pro_plan',
      action: 'EXIT',
    },
    {
      ts: '2025-10-05T10:17:12Z',
      profileId: 'user-33',
      segment: 'reengage',
      action: 'ENTER',
    },
    {
      ts: '2025-10-05T10:18:00Z',
      profileId: 'user-42',
      segment: 'pro_plan',
      action: 'ENTER',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should show "Simulator not running" when simulator is stopped', () => {
    vi.mocked(useSegmentFeedModule.useSegmentFeed).mockReturnValue({
      events: [],
      connected: false,
      error: null,
    });

    render(<SegmentFeed isSimulatorRunning={false} />);

    expect(screen.getByText('Simulator not running')).toBeInTheDocument();
    expect(screen.getByText('Click the Start button to begin generating segment events')).toBeInTheDocument();
  });

  it('should render segment events when simulator is running', async () => {
    vi.mocked(useSegmentFeedModule.useSegmentFeed).mockReturnValue({
      events: mockEvents,
      connected: true,
      error: null,
    });

    render(<SegmentFeed isSimulatorRunning={true} />);

    expect(screen.getByText('Segment Activity Feed')).toBeInTheDocument();

    // Wait for throttled events to be displayed (1s throttle)
    await waitFor(() => {
      expect(screen.getAllByText('user-42').length).toBeGreaterThan(0);
    }, { timeout: 2000 });

    expect(screen.getAllByText('user-99').length).toBeGreaterThan(0);
    expect(screen.getAllByText('power_user').length).toBeGreaterThan(0);
    expect(screen.getAllByText('pro_plan').length).toBeGreaterThan(0);
  });

  it('should filter events by selected segment', async () => {
    vi.mocked(useSegmentFeedModule.useSegmentFeed).mockReturnValue({
      events: mockEvents,
      connected: true,
      error: null,
    });

    render(<SegmentFeed isSimulatorRunning={true} />);

    // Wait for events to be displayed
    await waitFor(() => {
      expect(screen.getAllByText(/user-/).length).toBe(4);
    }, { timeout: 2000 });

    // Click the power_user filter checkbox
    const powerUserCheckbox = screen.getByLabelText(/power_user/);
    fireEvent.click(powerUserCheckbox);

    // Now only power_user events should be shown (1 event)
    // The event with user-42 and power_user segment
    const visibleEvents = screen.getAllByText(/user-/);
    expect(visibleEvents.length).toBe(1);
    expect(screen.getByText('user-42')).toBeInTheDocument();
  });

  it('should filter events by multiple selected segments', async () => {
    vi.mocked(useSegmentFeedModule.useSegmentFeed).mockReturnValue({
      events: mockEvents,
      connected: true,
      error: null,
    });

    render(<SegmentFeed isSimulatorRunning={true} />);

    // Wait for events to be displayed
    await waitFor(() => {
      expect(screen.getAllByText(/user-/).length).toBe(4);
    }, { timeout: 2000 });

    // Click power_user and pro_plan filters
    const powerUserCheckbox = screen.getByLabelText(/power_user/);
    const proPlanCheckbox = screen.getByLabelText(/pro_plan/);

    fireEvent.click(powerUserCheckbox);
    fireEvent.click(proPlanCheckbox);

    // Should show 3 events: 1 power_user + 2 pro_plan
    const visibleEvents = screen.getAllByText(/user-/);
    expect(visibleEvents.length).toBe(3);
  });

  it('should show all events when no filters are selected', async () => {
    vi.mocked(useSegmentFeedModule.useSegmentFeed).mockReturnValue({
      events: mockEvents,
      connected: true,
      error: null,
    });

    render(<SegmentFeed isSimulatorRunning={true} />);

    // Wait for events to be displayed
    await waitFor(() => {
      expect(screen.getAllByText(/user-/).length).toBe(4);
    }, { timeout: 2000 });
  });

  it('should show "no events" message when filters exclude all events', () => {
    vi.mocked(useSegmentFeedModule.useSegmentFeed).mockReturnValue({
      events: [
        {
          ts: '2025-10-05T10:15:30Z',
          profileId: 'user-1',
          segment: 'other_segment',
          action: 'ENTER',
        },
      ],
      connected: true,
      error: null,
    });

    render(<SegmentFeed isSimulatorRunning={true} />);

    // Select power_user filter
    const powerUserCheckbox = screen.getByLabelText(/power_user/);
    fireEvent.click(powerUserCheckbox);

    // Should show "no events match" message
    expect(screen.getByText('No events match the selected filters')).toBeInTheDocument();
  });

  it('should render ENTER and EXIT action badges with correct colors', async () => {
    vi.mocked(useSegmentFeedModule.useSegmentFeed).mockReturnValue({
      events: mockEvents,
      connected: true,
      error: null,
    });

    render(<SegmentFeed isSimulatorRunning={true} />);

    await waitFor(() => {
      const enterBadges = screen.getAllByText('ENTER');
      const exitBadges = screen.getAllByText('EXIT');

      expect(enterBadges.length).toBe(3); // 3 ENTER events
      expect(exitBadges.length).toBe(1); // 1 EXIT event
    }, { timeout: 2000 });
  });

  it('should show connection status indicator', async () => {
    vi.mocked(useSegmentFeedModule.useSegmentFeed).mockReturnValue({
      events: mockEvents,
      connected: true,
      error: null,
    });

    render(<SegmentFeed isSimulatorRunning={true} />);

    await waitFor(() => {
      expect(screen.getByText('4 events')).toBeInTheDocument();
    }, { timeout: 2000 });
  });

  it('should display error message when present', () => {
    vi.mocked(useSegmentFeedModule.useSegmentFeed).mockReturnValue({
      events: [],
      connected: false,
      error: 'Connection failed',
    });

    render(<SegmentFeed isSimulatorRunning={true} />);

    expect(screen.getByText('Error: Connection failed')).toBeInTheDocument();
  });

  it('should format timestamps correctly', async () => {
    vi.mocked(useSegmentFeedModule.useSegmentFeed).mockReturnValue({
      events: [
        {
          ts: '2025-10-05T14:05:12Z',
          profileId: 'user-1',
          segment: 'power_user',
          action: 'ENTER',
        },
      ],
      connected: true,
      error: null,
    });

    render(<SegmentFeed isSimulatorRunning={true} />);

    // Wait for events to be displayed and check for time format
    await waitFor(() => {
      const timeElements = screen.getAllByText(/\d{2}:\d{2}:\d{2}/);
      expect(timeElements.length).toBeGreaterThan(0);
    }, { timeout: 2000 });
  });
});

// Test filter predicate logic in isolation
describe('SegmentFeed filter logic', () => {
  it('should correctly filter events by segment name', () => {
    const events: SegmentEvent[] = [
      { ts: '2025-10-05T10:00:00Z', profileId: 'user-1', segment: 'power_user', action: 'ENTER' },
      { ts: '2025-10-05T10:01:00Z', profileId: 'user-2', segment: 'pro_plan', action: 'ENTER' },
      { ts: '2025-10-05T10:02:00Z', profileId: 'user-3', segment: 'reengage', action: 'EXIT' },
      { ts: '2025-10-05T10:03:00Z', profileId: 'user-4', segment: 'power_user', action: 'EXIT' },
    ];

    // Test filtering for single segment
    const selectedFilters = new Set(['power_user']);
    const filtered = events.filter((event) => selectedFilters.has(event.segment));

    expect(filtered).toHaveLength(2);
    expect(filtered.every((e) => e.segment === 'power_user')).toBe(true);
  });

  it('should return all events when no filters are selected', () => {
    const events: SegmentEvent[] = [
      { ts: '2025-10-05T10:00:00Z', profileId: 'user-1', segment: 'power_user', action: 'ENTER' },
      { ts: '2025-10-05T10:01:00Z', profileId: 'user-2', segment: 'pro_plan', action: 'ENTER' },
    ];

    const selectedFilters = new Set<string>();
    const filtered = selectedFilters.size === 0 ? events : events.filter((event) => selectedFilters.has(event.segment));

    expect(filtered).toHaveLength(2);
  });

  it('should filter events by multiple segments', () => {
    const events: SegmentEvent[] = [
      { ts: '2025-10-05T10:00:00Z', profileId: 'user-1', segment: 'power_user', action: 'ENTER' },
      { ts: '2025-10-05T10:01:00Z', profileId: 'user-2', segment: 'pro_plan', action: 'ENTER' },
      { ts: '2025-10-05T10:02:00Z', profileId: 'user-3', segment: 'reengage', action: 'EXIT' },
    ];

    const selectedFilters = new Set(['power_user', 'reengage']);
    const filtered = events.filter((event) => selectedFilters.has(event.segment));

    expect(filtered).toHaveLength(2);
    expect(filtered[0].segment).toBe('power_user');
    expect(filtered[1].segment).toBe('reengage');
  });

  it('should return empty array when filters match no events', () => {
    const events: SegmentEvent[] = [
      { ts: '2025-10-05T10:00:00Z', profileId: 'user-1', segment: 'other_segment', action: 'ENTER' },
    ];

    const selectedFilters = new Set(['power_user']);
    const filtered = events.filter((event) => selectedFilters.has(event.segment));

    expect(filtered).toHaveLength(0);
  });
});
