// Core event types matching backend schema

/**
 * Profile types for event categorization
 */
export type Profile = 'SASE' | 'IGAMING' | 'CDP';

/**
 * Alert severity levels
 */
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH';

/**
 * Base payload interface - marker for all event payloads
 */
export interface Payload {
  // Marker interface
}

/**
 * Entity payload for SASE and IGAMING events
 */
export interface EntityPayload extends Payload {
  entityId: string;
  profile: Profile;
  type: string;
  value?: number;
  tags?: Record<string, string>;
}

/**
 * Base event structure with generic payload
 */
export interface BaseEvent<P extends Payload = Payload> {
  eventId: string;
  ts: string; // ISO timestamp
  payload: P;
}

/**
 * Entity event (SASE/IGAMING)
 */
export interface EntityEvent extends BaseEvent<EntityPayload> {
  eventId: string;
  ts: string;
  payload: EntityPayload;
}

/**
 * Alert generated from events
 */
export interface Alert {
  id: string;
  ts: string; // ISO timestamp
  rule: string;
  entityId: string;
  severity: Severity;
  evidence: Record<string, unknown>;
}

/**
 * CDP Event types
 */
export type CdpEventType = 'IDENTIFY' | 'TRACK' | 'ALIAS' | 'GROUP' | 'PAGE' | 'SCREEN';

/**
 * CDP Profile identifiers
 */
export interface ProfileIdentifiers {
  userIds: string[];
  emails: string[];
  anonymousIds: string[];
}

/**
 * CDP Profile
 */
export interface CdpProfile {
  profileId: string;
  identifiers: ProfileIdentifiers;
  traits: Record<string, unknown>;
  counters: Record<string, number>;
  segments: string[];
  lastSeen: string; // ISO timestamp
}

/**
 * CDP Segment action (ENTER/EXIT)
 */
export type SegmentAction = 'ENTER' | 'EXIT';

/**
 * CDP Segment event
 */
export interface SegmentEvent {
  ts: string; // ISO timestamp
  profileId: string;
  segment: string;
  action: SegmentAction;
}

/**
 * SSE Message wrapper for different event types
 */
export interface SSEMessage<T = unknown> {
  type: string;
  data?: T;
  timestamp?: string;
  message?: string;
}

/**
 * Stats overview
 */
export interface StatsOverview {
  eventsPerMin: number;
  alertsPerMin: number;
  uptimeSec: number;
}

/**
 * Simulator configuration
 */
export interface SimulatorConfig {
  profile: Profile;
  rps: number;
  latenessSec: number;
  running: boolean;
}

/**
 * Simulator response
 */
export interface SimulatorResponse {
  status: 'started' | 'stopped' | 'running' | 'not_running' | 'already_running' | 'already_stopped' | 'error';
  message: string;
  profile: Profile;
  rps?: number;
  latenessSec?: number;
}
