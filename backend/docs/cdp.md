# Customer Data Platform (CDP) Module

## Overview

The CDP module provides a real-time customer data platform for tracking user events, building unified customer profiles, and managing customer segments. It implements industry-standard IDENTIFY, TRACK, and ALIAS event types with Last-Write-Wins (LWW) semantics for profile merging.

## Architecture

### Core Components

1. **CdpIngestController** - REST API for event ingestion
2. **CdpEventBus** - In-memory event bus for distributing events
3. **CdpEventProcessor** - Windowed event processor with watermarking
4. **CdpPipeline** - Orchestrates event processing flow
5. **ProfileStore** - In-memory profile storage with LWW trait merging
6. **IdentityGraph** - Manages identity resolution and canonical IDs
7. **SegmentEngine** - Evaluates and manages customer segments
8. **RollingCounter** - Time-windowed event counting

### Event Flow

```
HTTP POST /cdp/ingest
    ↓
CdpIngestController (validation)
    ↓
CdpEventBus (in-memory or Kafka)
    ↓
CdpEventProcessor (windowing + ordering)
    ↓
CdpPipeline (processing)
    ↓
├─ IdentityGraph (identity resolution)
├─ ProfileStore (profile updates)
├─ RollingCounter (event counting)
└─ SegmentEngine (segment evaluation)
```

## Event Types

### IDENTIFY

Creates or updates a customer profile with user traits.

**Example:**
```json
{
  "eventId": "evt-123",
  "ts": "2025-10-04T10:30:00Z",
  "payload": {
    "type": "IDENTIFY",
    "userId": "user-123",
    "email": "user@example.com",
    "traits": {
      "name": "John Doe",
      "plan": "pro",
      "country": "US"
    }
  }
}
```

### TRACK

Records a customer action or event.

**Example:**
```json
{
  "eventId": "evt-456",
  "ts": "2025-10-04T10:35:00Z",
  "payload": {
    "type": "TRACK",
    "userId": "user-123",
    "name": "Feature Used",
    "properties": {
      "feature": "dashboard",
      "duration": 120
    }
  }
}
```

### ALIAS

Links multiple identifiers to the same customer profile.

**Example:**
```json
{
  "eventId": "evt-789",
  "ts": "2025-10-04T10:40:00Z",
  "payload": {
    "type": "ALIAS",
    "anonymousId": "anon-456",
    "userId": "user-123"
  }
}
```

## Last-Write-Wins (LWW) Semantics

### How LWW Works

The CDP uses **timestamp-based Last-Write-Wins** for profile traits. Each trait tracks its last update timestamp, and only events with newer timestamps can update the trait value.

### Example: Preventing Stale Updates

**Scenario:**
```
t=10:00:00  Event A: IDENTIFY {userId: "u123", traits: {plan: "free", email: "old@example.com"}}
            → Gets stuck in a retry queue due to network failure

t=11:00:00  Event B: IDENTIFY {userId: "u123", traits: {plan: "pro", email: "new@example.com"}}
            → Arrives and processes successfully

t=14:00:00  Event A finally arrives (4 hours late!)
```

**Processing:**
```kotlin
// At t=11:00:00 - Process Event B
profile.traits = {
  plan: {value: "pro", lastUpdated: 11:00:00},
  email: {value: "new@example.com", lastUpdated: 11:00:00}
}

// At t=14:00:00 - Process Event A (4 hours late)
// Event A has ts=10:00:00, which is OLDER than 11:00:00
// LWW compares timestamps:
//   Event A: 10:00:00 < 11:00:00 (current)
//   → Event A is rejected ✓

profile.traits remains = {
  plan: {value: "pro", lastUpdated: 11:00:00},  // ✓ Not overwritten
  email: {value: "new@example.com", lastUpdated: 11:00:00}  // ✓ Not overwritten
}
```

**Key Point:** LWW protects against stale updates by comparing the **event's actual timestamp** (when it happened), not when it arrived at the server.

### What Gets LWW Protection?

✅ **Traits** - Each trait has individual timestamp tracking
✅ **lastSeen** - Only newer timestamps update it
⚠️ **Identifiers** - Use set union (grow-only) - all known identifiers are retained for matching

### Per-Trait Granularity

LWW operates at the **individual trait level**, not the entire profile:

```kotlin
// t=10:00 - Event sets {plan: "free", country: "US"}
profile.traits = {
  plan: {value: "free", lastUpdated: 10:00},
  country: {value: "US", lastUpdated: 10:00}
}

// t=11:00 - Event updates only plan to "pro"
profile.traits = {
  plan: {value: "pro", lastUpdated: 11:00},      // ✓ Updated
  country: {value: "US", lastUpdated: 10:00}     // ✓ Unchanged
}

// t=12:00 - Late event tries to set plan: "basic" at t=10:30
// plan's lastUpdated (11:00) > event ts (10:30)
// → plan stays "pro", but other traits could still be updated if they have older timestamps
```

## Event Windowing and Watermarking

### Why Windowing?

Even with LWW semantics, windowing provides important benefits:

1. **Reduces segment recalculations** - Avoids recomputing segments for every late event
2. **Ensures consistent segment evaluation** - Segments are computed over complete time windows
3. **Prevents flickering** - Avoids emitting conflicting ENTER/EXIT events

### How Windowing Helps with Late Events

**Scenario: User Signs Up and Immediately Uses Feature**

**Timeline of actual events:**
```
t=10:00:00  Event A: IDENTIFY {userId: "u123", traits: {plan: "free"}}
t=10:00:05  Event B: TRACK {userId: "u123", name: "Feature Used"}
t=10:00:10  Event C: TRACK {userId: "u123", name: "Feature Used"}
t=10:00:15  Event D: IDENTIFY {userId: "u123", traits: {plan: "pro"}}
```

**What arrives at the server (network delays):**
```
t=10:00:01  Event A arrives (1s delay)
t=10:00:20  Event D arrives (5s delay)
t=10:00:25  Event B arrives (20s late!) - mobile was offline
t=10:00:26  Event C arrives (16s late!)
```

#### Without Windowing (Process Immediately):

```kotlin
// At t=10:00:01 - Process Event A
profile.traits = {plan: "free"}

// At t=10:00:20 - Process Event D
profile.traits = {plan: "pro"}  // LWW: 10:00:15 > 10:00:00 ✓
// Segment engine evaluates: counter=0, no "power_user" segment

// At t=10:00:25 - Process Event B (LATE!)
// Problem: We already computed segments, now need to recompute
rollingCounter.increment("u123", "Feature Used", ts=10:00:05)
// Need to retroactively update segments!

// At t=10:00:26 - Process Event C (LATE!)
rollingCounter.increment("u123", "Feature Used", ts=10:00:10)
// Another retroactive segment update needed!
```

**Problem:** Each late event triggers segment recomputation, potentially emitting conflicting ENTER/EXIT events.

#### With Windowing (Current Implementation):

**Processing window = 5s, Current time = 10:00:30**

```kotlin
// Watermark = 10:00:30 - 5s = 10:00:25
// "Don't process events newer than 10:00:25 yet, wait for stragglers"

Buffer state at tick():
- Event A (ts=10:00:00): age=30s, watermark=10:00:25 → READY
- Event D (ts=10:00:15): age=15s, watermark=10:00:25 → READY
- Event B (ts=10:00:05): age=25s, watermark=10:00:25 → READY
- Event C (ts=10:00:10): age=20s, watermark=10:00:25 → READY

Processing order (sorted by timestamp):
1. Event A (10:00:00) → profile created
2. Event B (10:00:05) → counter: 1
3. Event C (10:00:10) → counter: 2
4. Event D (10:00:15) → traits updated, counter: 2

Segment evaluation happens ONCE with complete data:
✓ All events from [10:00:00-10:00:15] are present
✓ Counter shows 2 "Feature Used" events
✓ Segment engine makes correct decision on complete window
```

**Benefit:** Late events B and C don't cause problems because they're buffered, sorted, and processed together.

### Configuration

```yaml
event-processor:
  # Time window for buffering events (watermark lag)
  window-size: 5s

  # Grace period for extremely late events
  grace-period: 2m

  # Deduplication TTL
  dedup-ttl: 10m

  # Watermark ticker interval
  ticker-interval: 1s
```

**Processing Window (5s):** Events are buffered for 5 seconds to allow stragglers to arrive

**Grace Period (2m):** Events older than 2 minutes past the watermark are dropped

**Example:**
```
Current time: 10:00:30
Watermark: 10:00:25 (30s - 5s)
Grace period cutoff: 10:08:25 (watermark - 2m)

Event with ts=10:00:20 → Buffered (within watermark)
Event with ts=10:00:10 → Processed (beyond watermark)
Event with ts=10:06:00 → Dropped (beyond grace period)
```

## Identity Resolution

### Canonical IDs

The IdentityGraph manages identity resolution, creating canonical IDs that link multiple identifiers to a single customer profile.

**Example:**
```
1. IDENTIFY {anonymousId: "anon-123"}
   → Creates canonical ID: "canonical-1"

2. TRACK {anonymousId: "anon-123"}
   → Resolves to "canonical-1"

3. ALIAS {anonymousId: "anon-123", userId: "user-456"}
   → Links both identifiers to "canonical-1"

4. IDENTIFY {userId: "user-456", email: "user@example.com"}
   → Resolves to "canonical-1", adds email identifier
```

**Result:**
```
canonical-1 → {
  anonymousIds: ["anon:anon-123"],
  userIds: ["user:user-456"],
  emails: ["email:user@example.com"]
}
```

### Identifier Merging

Identifiers use **set union** (grow-only CRDT) rather than LWW:
- All known identifiers are retained
- Enables matching users across any past identifier
- Naturally conflict-free

## Customer Segments

### Built-in Segments

1. **power_user** - Users who trigger 5+ events within 24 hours
2. **pro_plan** - Users with `plan` trait set to "pro"
3. **reengage** - Users inactive for 10+ minutes who return

### Segment Events

When a user enters or exits a segment, the system emits segment events:

```json
{
  "eventId": "seg-evt-123",
  "ts": "2025-10-04T11:00:00Z",
  "payload": {
    "type": "TRACK",
    "userId": "user-123",
    "name": "power_user ENTER"
  }
}
```

### Configuration

```yaml
segment-engine:
  # Inactivity threshold for reengage segment
  reengage-inactivity-threshold: 10m

  # Power user configuration
  power-user-threshold: 5
  power-user-window: 24h
```

## Kafka Integration

The CDP supports both in-memory and Kafka-based event transport.

### Configuration

```yaml
transport:
  mode: kafka  # memory | kafka

spring:
  kafka:
    bootstrap-servers: localhost:19092
    topics:
      cdp-events: "cdp-events"
```

### How It Works

1. **CdpIngestController** receives HTTP POST
2. **CdpEventBus** publishes to Kafka topic `cdp-events`
3. **KafkaCdpEventTransport** consumes from Kafka
4. Events are partitioned by customer ID (userId or anonymousId)
5. Kafka guarantees ordering per partition
6. **CdpPipeline** processes events from the bus

### Serialization

Both `EntityEvent` and `CdpEvent` are supported via separate Kafka templates:
- `cdpEventKafkaTemplate` - For CDP events
- `entityEventKafkaTemplate` - For entity events

Each has its own JSON serializer/deserializer configured with proper type handling.

## API

### POST /cdp/ingest

Ingest a CDP event.

**Request:**
```bash
curl -X POST http://localhost:8080/cdp/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-123",
    "ts": "2025-10-04T10:30:00Z",
    "payload": {
      "type": "IDENTIFY",
      "userId": "user-123",
      "traits": {"plan": "pro"}
    }
  }'
```

**Response (202 Accepted):**
```json
{
  "status": "accepted",
  "eventId": "evt-123"
}
```

**Response (400 Bad Request):**
```json
{
  "status": "error",
  "message": "TRACK events require a name"
}
```

### Validation Rules

- `eventId` must not be empty
- At least one of `userId`, `email`, or `anonymousId` must be present
- TRACK events must have a `name` field
- All timestamps must be valid ISO-8601 format

## Testing

### Example Test: LWW Trait Merging

```kotlin
@Test
fun `LWW should prevent older trait from overriding newer trait`() = runBlocking {
    val now = fixedClock.instant().minusSeconds(10)
    val userId = "u123"

    // Event 1: Set plan=pro at T+0
    val event1 = CdpEvent(
        eventId = "evt-1",
        ts = now,
        payload = CdpEventPayload(
            type = CdpEventType.IDENTIFY,
            userId = userId,
            traits = mapOf("plan" to "pro"),
        ),
    )

    // Event 2: Try to set plan=basic at T-10 (older)
    val event2 = CdpEvent(
        eventId = "evt-2",
        ts = now.minusSeconds(10),  // ← Older timestamp
        payload = CdpEventPayload(
            type = CdpEventType.IDENTIFY,
            userId = userId,
            traits = mapOf("plan" to "basic"),
        ),
    )

    // Publish newer event first
    eventBus.publishEvent(event1)
    delay(200)
    pipeline.getEventProcessor().tick()

    // Then publish older event
    eventBus.publishEvent(event2)
    delay(200)
    pipeline.getEventProcessor().tick()

    // Verify plan is still "pro" (not overridden by older "basic")
    val profile = profileStore.get(canonicalId)
    assertEquals("pro", profile.traits["plan"])  // ✓ LWW works!
}
```

## Metrics

The CDP exposes metrics via Micrometer:

- `cdp.events.buffered` - Number of events in the processing buffer
- `cdp.events.processed` - Total events processed
- `cdp.events.late` - Events arriving beyond processing window
- `cdp.events.dropped` - Events dropped (beyond grace period)
- `cdp.events.dedup_hits` - Duplicate events detected

## Performance Considerations

### Memory Usage

- **ProfileStore**: O(number of profiles × average traits per profile)
- **IdentityGraph**: O(number of unique identifiers)
- **RollingCounter**: O(number of profiles × event types × buckets)

### Concurrency

- All stores use `ConcurrentHashMap` for thread-safe access
- Event processing is single-threaded per customer (via Kafka partitioning)
- Different customers are processed in parallel

### Scalability

- Horizontal scaling via Kafka partitions
- Each partition processes one subset of customers
- Stateless processing (state in external stores in production)

## Future Enhancements

- [ ] Persistent storage (PostgreSQL, Redis)
- [ ] Custom segment definitions via UI
- [ ] Real-time segment webhooks
- [ ] Profile enrichment from external APIs
- [ ] GDPR compliance (right to be forgotten)
- [ ] Profile export API
- [ ] Advanced identity resolution (fuzzy matching)
