# Cloud Foundry Health Monitoring Setup

This document explains how to configure the health monitoring system to work with Cloud Foundry applications.

## Overview

The system monitors 4 Cloud Foundry applications:
- **imc-telematics-gen** (stable name)
- **RxEyrg6-telemetry-to-hdfs-imc-hdfs-sink-v51** (version changes)
- **RxEyrg6-telemetry-to-processor-imc-telemetry-processor-v30** (version changes)
- **RxEyrg6-vehicle-events-to-jdbc-vehicle-events-sink-v25** (version changes)

## Configuration Options

### Option 1: Automated Discovery (Recommended)

Use the provided discovery script to automatically find CF apps and generate configuration:

```bash
# Make sure you're logged into CF first
cf login

# Run the discovery script
./discover-apps.sh

# This generates application-cloud.yml with discovered URLs
```

**Benefits:**
- ✅ Handles version changes automatically
- ✅ No manual URL maintenance
- ✅ Quick setup and updates

### Option 2: Manual Configuration

Copy the template and manually configure URLs:

```bash
# Copy template
cp src/main/resources/application-cloud.yml.template src/main/resources/application-cloud.yml

# Edit URLs to match your CF apps
vi src/main/resources/application-cloud.yml
```

## Running with Cloud Profile

Once configured, run the application with the cloud profile:

```bash
# Local development (uses localhost URLs)
./mvnw spring-boot:run

# Cloud Foundry monitoring (uses CF app URLs)
./mvnw spring-boot:run -Dspring.profiles.active=cloud
```

## Configuration Format

The `application-cloud.yml` should contain:

```yaml
component:
  health:
    generator-url: https://imc-telematics-gen.apps.domain.com/actuator/health
    processor-url: https://RxEyrg6-telemetry-to-processor-imc-telemetry-processor-v30.apps.domain.com/actuator/health
    hdfs-url: https://RxEyrg6-telemetry-to-hdfs-imc-hdfs-sink-v51.apps.domain.com/actuator/health
    jdbc-url: https://RxEyrg6-vehicle-events-to-jdbc-vehicle-events-sink-v25.apps.domain.com/actuator/health
```

## Health Monitoring Features

### Visual Indicators
- 🟢 **Green circles** = Component healthy (UP)
- 🔴 **Red circles** = Component unhealthy (DOWN)
- ⚫ **Gray circles** = Unknown status

### Animation Flow Control
- Data packets only flow between healthy components
- When components go down, pipeline flow stops realistically
- Demonstrates actual failure impact on the pipeline

### Real-time Updates
- Health checks every 8 seconds
- Live visual updates without page refresh
- API endpoint: `/api/components/health`

## Troubleshooting

### Discovery Script Issues

```bash
# Check CF CLI installation
cf --version

# Check CF login status
cf target

# Check if apps are running
cf apps | grep -E "(imc-telematics-gen|hdfs-sink|telemetry-processor|jdbc-sink)"
```

### Manual URL Discovery

```bash
# Find specific app
cf apps | grep "telemetry-processor"

# Get app details and routes
cf app RxEyrg6-telemetry-to-processor-imc-telemetry-processor-v30
```

### Health Endpoint Testing

```bash
# Test individual component health
curl https://your-app.apps.domain.com/actuator/health

# Test monitoring API
curl http://localhost:8080/api/components/health
```

## Version Updates

When CF apps get new versions:

**Option 1 (Automated):**
```bash
./discover-apps.sh  # Re-run discovery
```

**Option 2 (Manual):**
- Update URLs in `application-cloud.yml`
- Restart the monitoring application

## Security Considerations

- Actuator endpoints should be secured in production
- Consider using CF security groups to restrict access
- Monitor logs for failed health checks

## Performance Notes

- Health checks run every 8 seconds
- Failed health checks are logged as warnings
- No impact on monitored applications
- Graceful degradation when components are unreachable