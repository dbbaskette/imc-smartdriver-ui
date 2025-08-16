# Configuration Setup

This project uses template-based configuration to keep sensitive credentials out of version control.

## Configuration Files

### Templates (Tracked in Git)
- `src/main/resources/application.yml.template` - Local development configuration template
- `src/main/resources/application-cloud.yml.template` - Cloud Foundry configuration template

### Actual Config Files (Git Ignored)
- `src/main/resources/application.yml` - Local development configuration
- `src/main/resources/application-cloud.yml` - Cloud Foundry configuration

## Initial Setup

The actual configuration files are automatically created from templates during build. To customize:

1. **Copy templates** (if not already done):
   ```bash
   cp src/main/resources/application.yml.template src/main/resources/application.yml
   cp src/main/resources/application-cloud.yml.template src/main/resources/application-cloud.yml
   ```

2. **Configure local environment** by editing `application.yml` or setting environment variables:
   ```bash
   export RABBITMQ_HOST=your-rabbit-host
   export RABBITMQ_USERNAME=your-username
   export RABBITMQ_PASSWORD=your-password
   export GP_PASSWORD=your-greenplum-password
   ```

## Profiles

### Local Profile (`local`)
- Default profile for development
- Uses localhost defaults with environment variable overrides
- RabbitMQ: localhost:5672 (guest/guest)
- Metrics mode: `mock` (no external dependencies required)

### Cloud Profile (`cloud`)
- Activated with `SPRING_PROFILES_ACTIVE=cloud`
- Auto-configures RabbitMQ via Cloud Foundry service bindings (VCAP_SERVICES)
- Metrics mode: `real` (connects to actual external systems)
- Enhanced monitoring and logging for production

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `RABBITMQ_HOST` | localhost | RabbitMQ server hostname |
| `RABBITMQ_PORT` | 5672 | RabbitMQ AMQP port |
| `RABBITMQ_USERNAME` | guest | RabbitMQ username |
| `RABBITMQ_PASSWORD` | guest | RabbitMQ password |
| `RABBITMQ_QUEUE_NAME` | vehicle-events | Queue name for telemetry data |
| `GP_PASSWORD` | (empty) | Greenplum database password |
| `METRICS_MODE` | mock/real | Data collection mode |

## Usage

### Local Development
```bash
./run.sh
# or
./mvnw spring-boot:run
```

### Cloud Foundry Deployment
```bash
export SPRING_PROFILES_ACTIVE=cloud
./mvnw clean package
cf push imc-smartdriver-ui -p target/imc-smartdriver-ui-1.0.0-SNAPSHOT.jar
```

## Security Notes

- Never commit actual configuration files to git
- Use environment variables for sensitive data
- Templates show structure but contain no secrets
- Cloud Foundry automatically injects service credentials