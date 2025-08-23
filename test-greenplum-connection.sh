#!/bin/bash

# =============================================================================
# Test Greenplum Database Connection Script
# =============================================================================
# This script tests the database connectivity using the same configuration
# as the Spring Boot application
# =============================================================================

# Load environment variables from config.env
if [ -f "./config.env" ]; then
    echo "Loading configuration from config.env..."
    source ./config.env
else
    echo "Warning: config.env not found. Using defaults."
fi

# Set defaults if not provided
GREENPLUM_HOST=${GREENPLUM_HOST:-${PGHOST:-"big-data-001.kuhn-labs.com"}}
GREENPLUM_PORT=${GREENPLUM_PORT:-${PGPORT:-"5432"}}
GREENPLUM_DATABASE=${GREENPLUM_DATABASE:-${PGDATABASE:-"insurance_megacorp"}}
GREENPLUM_USER=${GREENPLUM_USER:-${PGUSER:-"gpadmin"}}
GREENPLUM_PASSWORD=${GREENPLUM_PASSWORD:-${PGPASSWORD}}

echo "=============================================================================
Testing Greenplum Database Connection
=============================================================================
Host: $GREENPLUM_HOST
Port: $GREENPLUM_PORT  
Database: $GREENPLUM_DATABASE
User: $GREENPLUM_USER
Password: [${#GREENPLUM_PASSWORD} characters]
============================================================================="

# Test basic connectivity
echo "Testing basic connectivity..."
if command -v psql &> /dev/null; then
    echo "Using psql to test connection..."
    
    export PGHOST=$GREENPLUM_HOST
    export PGPORT=$GREENPLUM_PORT
    export PGDATABASE=$GREENPLUM_DATABASE
    export PGUSER=$GREENPLUM_USER
    export PGPASSWORD=$GREENPLUM_PASSWORD
    
    # Test connection with a simple query
    echo "SELECT 'Connection successful!' as status, version() as database_version;" | psql -t
    
    if [ $? -eq 0 ]; then
        echo "✅ Database connection successful!"
        
        echo ""
        echo "Testing Safe Driver Scoring tables..."
        
        # Test if required tables exist
        echo "Checking for required tables..."
        psql -t -c "
        SELECT 
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'safe_driver_scores') 
                 THEN '✅ safe_driver_scores table exists' 
                 ELSE '❌ safe_driver_scores table missing' END,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'driver_ml_training_data') 
                 THEN '✅ driver_ml_training_data table exists' 
                 ELSE '❌ driver_ml_training_data table missing' END,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'driver_accident_model') 
                 THEN '✅ driver_accident_model table exists' 
                 ELSE '❌ driver_accident_model table missing' END,
            CASE WHEN EXISTS (SELECT 1 FROM information_schema.views WHERE table_name = 'v_current_driver_scores') 
                 THEN '✅ v_current_driver_scores view exists' 
                 ELSE '❌ v_current_driver_scores view missing' END;
        "
        
        echo ""
        echo "Testing sample queries..."
        
        # Test sample data query
        echo "Sample driver scores (if data exists):"
        psql -t -c "
        SELECT 
            driver_id, 
            score, 
            calculation_date 
        FROM safe_driver_scores 
        ORDER BY score DESC 
        LIMIT 3;" 2>/dev/null || echo "No data found in safe_driver_scores table"
        
    else
        echo "❌ Database connection failed!"
    fi
    
else
    echo "❌ psql command not found. Install PostgreSQL client tools to test connection."
    echo ""
    echo "Alternative: Test connection using Java application directly."
fi

echo ""
echo "=============================================================================
To enable real data in the application:
1. Set GREENPLUM_USE_REAL_DATA=true in config.env
2. Ensure database credentials are correct
3. Restart the application
============================================================================="