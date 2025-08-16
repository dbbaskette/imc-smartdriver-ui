#!/bin/bash

# Smart Driver Monitoring UI - Development Script
# Kills any existing processes on port 8080 and starts the application

APP_PORT=8080
APP_NAME="imc-smartdriver-ui"

echo "🔍 Checking for existing processes on port $APP_PORT..."

# Kill any processes using the port
PIDS=$(lsof -ti:$APP_PORT 2>/dev/null)
if [ ! -z "$PIDS" ]; then
    echo "🗑️  Killing existing processes: $PIDS"
    echo $PIDS | xargs kill -9 2>/dev/null
    sleep 1
else
    echo "✅ Port $APP_PORT is available"
fi

echo "🚀 Starting $APP_NAME..."
echo "📱 Dashboard will be available at: http://localhost:$APP_PORT"
echo "🏥 Health endpoint: http://localhost:$APP_PORT/actuator/health"
echo ""
echo "Press Ctrl+C to stop the application"
echo "=========================================="

# Start the application
./mvnw spring-boot:run