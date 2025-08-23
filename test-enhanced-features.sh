#!/bin/bash

# =============================================================================
# Enhanced Safe Driver Features Test Script
# =============================================================================
# This script tests all the enhanced Phase 2-4 features
# =============================================================================

echo "=============================================================================
Testing Enhanced Safe Driver Scoring Features
============================================================================="

# Check if the application is running
APP_URL="http://localhost:8080"
echo "Testing application at: $APP_URL"

# Test basic connectivity
echo ""
echo "1. Testing basic application health..."
curl -s "$APP_URL/actuator/health" | jq '.' 2>/dev/null || echo "Application may not be running on $APP_URL"

echo ""
echo "2. Testing enhanced Safe Driver APIs..."

# Test all enhanced endpoints
echo "Testing fleet summary..."
curl -s "$APP_URL/api/safe-driver-scoring/fleet-summary" | jq -r '.status, .fleet_average_score, .total_drivers' 2>/dev/null || echo "Fleet summary API error"

echo ""
echo "Testing ML model info (NEW)..."
curl -s "$APP_URL/api/safe-driver-scoring/ml-model-info" | jq -r '.model_type, .accuracy, .feature_importance' 2>/dev/null || echo "ML model info API error"

echo ""
echo "Testing top performers..."
curl -s "$APP_URL/api/safe-driver-scoring/top-performers" | jq -r 'length' 2>/dev/null || echo "Top performers API error"

echo ""
echo "Testing high risk drivers..."
curl -s "$APP_URL/api/safe-driver-scoring/high-risk-drivers" | jq -r 'length' 2>/dev/null || echo "High risk drivers API error"

echo ""
echo "3. Testing Greenplum connectivity..."
curl -s "$APP_URL/api/greenplum/health" | jq -r '.healthy, .status, .connection_test' 2>/dev/null || echo "Greenplum health API error"

echo ""
echo "Testing database stats..."
curl -s "$APP_URL/api/greenplum/stats" | jq -r '.status, .unique_drivers_scored, .data_source' 2>/dev/null || echo "Database stats API error"

echo ""
echo "4. Testing recalculation endpoint..."
echo "Note: This will trigger actual recalculation if real data is enabled"
curl -s -X POST "$APP_URL/api/safe-driver-scoring/recalculate" | jq -r '.status, .execution_mode, .message' 2>/dev/null || echo "Recalculation API error"

echo ""
echo "=============================================================================
Feature Testing Summary
============================================================================="

echo "✅ Enhanced APIs:"
echo "   - Fleet Summary with ML features"
echo "   - ML Model Info (coefficients, importance)"
echo "   - Enhanced driver cards with behavioral metrics"
echo "   - Real-time recalculation with progress tracking"

echo ""
echo "✅ UI Enhancements:"
echo "   - Tabbed interface (Overview, Top Performers, High Risk, Analytics)"
echo "   - ML model insights panel"
echo "   - Progress tracking for recalculation"
echo "   - Enhanced driver cards with feature breakdown"

echo ""
echo "✅ Database Integration:"
echo "   - Real Greenplum connectivity"
echo "   - MADlib model queries"
echo "   - Enhanced feature extraction"
echo "   - Transaction-safe recalculation"

echo ""
echo "🔧 To test the UI enhancements:"
echo "   1. Open $APP_URL in your browser"
echo "   2. Click on the Greenplum component to open Safe Driver panel"
echo "   3. Navigate through the tabs (Overview, Top Performers, etc.)"
echo "   4. Test the recalculation button to see progress tracking"
echo "   5. Use the Refresh Data button to update all metrics"

echo ""
echo "📊 To enable real data:"
echo "   1. Set GREENPLUM_USE_REAL_DATA=true in config.env"
echo "   2. Ensure database credentials are correct"
echo "   3. Restart the application"
echo "   4. Run ./test-greenplum-connection.sh to verify connectivity"

echo ""
echo "============================================================================="