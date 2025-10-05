import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act, cleanup } from '@testing-library/react';
import { useSSE, useLatestAlerts, useSSEConnection, type Alert, type SSEMessage } from './useSSE';

// Access to mock instances through global setup
declare global {
  interface Window {
    mockEventSourceInstances: any[];
  }
}

// Get the MockEventSource constructor
const MockEventSource = global.EventSource as any;

describe('useSSE', () => {
  afterEach(() => {
    cleanup();
  });

  describe('initialization', () => {
    it('should initialize with empty state', () => {
      const { result } = renderHook(() => useSSE());

      expect(result.current.alerts).toEqual([]);
      expect(result.current.connected).toBe(false);
      expect(result.current.error).toBe(null);
      expect(result.current.lastMessage).toBe(null);
    });

    it('should use custom options', () => {
      const options = {
        maxAlerts: 50,
        reconnectDelay: 1000,
        baseUrl: 'http://custom-url',
      };

      const { result } = renderHook(() => useSSE(options));

      expect(result.current.alerts).toEqual([]);
      expect(result.current.connected).toBe(false);
    });
  });

  describe('connection handling', () => {
    it('should establish connection and set connected state', async () => {
      const { result } = renderHook(() => useSSE({ baseUrl: 'http://test' }));

      // Wait for connection to open (simulated in MockEventSource)
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 20));
      });

      expect(result.current.connected).toBe(true);
      expect(result.current.error).toBe(null);
    });

    it('should handle connection errors', async () => {
      const { result } = renderHook(() => useSSE({ baseUrl: 'http://test' }));

      // Simulate connection error
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 20));
        // Find the EventSource instance and trigger error
        const eventSource = MockEventSource.mock.results[MockEventSource.mock.results.length - 1]?.value;
        if (eventSource && eventSource.onerror) {
          eventSource.readyState = MockEventSource.CLOSED;
          eventSource.onerror(new Event('error'));
        }
      });

      expect(result.current.connected).toBe(false);
      expect(result.current.error).toBe('Connection closed by server');
    });
  });

  describe('message handling', () => {
    it('should handle alert messages', async () => {
      const { result } = renderHook(() => useSSE({ baseUrl: 'http://test' }));

      // Wait for connection
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 20));
      });

      const mockAlert: Alert = {
        id: 'alert-123',
        ts: '2023-12-01T12:00:00Z',
        rule: 'R1_VELOCITY_SPIKE',
        entityId: 'user123',
        severity: 'HIGH',
        evidence: { rate: 150, threshold: 100 },
      };

      const mockMessage: SSEMessage = {
        type: 'alert',
        data: mockAlert,
        timestamp: '2023-12-01T12:00:00Z',
      };

      // Simulate receiving alert message
      await act(async () => {
        const eventSource = MockEventSource.mock.results[MockEventSource.mock.results.length - 1]?.value;
        if (eventSource && eventSource.onmessage) {
          eventSource.onmessage(
            new MessageEvent('message', {
              data: JSON.stringify(mockMessage),
            })
          );
        }
      });

      expect(result.current.alerts).toHaveLength(1);
      expect(result.current.alerts[0]).toEqual(mockAlert);
      expect(result.current.lastMessage).toEqual(mockMessage);
    });

    it('should handle heartbeat messages', async () => {
      const { result } = renderHook(() => useSSE({ baseUrl: 'http://test' }));

      // Wait for connection
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 20));
      });

      const heartbeatMessage: SSEMessage = {
        type: 'heartbeat',
        timestamp: '2023-12-01T12:00:00Z',
      };

      // Simulate receiving heartbeat
      await act(async () => {
        const eventSource = MockEventSource.mock.results[MockEventSource.mock.results.length - 1]?.value;
        if (eventSource && eventSource.onmessage) {
          eventSource.onmessage(
            new MessageEvent('message', {
              data: JSON.stringify(heartbeatMessage),
            })
          );
        }
      });

      expect(result.current.alerts).toHaveLength(0); // Heartbeat shouldn't add alerts
      expect(result.current.lastMessage).toEqual(heartbeatMessage);
    });

    it('should handle connection messages', async () => {
      const { result } = renderHook(() => useSSE({ baseUrl: 'http://test' }));

      // Wait for connection
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 20));
      });

      const connectionMessage: SSEMessage = {
        type: 'connection',
        message: 'Connected to alerts stream',
        timestamp: '2023-12-01T12:00:00Z',
      };

      // Simulate receiving connection message
      await act(async () => {
        const eventSource = MockEventSource.mock.results[MockEventSource.mock.results.length - 1]?.value;
        if (eventSource && eventSource.onmessage) {
          eventSource.onmessage(
            new MessageEvent('message', {
              data: JSON.stringify(connectionMessage),
            })
          );
        }
      });

      expect(result.current.alerts).toHaveLength(0);
      expect(result.current.lastMessage).toEqual(connectionMessage);
    });

    it('should limit alerts to maxAlerts', async () => {
      const { result } = renderHook(() => useSSE({ maxAlerts: 2, baseUrl: 'http://test' }));

      // Wait for connection
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 20));
      });

      // Add 3 alerts
      for (let i = 1; i <= 3; i++) {
        const mockAlert: Alert = {
          id: `alert-${i}`,
          ts: `2023-12-01T12:00:0${i}Z`,
          rule: 'R1_VELOCITY_SPIKE',
          entityId: `user${i}`,
          severity: 'HIGH',
          evidence: {},
        };

        const mockMessage: SSEMessage = {
          type: 'alert',
          data: mockAlert,
        };

        await act(async () => {
          const eventSource = MockEventSource.mock.results[MockEventSource.mock.results.length - 1]?.value;
          if (eventSource && eventSource.onmessage) {
            eventSource.onmessage(
              new MessageEvent('message', {
                data: JSON.stringify(mockMessage),
              })
            );
          }
        });
      }

      // Should only keep the latest 2 alerts
      expect(result.current.alerts).toHaveLength(2);
      expect(result.current.alerts[0].id).toBe('alert-3');
      expect(result.current.alerts[1].id).toBe('alert-2');
    });

    it('should handle malformed JSON messages', async () => {
      const { result } = renderHook(() => useSSE({ baseUrl: 'http://test' }));

      // Wait for connection
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 20));
      });

      // Mock console.error to avoid test noise
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      // Simulate receiving malformed JSON
      await act(async () => {
        const eventSource = MockEventSource.mock.results[MockEventSource.mock.results.length - 1]?.value;
        if (eventSource && eventSource.onmessage) {
          eventSource.onmessage(
            new MessageEvent('message', {
              data: 'invalid json',
            })
          );
        }
      });

      expect(result.current.error).toBe('Failed to parse server message');
      expect(consoleSpy).toHaveBeenCalledWith('Failed to parse SSE message:', expect.any(Error));

      consoleSpy.mockRestore();
    });
  });

  describe('cleanup', () => {
    it('should close connection on unmount', async () => {
      const { result, unmount } = renderHook(() => useSSE({ baseUrl: 'http://test' }));

      // Wait for connection
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 20));
      });

      expect(result.current.connected).toBe(true);

      // Unmount hook
      unmount();

      // EventSource should be closed
      const eventSource = MockEventSource.mock.results[MockEventSource.mock.results.length - 1]?.value;
      expect(eventSource?.close).toHaveBeenCalled();
    });
  });
});

describe('useLatestAlerts', () => {
  afterEach(() => {
    cleanup();
  });

  it('should return latest alerts', async () => {
    // This hook uses useSSE internally, so we need to mock the entire flow
    const { result } = renderHook(() => useLatestAlerts(2));

    expect(result.current).toEqual([]);

    // Note: This is a simplified test. In a real scenario, you'd need to
    // set up the SSE connection and add alerts to test the full flow.
  });
});

describe('useSSEConnection', () => {
  afterEach(() => {
    cleanup();
  });

  it('should return connection status', async () => {
    const { result } = renderHook(() => useSSEConnection());

    expect(result.current.connected).toBe(false);
    expect(result.current.error).toBe(null);

    // Wait for connection (simulated)
    await act(async () => {
      await new Promise(resolve => setTimeout(resolve, 20));
    });

    expect(result.current.connected).toBe(true);
  });
});