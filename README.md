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

## Architecture

Pulseboard MVP implements:
- Real-time event simulator → sliding windows → rule engine → SSE → minimal React UI with alerts and sparkline

## Contributing

See `docs/TICKETS.md` for current development tasks and roadmap.
