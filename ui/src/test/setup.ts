import '@testing-library/jest-dom';

// Mock EventSource for SSE tests
const mockInstances: any[] = [];

const MockEventSource = vi.fn().mockImplementation((url: string) => {
  const instance = {
    url,
    onopen: null,
    onmessage: null,
    onerror: null,
    readyState: 0,
    close: vi.fn(() => {
      instance.readyState = 2; // CLOSED
    }),

    // Constants
    CONNECTING: 0,
    OPEN: 1,
    CLOSED: 2,
  };

  // Add to instances array for test access
  mockInstances.push(instance);

  // Simulate connection opening after a brief delay
  setTimeout(() => {
    instance.readyState = 1; // OPEN
    if (instance.onopen) {
      instance.onopen(new Event('open'));
    }
  }, 10);

  return instance;
});

// Add constants to constructor
MockEventSource.CONNECTING = 0;
MockEventSource.OPEN = 1;
MockEventSource.CLOSED = 2;

global.EventSource = MockEventSource as any;

// Mock fetch for API tests
global.fetch = vi.fn();

// Setup cleanup
afterEach(() => {
  vi.clearAllMocks();
  mockInstances.length = 0; // Clear instances array
});