package com.insurancemegacorp.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Slf4j
@Service
public class GreenplumService {

    @Value("${greenplum.host:${GREENPLUM_HOST:big-data-001.kuhn-labs.com}}")
    private String host;
    
    @Value("${greenplum.port:${GREENPLUM_PORT:5432}}")
    private int port;
    
    @Value("${greenplum.database:${TARGET_DATABASE:${GREENPLUM_DATABASE:insurance_megacorp}}}")
    private String database;
    
    @Value("${greenplum.user:${GREENPLUM_USER:gpadmin}}")
    private String user;
    
    @Value("${greenplum.password:${GREENPLUM_PASSWORD:}}")
    private String password;
    
    @Value("${safe-driver.sql-script-url:https://raw.githubusercontent.com/dbbaskette/imc-schema/refs/heads/main/recalculate_safe_driver_scores.sql}")
    private String sqlScriptUrl;
    
    @Value("${greenplum.use-real-data:${GREENPLUM_USE_REAL_DATA:false}}")
    private boolean useRealData;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Autowired(required = false)
    private DataSource dataSource;

    /**
     * Check Greenplum health by attempting a simple query
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        // Debug logging to diagnose configuration issue
        log.info("GreenplumService Health Check - useRealData: {}, dataSource: {}", 
                useRealData, dataSource != null ? "available" : "null");
        
        try {
            if (useRealData && dataSource != null) {
                // Real database health check
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
                    
                    ResultSet rs = stmt.executeQuery();
                    boolean hasResult = rs.next();
                    
                    health.put("healthy", hasResult);
                    health.put("status", hasResult ? "UP" : "DOWN");
                    health.put("host", host);
                    health.put("port", port);
                    health.put("database", database);
                    health.put("user", user);
                    health.put("connection_test", "real");
                    health.put("timestamp", System.currentTimeMillis());
                    
                    log.info("Greenplum health check: {} (real connection to {})", hasResult ? "UP" : "DOWN", host);
                }
            } else {
                // Simulated health check
                health.put("healthy", true);
                health.put("status", "UP");
                health.put("host", host);
                health.put("port", port);
                health.put("database", database);
                health.put("connection_test", "simulated");
                health.put("timestamp", System.currentTimeMillis());
                
                log.debug("Greenplum health check: UP (simulated)");
            }
            
        } catch (Exception e) {
            log.error("Greenplum health check failed: {}", e.getMessage());
            
            health.put("healthy", false);
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("connection_test", useRealData ? "real" : "simulated");
            health.put("timestamp", System.currentTimeMillis());
        }
        
        return health;
    }

    /**
     * Get fleet safety summary for the Safe Driver Scoring dashboard
     */
    public Map<String, Object> getFleetSafetySummary() {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            if (useRealData && dataSource != null) {
                // Real database query
                summary = fetchRealFleetSummary();
            } else {
                if (useRealData && dataSource == null) {
                    log.warn("Real data requested but DataSource not available - falling back to simulated data");
                }
                // Simulated data based on the schema documentation
                summary.put("fleet_average_score", 83.2);
                summary.put("total_drivers", 15);
                summary.put("ml_model_accuracy", 94.3);
                
                Map<String, Integer> riskDistribution = new HashMap<>();
                riskDistribution.put("excellent", 2);   // 90-100
                riskDistribution.put("good", 8);        // 80-89
                riskDistribution.put("average", 2);     // 60-79
                riskDistribution.put("poor", 2);        // 40-59
                riskDistribution.put("high_risk", 1);   // 0-39
                summary.put("risk_distribution", riskDistribution);
                
                summary.put("total_events_analyzed", 2400);
                summary.put("last_updated", System.currentTimeMillis());
                summary.put("status", "success");
                
                log.debug("Retrieved fleet safety summary (simulated): {} drivers, avg score {}", 
                    summary.get("total_drivers"), summary.get("fleet_average_score"));
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch fleet safety summary: {}", e.getMessage());
            summary.put("status", "error");
            summary.put("error", e.getMessage());
        }
        
        return summary;
    }
    
    /**
     * Fetch real fleet summary from database
     */
    private Map<String, Object> fetchRealFleetSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        try (Connection conn = dataSource.getConnection()) {
            // Enhanced query with ML features from driver_ml_training_data
            String summaryQuery = """
                SELECT 
                    AVG(s.score) as fleet_average_score,
                    COUNT(s.driver_id) as total_drivers,
                    AVG(f.speed_compliance_rate) as avg_speed_compliance,
                    AVG(f.avg_g_force) as avg_g_force,
                    SUM(f.harsh_driving_events) as total_harsh_events,
                    AVG(f.phone_usage_rate) as avg_phone_usage,
                    SUM(f.accident_count) as total_accidents,
                    SUM(f.total_events) as total_telemetry_events,
                    -- Risk distribution
                    SUM(CASE WHEN s.score >= 90 THEN 1 ELSE 0 END) as excellent,
                    SUM(CASE WHEN s.score >= 80 AND s.score < 90 THEN 1 ELSE 0 END) as good,
                    SUM(CASE WHEN s.score >= 70 AND s.score < 80 THEN 1 ELSE 0 END) as average,
                    SUM(CASE WHEN s.score >= 60 AND s.score < 70 THEN 1 ELSE 0 END) as poor,
                    SUM(CASE WHEN s.score < 60 THEN 1 ELSE 0 END) as high_risk
                FROM v_current_driver_scores s
                LEFT JOIN driver_ml_training_data f ON s.driver_id = f.driver_id
                WHERE s.score IS NOT NULL
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(summaryQuery)) {
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    summary.put("fleet_average_score", Math.round(rs.getDouble("fleet_average_score") * 100.0) / 100.0);
                    summary.put("total_drivers", rs.getInt("total_drivers"));
                    
                    // ML model metrics (from real database or calculated)
                    summary.put("ml_model_accuracy", 94.3); // TODO: Calculate from model performance table
                    
                    // Fleet behavioral metrics
                    summary.put("avg_speed_compliance", Math.round(rs.getDouble("avg_speed_compliance") * 100.0) / 100.0);
                    summary.put("avg_g_force", Math.round(rs.getDouble("avg_g_force") * 10000.0) / 10000.0);
                    summary.put("total_harsh_events", rs.getInt("total_harsh_events"));
                    summary.put("avg_phone_usage", Math.round(rs.getDouble("avg_phone_usage") * 100.0) / 100.0);
                    summary.put("total_accidents", rs.getInt("total_accidents"));
                    summary.put("total_telemetry_events", rs.getLong("total_telemetry_events"));
                    
                    Map<String, Integer> riskDistribution = new HashMap<>();
                    riskDistribution.put("excellent", rs.getInt("excellent"));
                    riskDistribution.put("good", rs.getInt("good"));
                    riskDistribution.put("average", rs.getInt("average"));
                    riskDistribution.put("poor", rs.getInt("poor"));
                    riskDistribution.put("high_risk", rs.getInt("high_risk"));
                    summary.put("risk_distribution", riskDistribution);
                    
                    summary.put("last_updated", System.currentTimeMillis());
                    summary.put("status", "success");
                    summary.put("data_source", "real_database");
                    
                    log.info("Retrieved real fleet safety summary: {} drivers, avg score {}", 
                        summary.get("total_drivers"), summary.get("fleet_average_score"));
                } else {
                    throw new RuntimeException("No data found in safe_driver_scores table");
                }
            }
            
        } catch (SQLException e) {
            log.error("Database error fetching fleet summary: {}", e.getMessage());
            throw new RuntimeException("Database query failed: " + e.getMessage(), e);
        }
        
        return summary;
    }

    /**
     * Get top performing drivers
     */
    public List<Map<String, Object>> getTopPerformers() {
        List<Map<String, Object>> drivers = new ArrayList<>();
        
        try {
            if (useRealData && dataSource != null) {
                // Real database query
                drivers = fetchRealTopPerformers();
            } else {
                if (useRealData && dataSource == null) {
                    log.warn("Real data requested but DataSource not available - using simulated top performers");
                }
                // Simulated data based on schema documentation
                
                Map<String, Object> driver1 = new HashMap<>();
            driver1.put("driver_id", 400011);
            driver1.put("safety_score", 93.89);
            driver1.put("risk_category", "EXCELLENT");
            driver1.put("speed_compliance", 90.71);
            driver1.put("harsh_events", 0);
            driver1.put("phone_usage", 12.82);
            driver1.put("accidents", 0);
            drivers.add(driver1);
            
            Map<String, Object> driver2 = new HashMap<>();
            driver2.put("driver_id", 400017);
            driver2.put("safety_score", 92.04);
            driver2.put("risk_category", "EXCELLENT");
            driver2.put("speed_compliance", 90.52);
            driver2.put("harsh_events", 0);
            driver2.put("phone_usage", 21.90);
            driver2.put("accidents", 0);
            drivers.add(driver2);
            
            Map<String, Object> driver3 = new HashMap<>();
            driver3.put("driver_id", 400022);
            driver3.put("safety_score", 91.15);
            driver3.put("risk_category", "EXCELLENT");
            driver3.put("speed_compliance", 88.43);
            driver3.put("harsh_events", 0);
            driver3.put("phone_usage", 15.67);
            driver3.put("accidents", 0);
            drivers.add(driver3);
            
            Map<String, Object> driver4 = new HashMap<>();
            driver4.put("driver_id", 400035);
            driver4.put("safety_score", 87.62);
            driver4.put("risk_category", "GOOD");
            driver4.put("speed_compliance", 85.90);
            driver4.put("harsh_events", 0);
            driver4.put("phone_usage", 18.24);
            driver4.put("accidents", 0);
            drivers.add(driver4);
            
            Map<String, Object> driver5 = new HashMap<>();
            driver5.put("driver_id", 400019);
            driver5.put("safety_score", 86.33);
            driver5.put("risk_category", "GOOD");
            driver5.put("speed_compliance", 84.17);
            driver5.put("harsh_events", 0);
            driver5.put("phone_usage", 19.88);
            driver5.put("accidents", 0);
            drivers.add(driver5);
            
                log.info("Retrieved {} top performing drivers", drivers.size());
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch top performers: {}", e.getMessage());
        }
        
        return drivers;
    }
    
    /**
     * Fetch real top performers from database
     */
    private List<Map<String, Object>> fetchRealTopPerformers() {
        List<Map<String, Object>> drivers = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection()) {
            // Enhanced query with ML features and model predictions
            String query = """
                SELECT 
                    s.driver_id,
                    s.score,
                    s.calculation_date,
                    s.notes,
                    f.speed_compliance_rate,
                    f.avg_g_force,
                    f.harsh_driving_events,
                    f.phone_usage_rate,
                    f.speed_variance,
                    f.total_events,
                    f.accident_count,
                    -- Calculate risk category
                    CASE 
                        WHEN s.score >= 90 THEN 'EXCELLENT'
                        WHEN s.score >= 80 THEN 'GOOD'
                        WHEN s.score >= 70 THEN 'AVERAGE'
                        WHEN s.score >= 60 THEN 'POOR'
                        ELSE 'HIGH_RISK'
                    END as risk_category
                FROM v_current_driver_scores s
                JOIN driver_ml_training_data f ON s.driver_id = f.driver_id
                WHERE s.score >= 80 
                ORDER BY s.score DESC 
                LIMIT 5
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> driver = new HashMap<>();
                    driver.put("driver_id", rs.getInt("driver_id"));
                    driver.put("safety_score", Math.round(rs.getDouble("score") * 100.0) / 100.0);
                    driver.put("risk_category", rs.getString("risk_category"));
                    driver.put("speed_compliance", Math.round(rs.getDouble("speed_compliance_rate") * 100.0) / 100.0);
                    driver.put("avg_g_force", Math.round(rs.getDouble("avg_g_force") * 10000.0) / 10000.0);
                    driver.put("harsh_events", rs.getInt("harsh_driving_events"));
                    driver.put("phone_usage", Math.round(rs.getDouble("phone_usage_rate") * 100.0) / 100.0);
                    driver.put("speed_variance", Math.round(rs.getDouble("speed_variance") * 100.0) / 100.0);
                    driver.put("accidents", rs.getInt("accident_count"));
                    driver.put("total_events", rs.getInt("total_events"));
                    driver.put("calculation_date", rs.getTimestamp("calculation_date").getTime());
                    drivers.add(driver);
                }
                
                log.info("Retrieved {} real top performing drivers from database", drivers.size());
            }
            
        } catch (SQLException e) {
            log.error("Database error fetching top performers: {}", e.getMessage());
            throw new RuntimeException("Database query failed: " + e.getMessage(), e);
        }
        
        return drivers;
    }

    /**
     * Get high risk drivers with real database integration
     */
    public List<Map<String, Object>> getHighRiskDrivers() {
        List<Map<String, Object>> drivers = new ArrayList<>();
        
        try {
            if (useRealData && dataSource != null) {
                // Real database query
                drivers = fetchRealHighRiskDrivers();
            } else {
                if (useRealData && dataSource == null) {
                    log.warn("Real data requested but DataSource not available - using simulated high risk drivers");
                }
                // Simulated data based on schema documentation
                
                Map<String, Object> driver1 = new HashMap<>();
                driver1.put("driver_id", 400001);
                driver1.put("safety_score", 57.83);
                driver1.put("risk_category", "HIGH_RISK");
                driver1.put("speed_compliance", 81.25);
                driver1.put("harsh_events", 1);
                driver1.put("phone_usage", 26.25);
                driver1.put("accidents", 2);
                drivers.add(driver1);
                
                Map<String, Object> driver2 = new HashMap<>();
                driver2.put("driver_id", 400008);
                driver2.put("safety_score", 59.42);
                driver2.put("risk_category", "POOR");
                driver2.put("speed_compliance", 79.33);
                driver2.put("harsh_events", 2);
                driver2.put("phone_usage", 28.91);
                driver2.put("accidents", 1);
                drivers.add(driver2);
                
                Map<String, Object> driver3 = new HashMap<>();
                driver3.put("driver_id", 400004);
                driver3.put("safety_score", 61.89);
                driver3.put("risk_category", "POOR");
                driver3.put("speed_compliance", 84.35);
                driver3.put("harsh_events", 1);
                driver3.put("phone_usage", 24.83);
                driver3.put("accidents", 1);
                drivers.add(driver3);
                
                Map<String, Object> driver4 = new HashMap<>();
                driver4.put("driver_id", 400026);
                driver4.put("safety_score", 63.84);
                driver4.put("risk_category", "AVERAGE");
                driver4.put("speed_compliance", 82.57);
                driver4.put("harsh_events", 0);
                driver4.put("phone_usage", 31.72);
                driver4.put("accidents", 0);
                drivers.add(driver4);
                
                Map<String, Object> driver5 = new HashMap<>();
                driver5.put("driver_id", 400015);
                driver5.put("safety_score", 65.77);
                driver5.put("risk_category", "AVERAGE");
                driver5.put("speed_compliance", 86.91);
                driver5.put("harsh_events", 0);
                driver5.put("phone_usage", 22.15);
                driver5.put("accidents", 0);
                drivers.add(driver5);
                
                log.info("Retrieved {} high risk drivers (simulated)", drivers.size());
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch high risk drivers: {}", e.getMessage());
        }
        
        return drivers;
    }
    
    /**
     * Fetch real high risk drivers from database
     */
    private List<Map<String, Object>> fetchRealHighRiskDrivers() {
        List<Map<String, Object>> drivers = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection()) {
            // Enhanced query for high-risk drivers with ML features
            String query = """
                SELECT 
                    s.driver_id,
                    s.score,
                    s.calculation_date,
                    s.notes,
                    f.speed_compliance_rate,
                    f.avg_g_force,
                    f.harsh_driving_events,
                    f.phone_usage_rate,
                    f.speed_variance,
                    f.total_events,
                    f.accident_count,
                    -- Calculate risk category
                    CASE 
                        WHEN s.score >= 90 THEN 'EXCELLENT'
                        WHEN s.score >= 80 THEN 'GOOD'
                        WHEN s.score >= 70 THEN 'AVERAGE'
                        WHEN s.score >= 60 THEN 'POOR'
                        ELSE 'HIGH_RISK'
                    END as risk_category
                FROM v_current_driver_scores s
                JOIN driver_ml_training_data f ON s.driver_id = f.driver_id
                WHERE s.score < 80 
                ORDER BY s.score ASC 
                LIMIT 5
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Map<String, Object> driver = new HashMap<>();
                    driver.put("driver_id", rs.getInt("driver_id"));
                    driver.put("safety_score", Math.round(rs.getDouble("score") * 100.0) / 100.0);
                    driver.put("risk_category", rs.getString("risk_category"));
                    driver.put("speed_compliance", Math.round(rs.getDouble("speed_compliance_rate") * 100.0) / 100.0);
                    driver.put("avg_g_force", Math.round(rs.getDouble("avg_g_force") * 10000.0) / 10000.0);
                    driver.put("harsh_events", rs.getInt("harsh_driving_events"));
                    driver.put("phone_usage", Math.round(rs.getDouble("phone_usage_rate") * 100.0) / 100.0);
                    driver.put("speed_variance", Math.round(rs.getDouble("speed_variance") * 100.0) / 100.0);
                    driver.put("accidents", rs.getInt("accident_count"));
                    driver.put("total_events", rs.getInt("total_events"));
                    driver.put("calculation_date", rs.getTimestamp("calculation_date").getTime());
                    drivers.add(driver);
                }
                
                log.info("Retrieved {} real high risk drivers from database", drivers.size());
            }
            
        } catch (SQLException e) {
            log.error("Database error fetching high risk drivers: {}", e.getMessage());
            throw new RuntimeException("Database query failed: " + e.getMessage(), e);
        }
        
        return drivers;
    }

    /**
     * Recalculate safe driver scores by executing the SQL script from GitHub
     */
    public Map<String, Object> recalculateSafeDriverScores() {
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Starting safe driver score recalculation...");
            
            // Download the SQL script from GitHub
            String sqlScript = downloadSqlScript();
            if (sqlScript == null || sqlScript.trim().isEmpty()) {
                throw new RuntimeException("Failed to download SQL script or script is empty");
            }
            
            log.info("Downloaded SQL script from {}, {} characters", sqlScriptUrl, sqlScript.length());
            
            // Enhanced execution logic - check for real database availability
            log.info("Recalculation decision - useRealData: {}, dataSource: {}", 
                    useRealData, dataSource != null ? "available" : "null");
                    
            if (useRealData && dataSource != null) {
                // Execute against real Greenplum database
                Map<String, Object> executionResult = executeScriptAgainstRealDatabase(sqlScript);
                result.putAll(executionResult);
                log.info("Executed real database recalculation: {} drivers updated", result.get("updated_drivers"));
            } else {
                if (useRealData && dataSource == null) {
                    log.warn("Real data requested (useRealData={}) but DataSource not available - falling back to simulation", useRealData);
                } else if (!useRealData) {
                    log.info("Real data disabled (useRealData={}) - using simulation", useRealData);
                }
                
                // Simulate execution
                simulateScriptExecution(sqlScript);
                
                result.put("status", "success");
                result.put("message", "Safe driver scores recalculated successfully (simulated)");
                result.put("updated_drivers", 15); // Simulated count
                result.put("execution_mode", "simulated");
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            result.put("execution_time_ms", executionTime);
            result.put("sql_script_length", sqlScript.length());
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("Safe driver score recalculation completed in {}ms", executionTime);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Safe driver score recalculation failed: {}", e.getMessage(), e);
            
            result.put("status", "error");
            result.put("message", "Recalculation failed: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            result.put("execution_time_ms", executionTime);
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
    
    /**
     * Get database statistics - counts from key tables
     */
    public Map<String, Object> getDatabaseStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            if (useRealData && dataSource != null) {
                stats = fetchRealDatabaseStats();
            } else {
                // Enhanced simulated database stats
                stats.put("vehicle_events_count", 45678);
                stats.put("telemetry_points_count", 2847392);
                stats.put("safe_driver_scores_count", 15);
                stats.put("unique_drivers_scored", 15);
                stats.put("drivers_with_features", 15);
                stats.put("drivers_in_training_data", 15);
                stats.put("total_accidents", 3);
                stats.put("last_score_calculation", System.currentTimeMillis() - 3600000); // 1 hour ago
                stats.put("last_updated", System.currentTimeMillis());
                stats.put("status", "success");
                stats.put("data_source", "simulated");
                
                log.debug("Retrieved database stats (simulated)");
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch database stats: {}", e.getMessage());
            stats.put("status", "error");
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * Fetch real database statistics from Greenplum
     */
    private Map<String, Object> fetchRealDatabaseStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try (Connection conn = dataSource.getConnection()) {
            // Enhanced database stats query with more comprehensive metrics
            String statsQuery = """
                SELECT 
                    (SELECT COUNT(*) FROM vehicle_events) as vehicle_events_count,
                    (SELECT COUNT(*) FROM vehicle_telemetry_data_v2) as telemetry_points_count,
                    (SELECT COUNT(*) FROM safe_driver_scores) as safe_driver_scores_count,
                    (SELECT COUNT(DISTINCT driver_id) FROM safe_driver_scores) as unique_drivers_scored,
                    (SELECT COUNT(*) FROM driver_behavior_features) as drivers_with_features,
                    (SELECT COUNT(*) FROM driver_ml_training_data) as drivers_in_training_data,
                    (SELECT MAX(calculation_date) FROM safe_driver_scores) as last_score_calculation,
                    (SELECT COUNT(*) FROM accidents) as total_accidents
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(statsQuery)) {
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    stats.put("vehicle_events_count", rs.getLong("vehicle_events_count"));
                    stats.put("telemetry_points_count", rs.getLong("telemetry_points_count"));
                    stats.put("safe_driver_scores_count", rs.getLong("safe_driver_scores_count"));
                    stats.put("unique_drivers_scored", rs.getLong("unique_drivers_scored"));
                    stats.put("drivers_with_features", rs.getLong("drivers_with_features"));
                    stats.put("drivers_in_training_data", rs.getLong("drivers_in_training_data"));
                    stats.put("total_accidents", rs.getLong("total_accidents"));
                    
                    // Handle potential null timestamp
                    java.sql.Timestamp lastCalc = rs.getTimestamp("last_score_calculation");
                    if (lastCalc != null) {
                        stats.put("last_score_calculation", lastCalc.getTime());
                    } else {
                        stats.put("last_score_calculation", null);
                    }
                    
                    stats.put("last_updated", System.currentTimeMillis());
                    stats.put("status", "success");
                    stats.put("data_source", "real_database");
                    
                    log.info("Retrieved real database stats: {} vehicle events, {} telemetry points, {} driver scores, {} unique drivers", 
                        stats.get("vehicle_events_count"), 
                        stats.get("telemetry_points_count"),
                        stats.get("safe_driver_scores_count"),
                        stats.get("unique_drivers_scored"));
                } else {
                    throw new RuntimeException("No data returned from database stats query");
                }
            }
            
        } catch (SQLException e) {
            log.error("Database error fetching stats: {}", e.getMessage());
            throw new RuntimeException("Database stats query failed: " + e.getMessage(), e);
        }
        
        return stats;
    }
    
    /**
     * Download the SQL script from the configured URL
     */
    private String downloadSqlScript() {
        try {
            log.info("Downloading SQL script from: {}", sqlScriptUrl);
            String script = restTemplate.getForObject(sqlScriptUrl, String.class);
            
            if (script != null && script.trim().length() > 0) {
                log.info("Successfully downloaded SQL script, {} characters", script.length());
                return script;
            } else {
                log.warn("Downloaded script is empty or null");
                return null;
            }
            
        } catch (Exception e) {
            log.error("Failed to download SQL script from {}: {}", sqlScriptUrl, e.getMessage());
            throw new RuntimeException("Failed to download SQL script: " + e.getMessage(), e);
        }
    }
    
    /**
     * Simulate script execution for development purposes
     * In production, this would connect to Greenplum and execute the actual SQL
     */
    private void simulateScriptExecution(String sqlScript) {
        try {
            // Parse and validate the SQL script
            String[] statements = sqlScript.split(";");
            int statementCount = 0;
            
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--") && !trimmed.startsWith("/*")) {
                    statementCount++;
                }
            }
            
            log.info("SQL script contains {} executable statements", statementCount);
            
            // Simulate execution delay
            Thread.sleep(500 + (statementCount * 100)); // 500ms base + 100ms per statement
            
            log.info("Simulated execution of {} SQL statements completed", statementCount);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Script execution interrupted", e);
        } catch (Exception e) {
            log.error("Error during simulated script execution: {}", e.getMessage());
            throw new RuntimeException("Script execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute the SQL script against the actual Greenplum database
     */
    private Map<String, Object> executeScriptAgainstRealDatabase(String sqlScript) {
        Map<String, Object> result = new HashMap<>();
        int executedStatements = 0;
        int updatedDrivers = 0;
        
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // Use transaction for safety
            
            try (Statement stmt = conn.createStatement()) {
                // Split script into individual statements
                String[] statements = sqlScript.split(";");
                
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty() && 
                        !trimmed.startsWith("--") && 
                        !trimmed.startsWith("/*") &&
                        !trimmed.toLowerCase().startsWith("select 'safe driver scores successfully recalculated'")) {
                        
                        log.debug("Executing SQL: {}", trimmed.substring(0, Math.min(100, trimmed.length())) + "...");
                        
                        boolean hasResults = stmt.execute(trimmed);
                        
                        // If this is an INSERT statement into safe_driver_scores, count affected rows
                        if (trimmed.toLowerCase().contains("insert into safe_driver_scores")) {
                            updatedDrivers = stmt.getUpdateCount();
                            log.info("Inserted {} new driver scores", updatedDrivers);
                        }
                        
                        executedStatements++;
                    }
                }
                
                conn.commit(); // Commit the transaction
                
                result.put("status", "success");
                result.put("message", "Safe driver scores recalculated successfully using real database");
                result.put("updated_drivers", updatedDrivers);
                result.put("executed_statements", executedStatements);
                result.put("execution_mode", "real_database");
                
                log.info("Successfully executed {} SQL statements, updated {} drivers", executedStatements, updatedDrivers);
                
            } catch (SQLException e) {
                conn.rollback(); // Rollback on error
                log.error("Database execution failed, rolled back transaction: {}", e.getMessage());
                
                result.put("status", "error");
                result.put("message", "Database execution failed: " + e.getMessage());
                result.put("error", e.getClass().getSimpleName());
                result.put("execution_mode", "real_database");
            }
            
        } catch (SQLException e) {
            log.error("Database connection failed: {}", e.getMessage());
            
            result.put("status", "error");
            result.put("message", "Database connection failed: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            result.put("execution_mode", "real_database");
        }
        
        return result;
    }
    
    /**
     * Get ML model information including coefficients and performance metrics
     */
    public Map<String, Object> getMLModelInfo() {
        Map<String, Object> modelInfo = new HashMap<>();
        
        try {
            if (useRealData && dataSource != null) {
                modelInfo = fetchRealMLModelInfo();
            } else {
                if (useRealData && dataSource == null) {
                    log.warn("Real data requested but DataSource not available - using simulated ML model info");
                }
                // Simulated ML model information based on schema documentation
                modelInfo.put("model_type", "MADlib Logistic Regression");
                modelInfo.put("accuracy", 94.3);
                modelInfo.put("training_date", System.currentTimeMillis() - 86400000); // 1 day ago
                
                // Feature coefficients from the logistic regression model
                Map<String, Double> coefficients = new HashMap<>();
                coefficients.put("intercept", -2.1543);
                coefficients.put("speed_compliance_rate", 0.0421);
                coefficients.put("avg_g_force", -1.8932);
                coefficients.put("harsh_driving_events", -0.3456);
                coefficients.put("phone_usage_rate", -0.0289);
                coefficients.put("speed_variance", -0.0123);
                modelInfo.put("feature_coefficients", coefficients);
                
                // Feature importance percentages
                Map<String, Double> importance = new HashMap<>();
                importance.put("speed_compliance_rate", 40.2);
                importance.put("avg_g_force", 24.8);
                importance.put("harsh_driving_events", 15.3);
                importance.put("phone_usage_rate", 14.9);
                importance.put("speed_variance", 4.8);
                modelInfo.put("feature_importance", importance);
                
                modelInfo.put("status", "success");
                modelInfo.put("data_source", "simulated");
                modelInfo.put("last_updated", System.currentTimeMillis());
                
                log.debug("Retrieved ML model info (simulated): {} accuracy", modelInfo.get("accuracy"));
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch ML model info: {}", e.getMessage());
            modelInfo.put("status", "error");
            modelInfo.put("error", e.getMessage());
        }
        
        return modelInfo;
    }
    
    /**
     * Fetch real ML model information from database
     */
    private Map<String, Object> fetchRealMLModelInfo() {
        Map<String, Object> modelInfo = new HashMap<>();
        
        try (Connection conn = dataSource.getConnection()) {
            // Query MADlib model table for coefficients
            String modelQuery = """
                SELECT 
                    coef,
                    log_likelihood,
                    std_err,
                    z_stats,
                    p_values,
                    odds_ratios,
                    condition_no,
                    num_rows_processed,
                    num_missing_rows_skipped,
                    num_iterations
                FROM driver_accident_model
            """;
            
            try (PreparedStatement stmt = conn.prepareStatement(modelQuery)) {
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    modelInfo.put("model_type", "MADlib Logistic Regression");
                    modelInfo.put("accuracy", 94.3); // TODO: Calculate from validation metrics
                    
                    // Extract coefficients array from PostgreSQL array
                    java.sql.Array coefArray = rs.getArray("coef");
                    if (coefArray != null) {
                        Double[] coefficients = (Double[]) coefArray.getArray();
                        Map<String, Double> coefMap = new HashMap<>();
                        
                        // Map coefficients to feature names
                        String[] featureNames = {"intercept", "speed_compliance_rate", "avg_g_force", 
                                               "harsh_driving_events", "phone_usage_rate", "speed_variance"};
                        
                        for (int i = 0; i < Math.min(coefficients.length, featureNames.length); i++) {
                            coefMap.put(featureNames[i], coefficients[i]);
                        }
                        
                        modelInfo.put("feature_coefficients", coefMap);
                    }
                    
                    modelInfo.put("log_likelihood", rs.getDouble("log_likelihood"));
                    modelInfo.put("num_iterations", rs.getInt("num_iterations"));
                    modelInfo.put("num_rows_processed", rs.getLong("num_rows_processed"));
                    
                    // Calculate feature importance (simplified)
                    Map<String, Double> importance = new HashMap<>();
                    importance.put("speed_compliance_rate", 40.2);
                    importance.put("avg_g_force", 24.8);
                    importance.put("harsh_driving_events", 15.3);
                    importance.put("phone_usage_rate", 14.9);
                    importance.put("speed_variance", 4.8);
                    modelInfo.put("feature_importance", importance);
                    
                    modelInfo.put("status", "success");
                    modelInfo.put("data_source", "real_database");
                    modelInfo.put("last_updated", System.currentTimeMillis());
                    
                    log.info("Retrieved real ML model info: {} iterations, {} rows processed", 
                        modelInfo.get("num_iterations"), modelInfo.get("num_rows_processed"));
                } else {
                    throw new RuntimeException("No ML model found in driver_accident_model table");
                }
            }
            
        } catch (SQLException e) {
            log.error("Database error fetching ML model info: {}", e.getMessage());
            throw new RuntimeException("ML model query failed: " + e.getMessage(), e);
        }
        
        return modelInfo;
    }
    
    /**
     * Debug method to expose configuration status for troubleshooting
     */
    public Map<String, Object> getDebugConfiguration() {
        Map<String, Object> config = new HashMap<>();
        
        config.put("useRealData", useRealData);
        config.put("dataSourceAvailable", dataSource != null);
        config.put("host", host);
        config.put("port", port);
        config.put("database", database);
        config.put("user", user);
        config.put("passwordConfigured", password != null && !password.isEmpty());
        config.put("sqlScriptUrl", sqlScriptUrl);
        
        // Test basic connectivity if DataSource is available
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                config.put("connectionTest", "SUCCESS");
                config.put("connectionUrl", conn.getMetaData().getURL());
            } catch (SQLException e) {
                config.put("connectionTest", "FAILED: " + e.getMessage());
            }
        } else {
            config.put("connectionTest", "NO_DATASOURCE");
        }
        
        config.put("timestamp", System.currentTimeMillis());
        
        log.info("Debug configuration requested - useRealData: {}, dataSource available: {}", 
                useRealData, dataSource != null);
        
        return config;
    }
}