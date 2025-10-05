# Pulseboard

Real-time anomaly detection for live event streams.

## Quick Start

Get the development environment running in one command:

```bash
./scripts/dev.sh
```

This will start:
- Backend server on `http://localhost:8080`
- Frontend UI on `http://localhost:5173`

## Prerequisites

- **Java 21+** (for backend)
- **Node.js 18+** (for frontend)
- **npm** (for frontend dependencies)
- **Docker & Docker Compose** (for Kafka/Redpanda broker)

## Manual Setup

If you prefer to run services individually:

### Backend (Kotlin + Spring WebFlux)

```bash
cd backend
./gradlew bootRun
```

The backend will be available at `http://localhost:8080/health`

### Frontend (React + TypeScript)

```bash
cd ui
npm install
npm run dev
```

The UI will be available at `http://localhost:5173`

## Development

### Project Structure

- `backend/` - Kotlin Spring Boot backend
- `ui/` - React TypeScript frontend
- `scripts/` - Development utilities
- `docs/` - Documentation and tickets

### Code Quality

- **Backend**: Uses ktlint for Kotlin formatting
- **Frontend**: Uses ESLint + Prettier for code formatting

Run linters:

```bash
# Backend
cd backend && ./gradlew ktlintCheck

# Frontend
cd ui && npm run lint
```

## Kafka Development

For development with Kafka/Redpanda message broker:

### Start Redpanda Broker

```bash
# Start Redpanda single-node cluster with console
docker compose up -d

# Check broker status
docker compose ps

# View logs
docker compose logs -f redpanda
```

### Redpanda Console

Once running, access the Redpanda Console at:
- **Console UI**: `http://localhost:8080`

The console provides:
- Topic management and inspection
- Message browsing and publishing
- Consumer group monitoring
- Schema registry integration
- Cluster health and metrics

### Stop Broker

```bash
# Stop all services
docker compose down

# Stop and remove volumes (clean slate)
docker compose down -v
```

### Useful Commands

```bash
# Create topics using rpk CLI in container
docker exec -it redpanda rpk topic create events --partitions 3
docker exec -it redpanda rpk topic create alerts --partitions 3

# List topics
docker exec -it redpanda rpk topic list

# Produce test messages
docker exec -it redpanda rpk topic produce events

# Consume messages
docker exec -it redpanda rpk topic consume events --print-headers
```

## Architecture

Pulseboard implements three main profiles:

1. **SASE** - Secure Access Service Edge anomaly detection
2. **IGAMING** - Online gaming fraud detection
3. **CDP** - Customer Data Platform with real-time segmentation

### CDP Module

The CDP (Customer Data Platform) module demonstrates advanced stream processing concepts including:
- Event-time processing with late event handling
- Last-Write-Wins conflict resolution
- Sliding window aggregations
- Real-time profile unification
- Dynamic customer segmentation

**[→ Full CDP Documentation](docs/cdp/README.md)**

## Documentation

- **[TICKETS.md](docs/TICKETS.md)** - Development roadmap and task breakdown
- **[CDP Module](docs/cdp/README.md)** - Customer Data Platform architecture and concepts

## Contributing

See `docs/TICKETS.md` for current development tasks and roadmap.
