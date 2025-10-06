import { useEffect, useState } from 'react';
import { useEventSource } from './useEventSource';

// ProfileIdentifiers matching backend model
export interface ProfileIdentifiers {
  userIds: string[];
  emails: string[];
  anonymousIds: string[];
}

// ProfileSummary matching backend model
export interface ProfileSummary {
  profileId: string;
  plan: string | null;
  country: string | null;
  lastSeen: string; // ISO string
  identifiers: ProfileIdentifiers;
  featureUsedCount: number;
}

// Hook state
export interface CdpProfilesState {
  profiles: ProfileSummary[];
  connected: boolean;
  error: string | null;
}

/**
 * Hook for consuming the CDP profiles SSE stream.
 *
 * Subscribes to /sse/cdp/profiles and maintains a list of the top 20 profiles
 * sorted by lastSeen (descending).
 *
 * @param enabled - Whether to establish the SSE connection (default: true)
 */
export function useCdpProfiles(enabled: boolean = true): CdpProfilesState {
  const { lastMessage, connected, error } = useEventSource<ProfileSummary[]>('/sse/cdp/profiles', { enabled });
  const [profiles, setProfiles] = useState<ProfileSummary[]>([]);

  useEffect(() => {
    if (!lastMessage) return;

    // Handle different message types
    switch (lastMessage.type) {
      case 'profile_summaries':
        if (lastMessage.data) {
          // Backend already sends top 20 sorted by lastSeen desc
          setProfiles(lastMessage.data);
        }
        break;
      case 'connection':
        // Connection established
        break;
      case 'error':
        console.error('CDP profiles stream error:', lastMessage.message);
        break;
      default:
        // Ignore unknown message types
        break;
    }
  }, [lastMessage]);

  return {
    profiles,
    connected,
    error,
  };
}
