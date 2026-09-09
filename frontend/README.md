# Certificate Discovery Dashboard

A multi-cloud certificate discovery and inventory platform built with React, Vite, TypeScript, and WireMock.

## Features
- Multi-cloud configuration (AWS, Azure, GCP).
- Certificate scanning and discovery (simulated with WireMock).
- Advanced filtering and sorting.
- Test-Driven Development (TDD) with Playwright E2E tests.

## Prerequisites
- Node.js >= 18
- Docker, only for the WireMock quick start below

> Working against the real Spring Boot backend instead needs no Docker at all: run
> `./start.sh` from the repository root (see the root README for `--mode docker`, which
> is the opt-in, not the default). WireMock is for frontend-only work and the E2E suite.

## Quick Start

### 1. Start WireMock
WireMock acts as the API backend for local development and E2E testing.
```bash
docker compose up -d
```
It runs on port `8080`.

### 2. Start Frontend
```bash
cd frontend
npm install
npm run dev
```
The frontend should now run at `http://localhost:5173`.

### 3. Running Tests
Run unit and component tests:
```bash
cd frontend
npm run test
```

Run Playwright E2E user journey tests:
```bash
cd frontend
npx playwright test
```

Run everything:
```bash
cd frontend
npm run test:all
```

## Architecture
The application is purely frontend-driven and connects to API endpoints mapped in WireMock.

To point the UI to a real backend, modify `.env` or set the API base URL in `src/services/api.ts` (currently `VITE_API_BASE_URL=http://localhost:8080`).

## Mock Data
Dummy data and mock endpoints are managed by WireMock:
- `wiremock/mappings/` - API endpoint definitions.
- `wiremock/__files/` - Large JSON payloads (e.g. certificates list).
- `wiremock/data/` - Static sample files like `sample-account-config.csv`.
