#!/bin/bash

# Service Mapping Discovery Script
# Helps discover and configure service mappings for the monitoring UI

echo "🔍 Discovering Eureka service mappings for IMC Smart Driver UI..."

if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo ""
    echo "Usage: $0 [APP_URL]"
    echo ""
    echo "This script helps you discover available Eureka services and suggests"
    echo "mappings for the monitoring UI components."
    echo ""
    echo "Examples:"
    echo "  $0                                                    # Use localhost:8080"
    echo "  $0 https://imc-smartdriver-ui.apps.domain.com        # Use specific URL"
    echo ""
    echo "The script will:"
    echo "1. Query all available Eureka services"
    echo "2. Show current component mappings"
    echo "3. Suggest configuration for manifest.yml"
    exit 0
fi

# Default to localhost, or use provided URL
APP_URL="${1:-http://localhost:8080}"

echo "📡 Querying services from: $APP_URL"
echo ""

# Check if app is responding
if ! curl -s "$APP_URL/actuator/health" >/dev/null; then
    echo "❌ Cannot reach application at $APP_URL"
    echo "   Make sure the app is running and accessible."
    exit 1
fi

echo "✅ Application is accessible"
echo ""

# Get all available services
echo "🔎 Discovering all available Eureka services..."
ALL_SERVICES_RESPONSE=$(curl -s "$APP_URL/api/components/all-services")

if [ $? -ne 0 ] || [ "$ALL_SERVICES_RESPONSE" = "" ]; then
    echo "❌ Failed to get services list"
    echo "   Service discovery might not be enabled or configured."
    exit 1
fi

# Check if service discovery is enabled
if echo "$ALL_SERVICES_RESPONSE" | grep -q '"error"'; then
    echo "❌ Service discovery not enabled"
    echo "   Add COMPONENT_HEALTH_DISCOVERY_ENABLED: true to your configuration"
    exit 1
fi

echo "📋 Available Eureka services:"
echo "$ALL_SERVICES_RESPONSE" | jq -r '.all_available_services | keys[]' | while read -r service; do
    echo "   • $service"
done

echo ""
echo "🎯 Current component mappings:"
echo "$ALL_SERVICES_RESPONSE" | jq -r '.configured_mappings | to_entries[] | "   \(.key) → \(.value)"'

echo ""
echo "🧩 Component mapping guide:"
echo "   generator  → Should map to telemetry generator service"
echo "   processor  → Should map to telemetry/events processor service"
echo "   hdfs       → Should map to HDFS sink service"
echo "   jdbc       → Should map to JDBC/database sink service"

echo ""
echo "🔧 Suggested manifest.yml configuration:"
echo ""

# Try to suggest mappings based on service names
GENERATOR_SERVICE=$(echo "$ALL_SERVICES_RESPONSE" | jq -r '.all_available_services | keys[]' | grep -i "telematics-gen\|generator" | head -1)
PROCESSOR_SERVICE=$(echo "$ALL_SERVICES_RESPONSE" | jq -r '.all_available_services | keys[]' | grep -i "processor\|telemetry.*processor" | head -1)
HDFS_SERVICE=$(echo "$ALL_SERVICES_RESPONSE" | jq -r '.all_available_services | keys[]' | grep -i "hdfs\|sink.*hdfs" | head -1)
JDBC_SERVICE=$(echo "$ALL_SERVICES_RESPONSE" | jq -r '.all_available_services | keys[]' | grep -i "jdbc\|vehicle.*sink\|events.*sink" | head -1)

# Build suggested mapping
SUGGESTED_MAPPING=""
[ "$GENERATOR_SERVICE" != "null" ] && [ "$GENERATOR_SERVICE" != "" ] && SUGGESTED_MAPPING="${SUGGESTED_MAPPING}generator:${GENERATOR_SERVICE},"
[ "$PROCESSOR_SERVICE" != "null" ] && [ "$PROCESSOR_SERVICE" != "" ] && SUGGESTED_MAPPING="${SUGGESTED_MAPPING}processor:${PROCESSOR_SERVICE},"
[ "$HDFS_SERVICE" != "null" ] && [ "$HDFS_SERVICE" != "" ] && SUGGESTED_MAPPING="${SUGGESTED_MAPPING}hdfs:${HDFS_SERVICE},"
[ "$JDBC_SERVICE" != "null" ] && [ "$JDBC_SERVICE" != "" ] && SUGGESTED_MAPPING="${SUGGESTED_MAPPING}jdbc:${JDBC_SERVICE},"

# Remove trailing comma
SUGGESTED_MAPPING=$(echo "$SUGGESTED_MAPPING" | sed 's/,$//')

if [ "$SUGGESTED_MAPPING" != "" ]; then
    echo "env:"
    echo "  COMPONENT_HEALTH_SERVICE_MAPPINGS: \"$SUGGESTED_MAPPING\""
else
    echo "# No obvious service name matches found."
    echo "# Manually configure based on your actual service names:"
    echo "env:"
    echo "  COMPONENT_HEALTH_SERVICE_MAPPINGS: \"generator:your-generator-service,processor:your-processor-service,hdfs:your-hdfs-service,jdbc:your-jdbc-service\""
fi

echo ""
echo "💡 To update your deployment:"
echo "   1. Copy the suggested mapping to your manifest.yml"
echo "   2. Run: cf push"
echo "   3. Verify: curl $APP_URL/api/components/health"

echo ""
echo "📖 For more details, see SERVICE_MAPPING.md"