#!/bin/bash

# =============================================================================
# IMC Smart Driver UI - Local Development Script
# =============================================================================
# This script sources config.env and starts the app locally with real Greenplum data
# Based on set-env-and-push.sh but modified for local development
# =============================================================================

set -e  # Exit on any error

echo "🚀 IMC Smart Driver UI - Local Development Start"
echo "================================================="

# Check if config.env exists
if [ ! -f "config.env" ]; then
    echo "❌ Error: config.env file not found!"
    echo "   Please copy config.env.example to config.env and update with your values."
    exit 1
fi

# Source the configuration
echo "📄 Loading configuration from config.env..."
source config.env

# Verify critical variables are set
if [ -z "$PGHOST" ] || [ -z "$PGDATABASE" ] || [ -z "$PGUSER" ]; then
    echo "❌ Error: Critical database variables not set in config.env"
    echo "   Required: PGHOST, PGDATABASE, PGUSER, PGPASSWORD"
    exit 1
fi

echo "✅ Configuration loaded successfully"
echo "   Database: $PGUSER@$PGHOST:$PGPORT/$PGDATABASE"
echo "   Environment: $ENVIRONMENT"
echo "   Real Data: $GREENPLUM_USE_REAL_DATA"

# Test database connectivity (optional)
echo ""
echo "🔌 Testing database connectivity..."
if command -v psql &> /dev/null; then
    echo "SELECT 'Connection test successful!' as status;" | psql -h "$PGHOST" -p "$PGPORT" -d "$PGDATABASE" -U "$PGUSER" -t 2>/dev/null && echo "✅ Database connection verified" || echo "⚠️  Database connection test failed (app will use simulated data)"
else
    echo "⚠️  psql not found - skipping connection test"
fi

# Build the application (optional - comment out if you want to skip build)
echo ""
echo "🔨 Building application..."
./mvnw clean compile

echo "✅ Build completed successfully"

# Export environment variables for Spring Boot
echo ""
echo "🌍 Setting environment variables for Spring Boot..."
export GREENPLUM_HOST="$PGHOST"
export GREENPLUM_PORT="$PGPORT" 
export GREENPLUM_DATABASE="$PGDATABASE"
export GREENPLUM_USER="$PGUSER"
export GREENPLUM_PASSWORD="$PGPASSWORD"
export TARGET_DATABASE="$TARGET_DATABASE"
export GREENPLUM_USE_REAL_DATA="$GREENPLUM_USE_REAL_DATA"
export SAFE_DRIVER_SQL_SCRIPT_URL="$SAFE_DRIVER_SQL_SCRIPT_URL"

# Display key configuration
echo "   GREENPLUM_USE_REAL_DATA: $GREENPLUM_USE_REAL_DATA"
echo "   GREENPLUM_HOST: $GREENPLUM_HOST"
echo "   GREENPLUM_DATABASE: $GREENPLUM_DATABASE"

# Start the application
echo ""
echo "🚀 Starting Safe Driver UI locally..."
echo "   Application will be available at: http://localhost:8080"
echo "   Click the Greenplum component to open the enhanced Safe Driver panel"
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

./mvnw spring-boot:run

echo ""
echo "🛑 Application stopped"