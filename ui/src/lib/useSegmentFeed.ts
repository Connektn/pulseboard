import { useEffect, useState } from 'react';
import { useEventSource } from './useEventSource';
import type { SegmentEvent } from './types';

// Re-export for backward compatibility
export type { SegmentEvent, SegmentAction } from './types';

// Hook state
export interface SegmentFeedState {
  events: SegmentEvent[];
  connected: boolean;
  error: string | null;
}

/**
 * Hook for consuming the CDP segment activity feed SSE stream.
 *
 * Subscribes to /sse/cdp/segments and maintains a list of segment events.
 * Events are capped at maxEvents (default 200).
 *
 * @param enabled - Whether to establish the SSE connection
 * @param maxEvents - Maximum number of events to keep in memory
 */
export function useSegmentFeed(enabled: boolean = true, maxEvents: number = 200): SegmentFeedState {
  const { lastMessage, connected, error } = useEventSource<SegmentEvent>('/sse/cdp/segments', { enabled });
  const [events, setEvents] = useState<SegmentEvent[]>([]);

  useEffect(() => {
    if (!lastMessage) return;

    // Handle different message types
    switch (lastMessage.type) {
      case 'segment_event':
        if (lastMessage.data) {
          setEvents((prev) => {
            const newEvents = [lastMessage.data!, ...prev];
            // Cap to maxEvents
            return newEvents.slice(0, maxEvents);
          });
        }
        break;
      case 'connection':
        // Connection established
        break;
      case 'error':
        console.error('Segment feed stream error:', lastMessage.message);
        break;
      default:
        // Ignore unknown message types
        break;
    }
  }, [lastMessage, maxEvents]);

  return {
    events,
    connected,
    error,
  };
}
