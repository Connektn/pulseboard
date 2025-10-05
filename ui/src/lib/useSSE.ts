import { useEffect, useState, useRef, useCallback } from 'react';

// Alert type definition matching backend
export interface Alert {
  id: string;
  ts: string; // ISO string from backend
  rule: string;
  entityId: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
  evidence: Record<string, unknown>;
}

// SSE message types
export interface SSEMessage {
  type: 'alert' | 'heartbeat' | 'connection';
  data?: Alert;
  timestamp?: string;
  message?: string;
}

// Hook state interface
export interface SSEState {
  alerts: Alert[];
  connected: boolean;
  error: string | null;
  lastMessage: SSEMessage | null;
}

// Hook options
export interface UseSSEOptions {
  maxAlerts?: number; // Maximum number of alerts to keep in memory
  reconnectDelay?: number; // Delay before reconnecting in ms
  baseUrl?: string; // Backend base URL
  enabled?: boolean; // Whether to establish the connection
}

const BASE_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

export function useSSE(options: UseSSEOptions = {}): SSEState {
  const {
    maxAlerts = 100,
    reconnectDelay = 3000,
    baseUrl = BASE_URL,
    enabled = true,
  } = options;

  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastMessage, setLastMessage] = useState<SSEMessage | null>(null);

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

    const sseUrl = `${baseUrl}/sse/alerts`;
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
        const message: SSEMessage = JSON.parse(event.data);
        setLastMessage(message);

        // Handle different message types
        switch (message.type) {
          case 'alert':
            if (message.data) {
              setAlerts((prevAlerts) => {
                const newAlerts = [message.data!, ...prevAlerts];
                // Keep only the latest maxAlerts
                return newAlerts.slice(0, maxAlerts);
              });
            }
            break;
          case 'heartbeat':
            // Heartbeat received - connection is alive
            break;
          case 'connection':
            // Connection message - could log or handle specially
            break;
          default:
            console.warn('Unknown SSE message type:', message.type);
        }
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
  }, [baseUrl, maxAlerts, reconnectDelay, enabled]);

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
  }, [connect, enabled]);

  return {
    alerts,
    connected,
    error,
    lastMessage,
  };
}

// Utility hook for getting just the latest alerts
export function useLatestAlerts(count: number = 10): Alert[] {
  const { alerts } = useSSE();
  return alerts.slice(0, count);
}

// Utility hook for connection status only
export function useSSEConnection(): { connected: boolean; error: string | null } {
  const { connected, error } = useSSE();
  return { connected, error };
}