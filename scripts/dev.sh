#!/bin/bash

# Pulseboard Development Script
# Starts both backend and frontend concurrently

set -e

echo "🚀 Starting Pulseboard development environment..."

# Function to cleanup background processes
cleanup() {
    echo ""
    echo "🛑 Shutting down development servers..."
    kill $(jobs -p) 2>/dev/null || true
    exit
}

# Trap SIGINT (Ctrl+C) to cleanup
trap cleanup SIGINT

# Start backend in background
echo "📦 Starting backend on :8080..."
cd backend
./gradlew bootRun &
BACKEND_PID=$!
cd ..

# Give backend time to start
echo "⏳ Waiting for backend to initialize..."
sleep 5

# Start frontend in background
echo "🌐 Starting frontend on :5173..."
cd ui
npm run dev &
FRONTEND_PID=$!
cd ..

echo ""
echo "✅ Development servers started:"
echo "   - Backend:  http://localhost:8080"
echo "   - Frontend: http://localhost:5173"
echo ""
echo "Press Ctrl+C to stop all servers"

# Wait for background processes
wait