#!/bin/bash

# CF App Discovery Script
# Discovers Cloud Foundry app URLs and generates application-cloud.yml

echo "🔍 Discovering Cloud Foundry applications..."

# Check if cf CLI is available
if ! command -v cf &> /dev/null; then
    echo "❌ CF CLI not found. Please install and login to CF first."
    exit 1
fi

# Check if logged in
if ! cf target &> /dev/null; then
    echo "❌ Not logged into CF. Please run 'cf login' first."
    exit 1
fi

# App name patterns
declare -A app_patterns=(
    ["generator"]="imc-telematics-gen"
    ["hdfs"]="telemetry-to-hdfs-imc-hdfs-sink"
    ["processor"]="telemetry-to-processor-imc-telemetry-processor"
    ["jdbc"]="vehicle-events-to-jdbc-vehicle-events-sink"
)

# Get current CF target info
cf_target=$(cf target)
cf_domain=$(echo "$cf_target" | grep "api endpoint" | awk '{print $3}' | sed 's|https://api\.|apps.|')

echo "📍 CF Domain: $cf_domain"
echo ""

# Discover apps
declare -A discovered_urls

for component in "${!app_patterns[@]}"; do
    pattern="${app_patterns[$component]}"
    echo "🔎 Searching for $component ($pattern)..."
    
    # Find app matching pattern
    app_name=$(cf apps | grep "$pattern" | awk '{print $1}' | head -1)
    
    if [ -n "$app_name" ]; then
        # Get app route
        app_route=$(cf app "$app_name" | grep "routes:" -A 1 | tail -1 | awk '{print $1}')
        
        if [ -n "$app_route" ]; then
            app_url="https://$app_route/actuator/health"
            discovered_urls[$component]="$app_url"
            echo "   ✅ Found: $app_name -> $app_url"
        else
            echo "   ⚠️  Found app '$app_name' but no route"
        fi
    else
        echo "   ❌ No app found matching '$pattern'"
    fi
done

echo ""
echo "📝 Generating application-cloud.yml..."

# Generate application-cloud.yml
cat > src/main/resources/application-cloud.yml << EOF
# Auto-generated Cloud Foundry configuration
# Generated on $(date)

spring:
  profiles:
    active: cloud

# Component health monitoring URLs (auto-discovered)
component:
  health:
    generator-url: ${discovered_urls[generator]:-http://localhost:8082/actuator/health}
    processor-url: ${discovered_urls[processor]:-http://localhost:8080/actuator/health}
    hdfs-url: ${discovered_urls[hdfs]:-http://localhost:8081/actuator/health}
    jdbc-url: ${discovered_urls[jdbc]:-http://localhost:8083/actuator/health}

# RabbitMQ configuration (from CF service binding)
rabbitmq:
  management:
    api-url: \${vcap.services.rmq.credentials.http_api_uri}
  host: \${vcap.services.rmq.credentials.protocols.amqp.uris[0]}
  username: \${vcap.services.rmq.credentials.protocols.amqp.username}
  password: \${vcap.services.rmq.credentials.protocols.amqp.password}
  queue:
    name: telematics_exchange.crash-detection-group
  display:
    queues: telematics_exchange.crash-detection-group,vehicle_events.jdbc-group

logging:
  level:
    com.insurancemegacorp.monitoring: DEBUG
EOF

echo "✅ Generated src/main/resources/application-cloud.yml"
echo ""
echo "🚀 Discovered URLs:"
for component in "${!discovered_urls[@]}"; do
    echo "   $component: ${discovered_urls[$component]}"
done

echo ""
echo "💡 To use: ./mvnw spring-boot:run -Dspring.profiles.active=cloud"