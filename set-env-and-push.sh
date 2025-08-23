#!/bin/bash

# =============================================================================
# IMC Smart Driver UI - Cloud Foundry Deployment Script
# =============================================================================
# This script sources config.env and pushes the app to CF with environment variables
# =============================================================================

set -e  # Exit on any error

echo "🚀 IMC Smart Driver UI - Cloud Foundry Deployment"
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

# Build the application
echo ""
echo "🔨 Building application..."
./mvnw clean package -DskipTests

if [ ! -f "target/imc-smartdriver-ui-1.0.0.jar" ]; then
    echo "❌ Error: Build failed - JAR file not found"
    exit 1
fi

echo "✅ Build completed successfully"

# Set CF environment variables and push
echo ""
echo "☁️  Deploying to Cloud Foundry..."
echo "   Setting environment variables and pushing..."

cf push --var PGHOST="$PGHOST" \
        --var PGPORT="$PGPORT" \
        --var PGDATABASE="$PGDATABASE" \
        --var PGUSER="$PGUSER" \
        --var PGPASSWORD="$PGPASSWORD" \
        --var TARGET_DATABASE="$TARGET_DATABASE" \
        --var HDFS_NAMENODE_HOST="$HDFS_NAMENODE_HOST" \
        --var HDFS_NAMENODE_PORT="$HDFS_NAMENODE_PORT" \
        --var ENVIRONMENT="$ENVIRONMENT" \
        --var SAFE_DRIVER_SQL_SCRIPT_URL="$SAFE_DRIVER_SQL_SCRIPT_URL" \
        --var GREENPLUM_USE_REAL_DATA="$GREENPLUM_USE_REAL_DATA"

echo ""
echo "🎉 Deployment completed!"
echo "   Application URL: https://imc-smartdriver-ui.apps.tas-ndc.kuhn-labs.com"
echo ""