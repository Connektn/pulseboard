import { useState, useEffect, useRef, useMemo } from 'react';
import { useSegmentFeed, type SegmentEvent, type SegmentAction } from '../lib/useSegmentFeed';

interface SegmentFeedProps {
  isSimulatorRunning: boolean;
}

// Available segment filters
const SEGMENT_FILTERS = ['power_user', 'pro_plan', 'reengage'] as const;

export function SegmentFeed({ isSimulatorRunning }: SegmentFeedProps) {
  const { events, connected, error } = useSegmentFeed(isSimulatorRunning);
  const [selectedFilters, setSelectedFilters] = useState<Set<string>>(new Set());
  const [isUserScrolling, setIsUserScrolling] = useState(false);
  const feedRef = useRef<HTMLDivElement>(null);
  const lastScrollTop = useRef(0);
  const userScrollTimeout = useRef<number | null>(null);

  // Throttled events: update at most once per second
  const [displayedEvents, setDisplayedEvents] = useState<SegmentEvent[]>([]);

  useEffect(() => {
    const interval = setInterval(() => {
      setDisplayedEvents(events);
    }, 1000); // Throttle to 1s

    return () => clearInterval(interval);
  }, [events]);

  // Filter events based on selected filters
  const filteredEvents = useMemo(() => {
    if (selectedFilters.size === 0) {
      return displayedEvents;
    }
    return displayedEvents.filter((event) =>
      selectedFilters.has(event.segment)
    );
  }, [displayedEvents, selectedFilters]);

  // Handle scroll detection
  useEffect(() => {
    const handleScroll = () => {
      if (!feedRef.current) return;

      const { scrollTop, scrollHeight, clientHeight } = feedRef.current;

      // User is scrolling up or hovering
      if (scrollTop < lastScrollTop.current || scrollTop + clientHeight < scrollHeight - 50) {
        setIsUserScrolling(true);

        // Clear existing timeout
        if (userScrollTimeout.current) {
          clearTimeout(userScrollTimeout.current);
        }

        // Resume autoscroll after 3 seconds of no scrolling
        userScrollTimeout.current = setTimeout(() => {
          setIsUserScrolling(false);
        }, 3000);
      } else {
        // User scrolled to bottom
        setIsUserScrolling(false);
      }

      lastScrollTop.current = scrollTop;
    };

    const feedElement = feedRef.current;
    if (feedElement) {
      feedElement.addEventListener('scroll', handleScroll);
      return () => {
        feedElement.removeEventListener('scroll', handleScroll);
        if (userScrollTimeout.current) {
          clearTimeout(userScrollTimeout.current);
        }
      };
    }
  }, []);

  // Autoscroll to top when new events arrive (unless user is scrolling)
  useEffect(() => {
    if (!isUserScrolling && feedRef.current) {
      feedRef.current.scrollTop = 0;
    }
  }, [filteredEvents, isUserScrolling]);

  // Toggle filter
  const toggleFilter = (filter: string) => {
    setSelectedFilters((prev) => {
      const newFilters = new Set(prev);
      if (newFilters.has(filter)) {
        newFilters.delete(filter);
      } else {
        newFilters.add(filter);
      }
      return newFilters;
    });
  };

  // Format timestamp as HH:mm:ss
  const formatTime = (isoString: string): string => {
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch {
      return 'Invalid time';
    }
  };

  // Get badge style for action
  const getActionBadge = (action: SegmentAction) => {
    const isEnter = action === 'ENTER';
    return (
      <span
        style={{
          backgroundColor: isEnter ? '#10b98122' : '#ef444422',
          color: isEnter ? '#10b981' : '#ef4444',
          padding: '0.125rem 0.5rem',
          borderRadius: '0.25rem',
          fontSize: '0.75rem',
          fontWeight: '600',
          textTransform: 'uppercase',
        }}
      >
        {action}
      </span>
    );
  };

  // Get color for segment name
  const getSegmentColor = (segment: string): string => {
    switch (segment) {
      case 'power_user':
        return '#3b82f6'; // Blue
      case 'pro_plan':
        return '#8b5cf6'; // Purple
      case 'reengage':
        return '#f59e0b'; // Amber
      default:
        return '#6b7280'; // Gray
    }
  };

  // Simulator not running state
  if (!isSimulatorRunning) {
    return (
      <div
        style={{
          backgroundColor: 'white',
          borderRadius: '0.5rem',
          padding: '2rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          border: '1px solid #e2e8f0',
        }}
      >
        <div style={{ textAlign: 'center', color: '#6b7280' }}>
          <div style={{ fontSize: '1rem', fontWeight: '600', marginBottom: '0.5rem' }}>
            Simulator not running
          </div>
          <div style={{ fontSize: '0.875rem' }}>
            Click the Start button to begin generating segment events
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        backgroundColor: 'white',
        borderRadius: '0.5rem',
        boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
        border: '1px solid #e2e8f0',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        height: '600px',
      }}
    >
      {/* Header with filters */}
      <div
        style={{
          padding: '1rem',
          borderBottom: '1px solid #e2e8f0',
          backgroundColor: '#f9fafb',
        }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '0.75rem',
          }}
        >
          <h3
            style={{
              margin: 0,
              fontSize: '1rem',
              fontWeight: '600',
              color: '#374151',
            }}
          >
            Segment Activity Feed
          </h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <div
              style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: connected ? '#10b981' : '#ef4444',
              }}
            />
            <span style={{ fontSize: '0.75rem', color: '#6b7280' }}>
              {filteredEvents.length} {filteredEvents.length === 1 ? 'event' : 'events'}
            </span>
          </div>
        </div>

        {/* Filters */}
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
          {SEGMENT_FILTERS.map((filter) => (
            <label
              key={filter}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                fontSize: '0.875rem',
                cursor: 'pointer',
                userSelect: 'none',
              }}
            >
              <input
                type="checkbox"
                checked={selectedFilters.has(filter)}
                onChange={() => toggleFilter(filter)}
                style={{ cursor: 'pointer' }}
              />
              <span
                style={{
                  color: getSegmentColor(filter),
                  fontWeight: '500',
                }}
              >
                {filter}
              </span>
            </label>
          ))}
        </div>

        {error && (
          <div
            style={{
              marginTop: '0.5rem',
              padding: '0.5rem',
              backgroundColor: '#fef2f2',
              color: '#ef4444',
              borderRadius: '0.25rem',
              fontSize: '0.75rem',
            }}
          >
            Error: {error}
          </div>
        )}
      </div>

      {/* Feed list */}
      <div
        ref={feedRef}
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '1rem',
        }}
      >
        {filteredEvents.length === 0 ? (
          <div
            style={{
              textAlign: 'center',
              color: '#9ca3af',
              padding: '2rem',
              fontSize: '0.875rem',
            }}
          >
            {selectedFilters.size > 0
              ? 'No events match the selected filters'
              : 'Waiting for segment events...'}
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {filteredEvents.map((event, index) => (
              <div
                key={`${event.ts}-${event.profileId}-${index}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.75rem',
                  padding: '0.5rem',
                  borderRadius: '0.25rem',
                  backgroundColor: '#f9fafb',
                  fontSize: '0.875rem',
                  fontFamily: 'monospace',
                }}
              >
                <span style={{ color: '#6b7280', minWidth: '70px' }}>
                  {formatTime(event.ts)}
                </span>
                <span style={{ color: '#111827' }}>•</span>
                <span
                  style={{
                    color: '#111827',
                    fontWeight: '500',
                    minWidth: '120px',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                  title={event.profileId}
                >
                  {event.profileId}
                </span>
                <span style={{ color: '#111827' }}>•</span>
                {getActionBadge(event.action)}
                <span style={{ color: '#111827' }}>•</span>
                <span
                  style={{
                    color: getSegmentColor(event.segment),
                    fontWeight: '600',
                  }}
                >
                  {event.segment}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Autoscroll indicator */}
      {isUserScrolling && (
        <div
          style={{
            padding: '0.5rem',
            backgroundColor: '#fef3c7',
            color: '#92400e',
            textAlign: 'center',
            fontSize: '0.75rem',
            borderTop: '1px solid #fcd34d',
          }}
        >
          Autoscroll paused • Scroll to bottom or wait to resume
        </div>
      )}
    </div>
  );
}
