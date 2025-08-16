# Service Discovery Mapping Guide

This document explains how to map Eureka service names to UI components in the IMC Smart Driver monitoring dashboard.

## Overview

The monitoring UI displays 4 components:
- **Generator** (🚗) - Telemetry data generator
- **Processor** (⚙️) - Events processor for accident detection  
- **HDFS** (🗄️) - HDFS sink for data storage
- **JDBC** (🗃️) - JDBC processor for database writes

These need to be mapped to actual Eureka service names for health monitoring.

## Configuration Methods

### Method 1: Environment Variables (Recommended for CF)

Add to your `manifest.yml`:
```yaml
env:
  COMPONENT_HEALTH_SERVICE_MAPPINGS: "generator:imc-telematics-gen,processor:imc-telemetry-processor,hdfs:imc-hdfs-sink,jdbc:vehicle-events-sink"
```

### Method 2: Configuration File

In `application-cloud.yml`:
```yaml
component:
  health:
    service-mappings: "generator:imc-telematics-gen,processor:imc-telemetry-processor,hdfs:imc-hdfs-sink,jdbc:vehicle-events-sink"
```

### Method 3: Discovery and Manual Mapping

If you don't know the exact service names:

1. **Deploy without mappings** (uses defaults)
2. **Check available services**:
   ```bash
   curl https://your-app.apps.domain.com/api/components/all-services
   ```
3. **Update mappings** based on discovered services

## Mapping Format

The service mappings use this format:
```
"component1:service1,component2:service2,component3:service3"
```

**Example:**
```
"generator:imc-telematics-gen,processor:RxEyrg6-telemetry-processor-v30,hdfs:RxEyrg6-hdfs-sink-v51,jdbc:RxEyrg6-jdbc-sink-v25"
```

## Component Names (UI Display)

| Component | UI Display Name | Description |
|-----------|-----------------|-------------|
| `generator` | Telemetry Generator | Vehicle data generator |
| `processor` | Events Processor | Accident detection processor |
| `hdfs` | HDFS Sink | Data storage sink |
| `jdbc` | JDBC Processor | Database processor |

## Eureka Service Name Patterns

Common patterns for CF deployed services:

### Stable Names
- `imc-telematics-gen` (generator)
- `imc-telemetry-processor` (processor)
- `imc-hdfs-sink` (HDFS sink)
- `vehicle-events-sink` (JDBC processor)

### Versioned Names (CF with prefixes)
- `RxEyrg6-telemetry-to-processor-imc-telemetry-processor-v30`
- `RxEyrg6-telemetry-to-hdfs-imc-hdfs-sink-v51`
- `RxEyrg6-vehicle-events-to-jdbc-vehicle-events-sink-v25`

## Discovery Endpoints

Use these endpoints to discover available services:

### All Available Services
```bash
GET /api/components/all-services
```
**Response:**
```json
{
  "all_available_services": {
    "imc-telematics-gen": [{"instanceId": "...", "uri": "..."}],
    "imc-telemetry-processor": [{"instanceId": "...", "uri": "..."}]
  },
  "configured_mappings": {
    "generator": "imc-telematics-gen",
    "processor": "imc-telemetry-processor"
  }
}
```

### Configured Services Only
```bash
GET /api/components/services
```

### Component Health Status
```bash
GET /api/components/health
```
**Response:**
```json
{
  "component_health": {
    "generator": true,
    "processor": false,
    "hdfs": true,
    "jdbc": false
  },
  "discovery_mode": "service_discovery"
}
```

## Default Mappings

If no mappings are configured, these defaults are used:
```yaml
generator: imc-telematics-gen
processor: imc-telemetry-processor
hdfs: imc-hdfs-sink
jdbc: vehicle-events-sink
```

## Troubleshooting

### Service Not Found
```bash
# Check what services are actually registered
curl https://your-app/api/components/all-services | jq '.all_available_services | keys'

# Update mapping with correct service name
```

### Wrong Component Mapping
```bash
# Check current mappings
curl https://your-app/api/components/services | jq '.service_mappings'

# Update environment variable or configuration
```

### Health Check Failing
```bash
# Check if service has health endpoint
curl https://service-url/actuator/health

# Verify service is registered and healthy in Eureka
```

## Example Complete Configuration

For a typical CF deployment:

```yaml
# manifest.yml
applications:
  - name: imc-smartdriver-ui
    env:
      COMPONENT_HEALTH_SERVICE_MAPPINGS: "generator:imc-telematics-gen,processor:RxEyrg6-telemetry-processor-v30,hdfs:RxEyrg6-hdfs-sink-v51,jdbc:RxEyrg6-jdbc-sink-v25"
      COMPONENT_HEALTH_DISCOVERY_ENABLED: true
```

This provides flexible mapping between your UI component names and the actual Eureka service names, handling both stable and versioned service naming patterns.