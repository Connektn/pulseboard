import { useEffect, useState, useRef } from 'react';
import { useCdpProfiles, type ProfileSummary } from './useCdpProfiles';
import { useSegmentFeed, type SegmentEvent } from './useSegmentFeed';
import { SlidingWindow, UniqueCountWindow } from './slidingWindow';

/**
 * CDP KPI metrics computed from real-time SSE streams.
 */
export interface CdpKpis {
  activeProfiles: number; // Unique profiles active in last 5 minutes
  eventsPerMinute: number; // Rate of all CDP events per minute
  segmentEntersPerMinute: number; // Rate of ENTER events per minute
  segmentEntersHistory: number[]; // Last N data points for sparkline
}

interface EventTimestamp {
  timestamp: number;
}

interface ProfileEvent extends EventTimestamp {
  key: string; // profileId
}

/**
 * Hook for computing CDP KPIs from SSE streams.
 *
 * Subscribes to /sse/cdp/profiles and /sse/cdp/segments and computes:
 * - Active profiles (unique profiles seen in last 5 minutes)
 * - Events per minute (rate of all profile updates)
 * - Segment ENTERs per minute (rate of ENTER events)
 * - Sparkline data (ENTERs/min for last 2 minutes)
 *
 * Updates are throttled to ~1s to avoid jank.
 *
 * @param enabled - Whether to compute KPIs (tied to simulator running)
 */
export function useCdpKpis(enabled: boolean = true): CdpKpis {
  // Subscribe to SSE streams
  const { profiles } = useCdpProfiles(enabled);
  const { events: segmentEvents } = useSegmentFeed(enabled);

  // Sliding windows for KPI calculations
  const profileWindow = useRef(new UniqueCountWindow<ProfileEvent>(5 * 60 * 1000)); // 5 min
  const eventWindow = useRef(new SlidingWindow<EventTimestamp>(5 * 60 * 1000)); // 5 min
  const enterWindow = useRef(new SlidingWindow<EventTimestamp>(5 * 60 * 1000)); // 5 min

  // Sparkline data (last 2 minutes, 1 data point per second = 120 points)
  const [sparklineData, setSparklineData] = useState<number[]>([]);

  // Current KPIs (throttled updates)
  const [kpis, setKpis] = useState<CdpKpis>({
    activeProfiles: 0,
    eventsPerMinute: 0,
    segmentEntersPerMinute: 0,
    segmentEntersHistory: [],
  });

  // Track profiles seen
  useEffect(() => {
    if (!enabled) return;

    profiles.forEach((profile) => {
      const timestamp = new Date(profile.lastSeen).getTime();
      profileWindow.current.add({
        key: profile.profileId,
        timestamp,
      });
      eventWindow.current.add({ timestamp });
    });
  }, [profiles, enabled]);

  // Track segment ENTER events
  useEffect(() => {
    if (!enabled) return;

    segmentEvents.forEach((event) => {
      if (event.action === 'ENTER') {
        const timestamp = new Date(event.ts).getTime();
        enterWindow.current.add({ timestamp });
      }
    });
  }, [segmentEvents, enabled]);

  // Update KPIs at ~1s interval
  useEffect(() => {
    if (!enabled) {
      // Reset when disabled
      profileWindow.current.clear();
      eventWindow.current.clear();
      enterWindow.current.clear();
      setSparklineData([]);
      setKpis({
        activeProfiles: 0,
        eventsPerMinute: 0,
        segmentEntersPerMinute: 0,
        segmentEntersHistory: [],
      });
      return;
    }

    const interval = setInterval(() => {
      const activeProfiles = profileWindow.current.uniqueCount();
      const eventsPerMinute = eventWindow.current.getRatePerMinute();
      const segmentEntersPerMinute = enterWindow.current.getRatePerMinute();

      // Update sparkline data (keep last 120 points = 2 minutes)
      setSparklineData((prev) => {
        const newData = [...prev, segmentEntersPerMinute];
        return newData.slice(-120); // Keep last 120 points
      });

      setKpis({
        activeProfiles,
        eventsPerMinute,
        segmentEntersPerMinute,
        segmentEntersHistory: sparklineData,
      });
    }, 1000); // Update every 1s

    return () => clearInterval(interval);
  }, [enabled, sparklineData]);

  return kpis;
}
