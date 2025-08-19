# SCDF Health Check Solution

## Problem
JDBC Sink is deployed via Spring Cloud Data Flow (SCDF), which means:
- Dynamic CF URLs that change with each deployment (e.g., `rxeyrg6-vehicle-events-to-jdbc-...`)
- Not registered in Eureka service discovery
- Standard service discovery approaches won't work

## Current Status
- Health endpoint works: `https://rxeyrg6-vehicle-events-to-jdbc-vehicle-events-sink-v31.apps.tas-ndc.kuhn-labs.com/actuator/health`
- Returns `{"status":"UP"}` when healthy
- URL changes with each SCDF deployment

## Solution Options

### Option 1: SCDF REST API Integration
Query SCDF server to discover current app URLs dynamically.

```java
// Add SCDF client dependency
@Service
public class SCDFHealthService {
    @Value("${scdf.server.url}")
    private String scdfServerUrl;
    
    public String getAppUrl(String appName) {
        // GET {scdfServerUrl}/runtime/apps/{appName}
        // Parse response to get current URL
    }
}
```

**Pros:** Fully automated, always current
**Cons:** Requires SCDF server access, adds complexity

### Option 2: Environment Variable Configuration
Set health URLs as environment variables during SCDF deployment.

```bash
# During SCDF stream deployment
cf set-env imc-smartdriver-ui JDBC_SINK_HEALTH_URL https://current-jdbc-sink-url/actuator/health

# Or in manifest.yml
env:
  JDBC_SINK_HEALTH_URL: https://current-jdbc-sink-url/actuator/health
```

**Pros:** Simple, reliable
**Cons:** Manual step for each deployment

### Option 3: SCDF Environment Variable Injection
Use SCDF's ability to inject environment variables during deployment.

```bash
# In SCDF stream definition
stream deploy mystream --properties "app.vehicle-events-sink.spring.cloud.stream.bindings.output.destination=vehicle_events,app.monitoring-ui.JDBC_SINK_HEALTH_URL=https://\${vcap.application.uris[0]}/actuator/health"
```

**Pros:** Automated within SCDF workflow
**Cons:** Requires SCDF deployment script changes

### Option 4: Disable Health Check for SCDF Services
Accept that SCDF-deployed services can't be easily health-checked.

```yaml
component:
  health:
    jdbc-enabled: false  # Skip JDBC health checks
```

**Pros:** Simple, no maintenance
**Cons:** Loses health monitoring capability

## Recommended Approach

**Option 2 (Environment Variables)** is recommended because:
- Simple to implement and maintain
- Fits existing CF deployment patterns
- Reliable and predictable
- Easy to troubleshoot

## Implementation

### 1. Update Configuration
```yaml
# application.yml
component:
  health:
    jdbc-url: ${JDBC_SINK_HEALTH_URL:http://localhost:8083/actuator/health}
    jdbc-enabled: ${JDBC_SINK_HEALTH_ENABLED:true}
```

### 2. Deployment Process
```bash
# Get JDBC Sink URL from SCDF
JDBC_URL=$(cf apps | grep vehicle-events-sink | awk '{print $6}')

# Set environment variable
cf set-env imc-smartdriver-ui JDBC_SINK_HEALTH_URL "https://${JDBC_URL}/actuator/health"

# Restart to pick up new environment
cf restart imc-smartdriver-ui
```

### 3. Optional: Health Check Script
Create a script to automatically update URLs after SCDF deployments.

```bash
#!/bin/bash
# update-scdf-health-urls.sh

echo "Updating SCDF service health URLs..."

# Get current JDBC Sink URL
JDBC_URL=$(cf apps | grep vehicle-events-sink | awk '{print $6}')

if [ -n "$JDBC_URL" ]; then
    echo "Found JDBC Sink at: $JDBC_URL"
    cf set-env imc-smartdriver-ui JDBC_SINK_HEALTH_URL "https://$JDBC_URL/actuator/health"
    cf restart imc-smartdriver-ui
    echo "Health URLs updated successfully"
else
    echo "Warning: Could not find JDBC Sink URL"
fi
```

## Testing
```bash
# Verify environment variable is set
cf env imc-smartdriver-ui | grep JDBC_SINK_HEALTH_URL

# Test health endpoint directly
curl https://current-jdbc-sink-url/actuator/health

# Test through monitoring UI
curl http://monitoring-ui/api/components/health
```