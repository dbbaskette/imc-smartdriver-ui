# Cloud Foundry Deployment Guide

## Environment Variable Configuration

The Smart Driver UI uses environment variables for configuration in Cloud Foundry. Here are the different ways to set them:

### Method 1: Using the Deployment Script (Recommended)

1. **Copy and configure the environment file:**
   ```bash
   cp config.env.example config.env
   # Edit config.env with your actual values
   ```

2. **Deploy using the script:**
   ```bash
   ./set-env-and-push.sh
   ```

This script will:
- Load variables from `config.env`
- Build the application
- Push to CF with `--var` parameters
- Set all required environment variables

### Method 2: Manual CF Push with Variables

```bash
cf push --var PGHOST="your-greenplum-host" \
        --var PGPORT="5432" \
        --var PGDATABASE="your-database" \
        --var PGUSER="your-username" \
        --var PGPASSWORD="your-password" \
        --var TARGET_DATABASE="insurance_megacorp" \
        --var HDFS_NAMENODE_HOST="big-data-005.kuhn-labs.com" \
        --var HDFS_NAMENODE_PORT="8020" \
        --var ENVIRONMENT="production" \
        --var SAFE_DRIVER_SQL_SCRIPT_URL="https://raw.githubusercontent.com/dbbaskette/imc-schema/refs/heads/main/recalculate_safe_driver_scores.sql"
```

### Method 3: CF Set-Env (After Deployment)

```bash
# Set individual environment variables
cf set-env imc-smartdriver-ui GREENPLUM_HOST "your-greenplum-host"
cf set-env imc-smartdriver-ui GREENPLUM_DATABASE "your-database"
cf set-env imc-smartdriver-ui GREENPLUM_USER "your-username"
cf set-env imc-smartdriver-ui GREENPLUM_PASSWORD "your-password"

# Restart the app
cf restart imc-smartdriver-ui
```

### Method 4: User-Provided Service (For Credentials)

Create a user-provided service for database credentials:

```bash
cf create-user-provided-service greenplum-credentials -p '{
  "host": "your-greenplum-host",
  "port": "5432", 
  "database": "your-database",
  "user": "your-username",
  "password": "your-password"
}'
```

Then add to `manifest.yml` services section:
```yaml
services:
  - messaging-c856b29a-1c7e-4fd5-ab3b-0633b90869cc
  - imc-services
  - greenplum-credentials  # Add this line
```

## Required Environment Variables

### Database Configuration
- `GREENPLUM_HOST` - Greenplum server hostname
- `GREENPLUM_PORT` - Database port (default: 5432)
- `GREENPLUM_DATABASE` - Database name
- `GREENPLUM_USER` - Database username
- `GREENPLUM_PASSWORD` - Database password
- `TARGET_DATABASE` - Target schema/database name

### HDFS Configuration
- `HDFS_NAMENODE_HOST` - HDFS namenode hostname
- `HDFS_NAMENODE_PORT` - HDFS namenode port (default: 8020)

### Application Configuration
- `ENVIRONMENT` - Environment name (dev/staging/prod)
- `SAFE_DRIVER_SQL_SCRIPT_URL` - URL to the safe driver recalculation SQL script

## Spring Configuration Priority

The application uses this priority order for configuration:

1. **CF Environment Variables** (`GREENPLUM_HOST`, etc.)
2. **Spring Properties** (`greenplum.host`, etc.)
3. **Default Values** (hardcoded fallbacks)

For example, `GREENPLUM_HOST` environment variable overrides `greenplum.host` Spring property.

## Verifying Configuration

After deployment, check the environment:

```bash
cf env imc-smartdriver-ui
```

Look for your variables in the "User-Provided" section.

## Security Notes

⚠️ **CRITICAL SECURITY REMINDER**:
- **Never commit real credentials to git or documentation files**
- All examples use placeholder values like `your-password` - replace with actual values from your secure credential store
- Never commit `config.env` to git (it's in `.gitignore`)
- Use CF user-provided services for sensitive credentials in production
- Consider using CF service bindings for managed database services
- Store actual credentials in secure password managers or environment-specific configuration