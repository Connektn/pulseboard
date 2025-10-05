import { useEffect, useState, useRef, useCallback } from 'react';

// Generic SSE message
export interface SSEMessage<T = unknown> {
  type: string;
  data?: T;
  timestamp?: string;
  message?: string;
}

// Hook state interface
export interface EventSourceState<T> {
  lastMessage: SSEMessage<T> | null;
  connected: boolean;
  error: string | null;
}

// Hook options
export interface UseEventSourceOptions {
  reconnectDelay?: number; // Delay before reconnecting in ms
  baseUrl?: string; // Backend base URL
  enabled?: boolean; // Whether to establish the connection
}

const BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

/**
 * Generic hook for consuming Server-Sent Events (SSE) streams.
 *
 * @param endpoint - The SSE endpoint path (e.g., '/sse/cdp/profiles')
 * @param options - Configuration options
 * @returns State object with lastMessage, connected, and error
 */
export function useEventSource<T = unknown>(
  endpoint: string,
  options: UseEventSourceOptions = {}
): EventSourceState<T> {
  const {
    reconnectDelay = 3000,
    baseUrl = BASE_URL,
    enabled = true,
  } = options;

  const [lastMessage, setLastMessage] = useState<SSEMessage<T> | null>(null);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const eventSourceRef = useRef<EventSource | null>(null);
  const reconnectTimeoutRef = useRef<number | null>(null);
  const mountedRef = useRef(true);

  const connect = useCallback(() => {
    // Skip if disabled
    if (!enabled) {
      return;
    }

    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    const sseUrl = `${baseUrl}${endpoint}`;
    const eventSource = new EventSource(sseUrl);
    eventSourceRef.current = eventSource;

    eventSource.onopen = () => {
      if (mountedRef.current) {
        setConnected(true);
        setError(null);
      }
    };

    eventSource.onmessage = (event) => {
      if (!mountedRef.current) return;

      try {
        const message: SSEMessage<T> = JSON.parse(event.data);
        setLastMessage(message);
      } catch (err) {
        console.error('Failed to parse SSE message:', err);
        setError('Failed to parse server message');
      }
    };

    eventSource.onerror = () => {
      if (!mountedRef.current) return;

      setConnected(false);

      if (eventSource.readyState === EventSource.CLOSED) {
        setError('Connection closed by server');
      } else {
        setError('Connection error occurred');
      }

      // Attempt to reconnect after delay
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }

      reconnectTimeoutRef.current = setTimeout(() => {
        if (mountedRef.current) {
          connect();
        }
      }, reconnectDelay);
    };
  }, [baseUrl, endpoint, reconnectDelay, enabled]);

  // Connect on mount and handle cleanup
  useEffect(() => {
    // Skip if disabled
    if (!enabled) {
      // Close any existing connection if disabled
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      setConnected(false);
      return;
    }

    mountedRef.current = true;
    connect();

    return () => {
      mountedRef.current = false;

      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }

      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [connect]);

  return {
    lastMessage,
    connected,
    error,
  };
}
