import { describe, it, expect } from 'vitest';
import { SlidingWindow, UniqueCountWindow } from './slidingWindow';

describe('SlidingWindow', () => {

  it('should add events to the window', () => {
    const window = new SlidingWindow<{ timestamp: number }>(5000); // 5 second window

    const now = Date.now();
    window.add({ timestamp: now });
    window.add({ timestamp: now + 1000 });

    expect(window.count()).toBe(2);
  });

  it('should evict events older than the window', () => {
    const window = new SlidingWindow<{ timestamp: number }>(5000); // 5 second window

    const now = Date.now();

    window.add({ timestamp: now - 10000 }); // 10s ago - should be evicted
    window.add({ timestamp: now - 6000 }); // 6s ago - should be evicted
    window.add({ timestamp: now - 2000 }); // 2s ago - should be kept

    expect(window.count()).toBe(1);
  });

  it('should keep recent events within the window', () => {
    const window = new SlidingWindow<{ timestamp: number }>(5000); // 5 second window

    const now = Date.now();

    window.add({ timestamp: now - 1000 }); // 1s ago - should be kept
    window.add({ timestamp: now - 2000 }); // 2s ago - should be kept
    window.add({ timestamp: now - 3000 }); // 3s ago - should be kept

    expect(window.count()).toBe(3);
  });

  it('should calculate rate per minute correctly', () => {
    const window = new SlidingWindow<{ timestamp: number }>(60000); // 1 minute window

    const now = Date.now();

    // Add 10 events in the window
    for (let i = 0; i < 10; i++) {
      window.add({ timestamp: now - i * 1000 });
    }

    // Rate should be 10 events / 1 minute = 10/min
    expect(window.getRatePerMinute()).toBe(10);
  });

  it('should return zero rate for empty window', () => {
    const window = new SlidingWindow<{ timestamp: number }>(60000);

    expect(window.getRatePerMinute()).toBe(0);
  });

  it('should return all events in the window', () => {
    const window = new SlidingWindow<{ timestamp: number; data: string }>(5000);

    const now = Date.now();

    window.add({ timestamp: now - 1000, data: 'event1' });
    window.add({ timestamp: now - 2000, data: 'event2' });

    const events = window.getEvents();
    expect(events).toHaveLength(2);
    expect(events[0].data).toBe('event1');
    expect(events[1].data).toBe('event2');
  });

  it('should clear all events', () => {
    const window = new SlidingWindow<{ timestamp: number }>(5000);

    const now = Date.now();
    window.add({ timestamp: now });
    window.add({ timestamp: now + 1000 });

    expect(window.count()).toBe(2);

    window.clear();

    expect(window.count()).toBe(0);
  });
});

describe('UniqueCountWindow', () => {

  it('should count unique keys', () => {
    const window = new UniqueCountWindow<{ timestamp: number; key: string }>(5000);

    const now = Date.now();

    window.add({ timestamp: now - 1000, key: 'user1' });
    window.add({ timestamp: now - 2000, key: 'user2' });
    window.add({ timestamp: now - 3000, key: 'user1' }); // Duplicate key

    expect(window.uniqueCount()).toBe(2); // Only 2 unique keys
  });

  it('should evict old events when counting unique keys', () => {
    const window = new UniqueCountWindow<{ timestamp: number; key: string }>(5000);

    const now = Date.now();

    window.add({ timestamp: now - 10000, key: 'user1' }); // 10s ago - evicted
    window.add({ timestamp: now - 6000, key: 'user2' }); // 6s ago - evicted
    window.add({ timestamp: now - 2000, key: 'user3' }); // 2s ago - kept

    expect(window.uniqueCount()).toBe(1);
  });

  it('should handle empty window', () => {
    const window = new UniqueCountWindow<{ timestamp: number; key: string }>(5000);

    expect(window.uniqueCount()).toBe(0);
  });

  it('should clear all events', () => {
    const window = new UniqueCountWindow<{ timestamp: number; key: string }>(5000);

    const now = Date.now();
    window.add({ timestamp: now, key: 'user1' });
    window.add({ timestamp: now + 1000, key: 'user2' });

    expect(window.uniqueCount()).toBe(2);

    window.clear();

    expect(window.uniqueCount()).toBe(0);
  });

  it('should count only recent unique keys', () => {
    const window = new UniqueCountWindow<{ timestamp: number; key: string }>(5000);

    const now = Date.now();

    window.add({ timestamp: now - 10000, key: 'user1' }); // Old - evicted
    window.add({ timestamp: now - 2000, key: 'user2' }); // Recent - kept
    window.add({ timestamp: now - 1000, key: 'user3' }); // Recent - kept

    expect(window.uniqueCount()).toBe(2); // user2, user3
  });
});
