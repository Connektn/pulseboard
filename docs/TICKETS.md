# EPIC A — Project Bootstrap

## A1. Initialize Monorepo & Tooling

Goal: Create repo with backend/ (Kotlin + Spring Boot) and ui/ (Vite + React + TS).

Deliverables:
- Root README.md with quickstart.
- `.gitignore`, `.editorconfig`, MIT `LICENSE`.
- Root `scripts/` with `dev.sh` that runs BE+FE together.
- Prettier + ESLint for UI; ktlint for BE.

DoD:
- `./scripts/dev.sh` starts backend on `:8080` and UI on `:5173`; UI loads a placeholder page.
- Prettier/ESLint and ktlint run without errors.

## A2. Backend Scaffold (Kotlin + Spring WebFlux)

Goal: Spring Boot 3 app, Java 21, Kotlin coroutines, WebFlux.

Deliverables:
- `backend/build.gradle.kts` with dependencies: webflux, jackson-kotlin, kotlinx-coroutines, caffeine, micrometer, junit5, mockk.
- `Main.kt`, `application.yml` (CORS allow `http://localhost:5173`).
- Health endpoint `GET /health` → `{ "status": "UP" }`.

DoD: 
- `./gradlew bootRun` serves `/health` returning JSON.

## A3. UI Scaffold (Vite + React + TS)

Goal: Minimal React app with Tailwind or plain CSS.

Deliverables:
- `ui/` via Vite template `react-ts`.
- Basic layout: header, content area, footer.
- Env config to call BE at `http://localhost:8080`.

DoD:
- `npm run dev` shows a page “Pulseboard UI ready”.

# EPIC B — Domain & In-Memory Event Bus

## B1. Define Core Models

Goal: Data classes for Event, Alert, Profile.

Deliverables (backend):
- `core/Event.kt`, 
- `core/Alert.kt`, 
- `core/Profile.kt`.
- JSON mapping with Jackson Kotlin.

DoD: 
- Unit test that serializes/deserializes each model.

Suggested model:

```kotlin
data class Event(
  val ts: Instant,
  val profile: Profile,
  val type: String,
  val entityId: String,
  val value: Long? = null,
  val tags: Map<String, String> = emptyMap()
)

enum class Profile { SASE, IGAMING }

data class Alert(
  val id: String,
  val ts: Instant,
  val rule: String,
  val entityId: String,
  val severity: Severity,
  val evidence: Map<String, Any?>
)

enum class Severity { LOW, MEDIUM, HIGH }

```

## B2. In-Memory Event Bus (Coroutine Flows)

Goal: Shared flows for events and alerts.

Deliverables:
- `ingest/EventBus.kt` with events: `MutableSharedFlow<Event>` and alerts: `MutableSharedFlow<Alert>`.
- Backpressure policy (replay=0, buffer with DROP_OLDEST).

DoD: 
- Unit test: publish event → subscriber receives it.

# EPIC C — Simulator (Profiles)

## C1. Event Simulator (SASE & iGaming)

Goal: Coroutine simulator producing realistic events with jitter.

Deliverables:
- `ingest/Simulator.kt` with two generators:
  - SASE: CONN_OPEN, CONN_BYTES, LOGIN (+ occasional failed logins).
  - IGAMING: BET_PLACED, CASHIN, LOGIN.
- Config: rate per second, burst factor, random seeds.
- REST controls: GET/POST /profile (switch mode), POST /sim/start, POST /sim/stop.

DoD: 
- Starting simulator pushes events to bus; stopping pauses cleanly.

# EPIC D — Stateful Windows & Metrics

## D1. Sliding Window Store

Goal: Per-entity time windows (60–300s) + helpers.

Deliverables:
- `core/WindowStore.kt` with:
  - Append (ts,value), pruning by horizon.
  - Metrics: `ratePerMin(key)`, `sumIn(key, dur)`, `countIn(key, dur)`.
  - EWMA per (type, entity) with alpha config.
- Use Caffeine for small caches: `Cache<Key, RingBuffer>`.

DoD:
- Unit tests for pruning, rate, sum, ewma accuracy.


## D2. Percentile Approximation

Goal: P95 fallback metric.

Deliverables:
- Simple t-digest or reservoir sampling per metric; if time-boxed, store last N values and compute approximate P95.

DoD:
- Unit test verifies monotonic behavior and non-NaN outputs.

# EPIC E — Rule Engine

## E1. Implement R1–R4

Goal: Pure functions that evaluate events using WindowStore.

Rules:
- R1 Velocity Spike: `rate_now > 3×avg_5m && rate_now >= 20/min`.
- R2 Value Spike: `value_now > 4×EWMA && count_60s >= 5`.
- R3 Geo/Device Mismatch (2m): same entity, conflicting `geo` or `device`.
- R4 Exfil (SASE): `sum_30s > P95(last 1h)` (fallback constant).

Deliverables:
- `core/Rules.kt`: `suspend fun evaluateAll(event): List<Alert>`.
- Evidence maps include numbers and window details.

DoD:
- Unit tests with deterministic sequences triggering each rule and negative cases.

## E2. Processor Pipeline

Goal: Wire event consumption → state update → rules → alert publish.

Deliverables:
- `ingest/Processor.kt` coroutine launched on startup.
- Order: update window → evaluate rules → emit alerts.

DoD:
- When simulator runs, alerts begin streaming; stop when simulator stops.

# EPIC F — APIs & SSE

## F1. SSE Alerts Endpoint

Goal: Stream alerts to UI in real-time.

Deliverables:
- `api/AlertController.kt` → `GET /sse/alerts` (content-type `text/event-stream`).
- Heartbeat every 10s to keep connection open.

DoD:
- Hitting endpoint with curl prints JSON alerts as SSE.

## F2. Control & Status Endpoints

Goal: Operate the system during demo.

Deliverables:
- `GET /health` (already done in A2).
- `GET/POST /profile` → returns/sets `Profile`.
- `POST /sim/start`, `POST /sim/stop`.
- `GET /stats/overview` → `{ eventsPerMin, alertsPerMin, uptimeSec }`.

DoD:
- All endpoints return JSON; input validated.

# EPIC G — Frontend UI

## G1. API Client & SSE Hook

Goal: Minimal client for REST & SSE.

Deliverables:
- `src/lib/api.ts` for REST calls.
- `src/lib/useSSE.ts` custom hook for `/sse/alerts`.

DoD: 
- Hook reconnects on drop; exposes latest alerts as stream.

# G2. UI: Shell & Controls

Goal: Operator-friendly layout.

Deliverables:
- Header with app name Pulseboard, profile toggle (SASE/IGAMING), Start/Stop Simulation buttons, stats badges.
- Persist last selected profile in localStorage.

DoD: 
- Toggling profile calls backend and reflects current state.

## G3. UI: Live Alerts Table

Goal: Realtime table with last 100 alerts.

Deliverables:
- Columns: Time, Rule, Entity, Severity (colored), Evidence (expandable JSON).
- Sticky header, auto-scroll unless user hovers.

DoD: 
- Coming alerts push older down; max 100 kept in memory.

## G4. UI: Sparkline & Rate Panel

Goal: Visual pop for interviews.

Deliverables:
- `Chart.js` sparkline of alerts/min for last 2 minutes.
- KPI tiles: Events/min, Alerts/min, Uptime.

DoD: 
- KPIs update at 1s cadence; sparkline shifts in real-time.

# EPIC H — Kafka/Redpanda Integration

## H1. Docker Compose for Redpanda

Goal: One-liner local broker.

Deliverables:
- `docker-compose.yml` with Redpanda single node + console.
- `README` section with commands.

DoD: 
- `docker compose up -d` starts broker; console reachable.

## H2. Kafka Producer/Consumer Toggle

Goal: Switch between in-memory and Kafka transport.

Deliverables:
- `application.yml`: `transport.mode = memory|kafka`.
- Producer sends simulator events to topic events.
- Consumer reads events, passes into Processor.

DoD: 
- Both modes run; default = memory to simplify demo.

# EPIC I — Tests & CI

## I1. Unit Tests (Rules, Windows, API)

Goal: Confidence for demo.

Deliverables:
- JUnit tests for each rule edge case.
- Tests for WindowStore metrics.
- WebFlux slice test for /sse/alerts (emits a sample alert).

DoD: 
- `./gradlew test` green; coverage summary in console.

## I2. UI Tests (Smoke)

Goal: Basic UI integrity.

Deliverables:
- Vitest tests for utility functions & SSE hook.
- Playwright smoke: page loads, buttons call backend.

DoD: 
- `npm test` green.

# EPIC J — Docs & Packaging

## J1. README with 90-Second Demo

Goal: Make it trivially demo-able.

Deliverables:
- GIF of UI (or short Loom link).
- “Run locally” steps:
  - `./gradlew bootRun`
  - `npm i && npm run dev`
- “What you’re seeing” section mapping rules → alerts.
- Architecture diagram (Mermaid).

DoD: 
- Follow steps on clean machine: working demo in < 3 minutes.

## J2. Scripts & Makefile

Goal: One command to run everything.

Deliverables: 
- `Makefile` targets: `run`, `test`, `lint`, `clean`.

DoD: 
- `make run` starts BE+FE; `Ctrl-C` stops both.

# Suggested label & dependency map

- Labels: `epic:bootstrap`, `epic:backend`, `epic:streaming`, `epic:rules`, `epic:api`, `epic:ui`, `epic:kafka`, `epic:tests`, `epic:docs`, `good first issue`.
- Order: A1 → A2 → A3 → B1 → B2 → C1 → D1 → E1 → E2 → F1 → F2 → G1 → G2 → G3 → G4 → I1 → I2 → J1 → J2 → (H1, H2 optional parallel after F1).
