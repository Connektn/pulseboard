/**
 * Sliding window counter for computing time-based aggregations.
 *
 * Tracks timestamped events and computes counts/rates within a time window.
 */

export interface TimestampedEvent {
  timestamp: number; // Unix timestamp in milliseconds
}

/**
 * Sliding window that maintains events within a time window.
 * Automatically evicts old events outside the window.
 */
export class SlidingWindow<T extends TimestampedEvent> {
  private events: T[] = [];
  private windowMs: number;

  /**
   * @param windowMs - Window size in milliseconds
   */
  constructor(windowMs: number) {
    this.windowMs = windowMs;
  }

  /**
   * Add an event to the window.
   * Automatically evicts events older than the window.
   */
  add(event: T): void {
    this.events.push(event);
    this.evictOldEvents();
  }

  /**
   * Get count of events in the current window.
   */
  count(): number {
    this.evictOldEvents();
    return this.events.length;
  }

  /**
   * Get all events in the current window.
   */
  getEvents(): T[] {
    this.evictOldEvents();
    return [...this.events];
  }

  /**
   * Get rate per minute for events in the window.
   */
  getRatePerMinute(): number {
    this.evictOldEvents();
    if (this.events.length === 0) return 0;

    const windowMinutes = this.windowMs / 60000;
    return this.events.length / windowMinutes;
  }

  /**
   * Clear all events from the window.
   */
  clear(): void {
    this.events = [];
  }

  /**
   * Evict events older than the window.
   */
  private evictOldEvents(): void {
    const now = Date.now();
    const cutoff = now - this.windowMs;

    // Remove events older than cutoff
    this.events = this.events.filter(event => event.timestamp >= cutoff);
  }
}

/**
 * Count unique values within a sliding window.
 * Useful for counting unique profiles, etc.
 */
export class UniqueCountWindow<T extends TimestampedEvent & { key: string }> {
  private window: SlidingWindow<T>;

  constructor(windowMs: number) {
    this.window = new SlidingWindow(windowMs);
  }

  add(event: T): void {
    this.window.add(event);
  }

  /**
   * Get count of unique keys in the window.
   */
  uniqueCount(): number {
    const events = this.window.getEvents();
    const uniqueKeys = new Set(events.map(e => e.key));
    return uniqueKeys.size;
  }

  clear(): void {
    this.window.clear();
  }
}
