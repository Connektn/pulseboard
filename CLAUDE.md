# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is Pulseboard, a real-time anomaly detection system for live event streams. The repository is currently in its initial stages with a basic directory structure established.

## Project Structure

The project follows a multi-component architecture:

- `backend/` - Backend services (currently empty)
- `ui/` - User interface components (currently empty)
- `scripts/` - Utility and build scripts (currently empty)

## Development Status

This is a new project initialized with:
- MIT License
- Basic README with project description
- Git repository setup
- Directory structure for backend, UI, and scripts components

The codebase is currently empty and ready for initial implementation. No build commands, test frameworks, or development tools have been set up yet.

## Project Context

Pulseboard is designed for real-time anomaly detection in live event streams, suggesting it will likely involve:
- Stream processing capabilities
- Real-time data analysis
- Anomaly detection algorithms
- Dashboard/visualization components

## Mission

Build the Pulseboard MVP: real-time event simulator → sliding windows → rule engine → SSE → minimal React UI with alerts and sparkline.

## Workflow Guardrails

- Never commit to `main`. Use feature branches + small PRs.
- Conventional Commits (feat, fix, chore, docs, refactor, test).
- Each PR: brief description, checklist, links to issues, passing tests.
- No secrets in repo.

## Acceptance Bar

Running end-to-end demo + basic tests green.

## Order of Work (Epics)

A1 → A2 → A3 → B1 → B2 → C1 → D1 → E1 → E2 → F1 → F2 → G1 → G2 → G3 → G4 → I1 → I2 → J1 → J2.
(H1–H2 Kafka optional after F1.)

## Kickoff Task

1) Read `docs/TICKETS.md`.
2) Create GitHub issues for every ticket with proper labels.
3) Start with **A1** on a feature branch and open a PR.

