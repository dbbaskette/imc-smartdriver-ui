#!/bin/bash

# Build and Deploy Script for IMC Smart Driver UI
set -e

echo "🏗️  Building IMC Smart Driver UI..."

# Clean and build
echo "📦 Building application..."
./mvnw clean package -DskipTests

# Check if JAR was created
JAR_FILE="target/imc-smartdriver-ui-0.0.2.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ Build failed - JAR file not found: $JAR_FILE"
    exit 1
fi

echo "✅ Build successful: $JAR_FILE"

# Check if logged into CF
if ! cf target &> /dev/null; then
    echo "❌ Not logged into Cloud Foundry. Please run 'cf login' first."
    exit 1
fi

echo "🚀 Deploying to Cloud Foundry..."

# Deploy to CF
cf push

# Check deployment status
if cf app imc-smartdriver-ui | grep -q "running"; then
    echo ""
    echo "🎉 Deployment successful!"
    echo ""
    echo "📊 Application URLs:"
    echo "   Dashboard: https://imc-smartdriver-ui.apps.tas-ndc.kuhn-labs.com"
    echo "   Health:    https://imc-smartdriver-ui.apps.tas-ndc.kuhn-labs.com/actuator/health"
    echo "   Metrics:   https://imc-smartdriver-ui.apps.tas-ndc.kuhn-labs.com/api/components/health"
    echo ""
    echo "🔍 Service Discovery:"
    echo "   Services:  https://imc-smartdriver-ui.apps.tas-ndc.kuhn-labs.com/api/components/services"
    echo "   All Svcs:  https://imc-smartdriver-ui.apps.tas-ndc.kuhn-labs.com/api/components/all-services"
    echo ""
    echo "🧩 To discover and configure service mappings:"
    echo "   ./discover-mappings.sh https://imc-smartdriver-ui.apps.tas-ndc.kuhn-labs.com"
    echo ""
    echo "📱 To test locally with cloud profile:"
    echo "   ./mvnw spring-boot:run -Dspring.profiles.active=cloud"
else
    echo "❌ Deployment may have failed. Check with 'cf app imc-smartdriver-ui'"
    exit 1
fi