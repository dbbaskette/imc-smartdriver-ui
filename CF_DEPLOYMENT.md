# Cloud Foundry Deployment Guide

This guide explains how to deploy the IMC Smart Driver UI to Cloud Foundry with service discovery.

## Prerequisites

1. **CF CLI installed and logged in**
2. **Services available**:
   - `messaging-c856b29a-1c7e-4fd5-ab3b-0633b90869cc` (RabbitMQ)
   - `imc-services` (Spring Cloud Service Registry)

## Quick Deployment

```bash
# 1. Build the application
./mvnw clean package -DskipTests

# 2. Deploy to Cloud Foundry
cf push

# 3. Check status
cf app imc-smartdriver-ui
```

## Service Discovery Features

### Automatic Service Discovery
The application automatically discovers and monitors these services via Eureka:
- `imc-telematics-gen` 
- `imc-telemetry-processor`
- `imc-hdfs-sink`
- `vehicle-events-sink`

### Health Monitoring
- **Automatic discovery**: No manual URL configuration needed
- **Real-time health checks**: Every 8 seconds
- **Visual indicators**: Red/green circles show component health
- **Pipeline awareness**: Animation stops when components are down

### API Endpoints
- `/api/components/health` - Component health status
- `/api/components/services` - Discovered services info
- `/api/rabbitmq/exchange/throughput` - RabbitMQ metrics

## Configuration

### Environment Variables (Optional)
```yaml
# In manifest.yml env section:
COMPONENT_HEALTH_DISCOVERY_ENABLED: true
EUREKA_CLIENT_ENABLED: true
EUREKA_CLIENT_REGISTER_WITH_EUREKA: false
EUREKA_CLIENT_FETCH_REGISTRY: true
```

### Service Binding
The application automatically configures itself using:
- **RabbitMQ**: `messaging-c856b29a-1c7e-4fd5-ab3b-0633b90869cc`
- **Eureka**: `imc-services`

## Testing

### Local Testing with Cloud Profile
```bash
# Test with cloud configuration locally
./mvnw spring-boot:run -Dspring.profiles.active=cloud
```

### Health Check URLs
```bash
# Application health
curl https://imc-smartdriver-ui.apps.tas-ndc.kuhn-labs.com/actuator/health

# Component health monitoring
curl https://imc-smartdriver-ui.apps.tas-ndc.kuhn-labs.com/api/components/health

# Discovered services
curl https://imc-smartdriver-ui.apps.tas-ndc.kuhn-labs.com/api/components/services
```

## Troubleshooting

### Service Discovery Issues
```bash
# Check if Eureka service is bound
cf env imc-smartdriver-ui | grep imc-services

# Check application logs
cf logs imc-smartdriver-ui --recent

# Verify other services are registered
cf apps | grep -E "(imc-telematics-gen|telemetry-processor|hdfs-sink|vehicle-events)"
```

### Common Issues

1. **Service not discovered**:
   - Ensure services are registered with same Eureka instance
   - Check service names match the patterns in code
   - Verify services are healthy and running

2. **Health checks failing**:
   - Ensure monitored services expose `/actuator/health`
   - Check network connectivity between CF apps
   - Verify service instances are healthy

3. **RabbitMQ connection issues**:
   - Verify service binding configuration
   - Check RabbitMQ service health
   - Validate credentials in VCAP_SERVICES

## Monitoring

### Visual Dashboard
- **Pipeline view**: Real-time data flow visualization
- **Health indicators**: Green/red circles above components
- **Info panel**: Component health status and metrics
- **Responsive design**: Works on desktop and tablet

### Key Features
- **Service discovery**: Automatic component detection
- **Real-time monitoring**: Live health and metrics updates
- **Failure simulation**: Animation stops when components fail
- **Cloud-native**: Designed for CF environments

## Scaling

The monitoring UI is designed as a single instance application:
```yaml
# In manifest.yml
instances: 1
memory: 1G
```

For high availability, consider:
- Running multiple instances behind a load balancer
- Using sticky sessions for WebSocket connections
- Implementing shared state storage if needed