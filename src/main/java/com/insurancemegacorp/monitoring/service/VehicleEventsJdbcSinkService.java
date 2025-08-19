package com.insurancemegacorp.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class VehicleEventsJdbcSinkService {

    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;
    private final String serviceName = "imc-jdbc-consumer";
    
    // Metric patterns to try for rows inserted (ordered by priority)
    private static final String[] ROWS_INSERTED_PATTERNS = {
        "jdbc_consumer_messages_processed_total", // New custom metric from IMC JDBC Consumer
        "rabbitmq_consumed_total", // Messages consumed from RabbitMQ
        "jdbc_sink_rows_inserted_total",
        "spring_data_repository_invocations_total", 
        "sink_records_sent_total",
        "sink_records_processed_total",
        "spring_integration_sends_total",
        "spring_integration_receives_total",
        "application_processed_total",
        "application_records_total",
        "kafka_consumer_records_consumed_total",
        "micrometer_counter_total", // Generic micrometer counter
        "jvm_threads_peak", // Any activity metric as fallback
        "process_uptime_seconds" // Process activity as last resort
    };
    
    // Metric patterns to try for database errors (ordered by priority)
    private static final String[] DATABASE_ERROR_PATTERNS = {
        "jdbc_sink_errors_total",
        "jdbc_connections_failed_total",
        "sink_records_failed_total", 
        "spring_integration_errors_total",
        "application_errors_total",
        "spring_data_repository_exceptions_total",
        "hikaricp_connections_timeout_total",
        "hikaricp_connections_failed_total",
        "logback_events_total", // Error log events
        "jvm_gc_pause_seconds" // GC pressure could indicate issues
    };

    @Autowired
    public VehicleEventsJdbcSinkService(RestTemplate restTemplate,
                                      DiscoveryClient discoveryClient) {
        this.restTemplate = restTemplate;
        this.discoveryClient = discoveryClient;
    }

    /**
     * Get JDBC sink metrics (rows inserted and database errors)
     */
    public Map<String, Object> getJdbcSinkMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            Optional<String> serviceUrl = getServiceUrl();
            
            if (serviceUrl.isEmpty()) {
                log.error("No healthy instances found for service: {}. Check if SCDF JDBC sink is running and registered.", serviceName);
                return createErrorMetrics("Service not available - check if " + serviceName + " is running and registered");
            }
            
            log.info("Connecting to JDBC sink service at: {}", serviceUrl.get());
            
            // Use Prometheus endpoint for JDBC sink metrics
            String prometheusUrl = serviceUrl.get() + "/actuator/prometheus";
            
            // Get Prometheus metrics
            Map<String, Double> prometheusMetrics = getPrometheusMetrics(prometheusUrl);
            
            // If we got metrics but no specific ones we're looking for, log available metrics
            if (!prometheusMetrics.isEmpty()) {
                log.info("Found {} Prometheus metrics from JDBC sink. Sample metrics: {}", 
                    prometheusMetrics.size(), 
                    prometheusMetrics.keySet().stream().limit(10).toList());
            }
            
            // Extract JDBC-specific metrics - try multiple patterns
            double rowsInserted = findBestMetricMatch(prometheusMetrics, ROWS_INSERTED_PATTERNS);
            double databaseErrors = findBestMetricMatch(prometheusMetrics, DATABASE_ERROR_PATTERNS);
            
            // Get the specific metrics the UI expects
            double jdbcConsumerProcessed = prometheusMetrics.getOrDefault("jdbc_consumer_messages_processed_total", 0.0);
            double rabbitMQConsumed = prometheusMetrics.getOrDefault("rabbitmq_consumed_total", 0.0);
            
            metrics.put("rows_inserted", Math.round(rowsInserted));
            metrics.put("database_errors", Math.round(databaseErrors));
            metrics.put("jdbc_consumer_messages_processed_total", Math.round(jdbcConsumerProcessed));
            metrics.put("rabbitmq_consumed_total", Math.round(rabbitMQConsumed));
            metrics.put("service_url", serviceUrl.get());
            metrics.put("status", "healthy");
            metrics.put("available_metrics_count", prometheusMetrics.size());
            metrics.put("timestamp", System.currentTimeMillis());
            
            log.info("Retrieved JDBC sink metrics from {}: rows={}, errors={}, total_metrics={}", 
                serviceName, Math.round(rowsInserted), Math.round(databaseErrors), prometheusMetrics.size());
            
        } catch (Exception e) {
            log.error("Failed to fetch JDBC sink metrics from {}: {}", serviceName, e.getMessage());
            return createErrorMetrics("Failed to fetch metrics: " + e.getMessage());
        }
        
        return metrics;
    }
    
    /**
     * Get health status of the JDBC sink service
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            Optional<String> serviceUrl = getServiceUrl();
            
            if (serviceUrl.isEmpty()) {
                return createHealthStatus(false, "No instances available");
            }
            
            String healthUrl = serviceUrl.get() + "/actuator/health";
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
                healthUrl, 
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> healthInfo = response.getBody();
                String status = (String) healthInfo.get("status");
                boolean isHealthy = "UP".equalsIgnoreCase(status);
                
                health.put("healthy", isHealthy);
                health.put("status", status);
                health.put("service_url", serviceUrl.get());
                health.put("details", healthInfo);
                health.put("timestamp", System.currentTimeMillis());
                
                log.debug("Health check for {}: {}", serviceName, status);
                
            } else {
                return createHealthStatus(false, "Health endpoint returned: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.warn("Health check failed for {}: {}", serviceName, e.getMessage());
            return createHealthStatus(false, "Health check failed: " + e.getMessage());
        }
        
        return health;
    }
    
    /**
     * Debug method to get raw Prometheus output
     */
    public String getRawPrometheusMetrics() {
        try {
            Optional<String> serviceUrl = getServiceUrl();
            
            if (serviceUrl.isEmpty()) {
                return "ERROR: No service URL available for " + serviceName + "\n" +
                       "Available services: " + discoveryClient.getServices() + "\n" +
                       "Service discovery may not be working or JDBC sink not registered.";
            }
            
            String prometheusUrl = serviceUrl.get() + "/actuator/prometheus";
            ResponseEntity<String> response = restTemplate.getForEntity(prometheusUrl, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String rawMetrics = response.getBody();
                
                // Parse and show summary
                String[] lines = rawMetrics.split("\n");
                Map<String, Double> parsedMetrics = new HashMap<>();
                
                for (String line : lines) {
                    if (!line.startsWith("#") && line.trim().length() > 0) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            try {
                                parsedMetrics.put(parts[0], Double.parseDouble(parts[parts.length - 1]));
                            } catch (NumberFormatException ignored) {
                                // Skip invalid metrics
                            }
                        }
                    }
                }
                
                StringBuilder summary = new StringBuilder();
                summary.append("=== JDBC Sink Prometheus Metrics Summary ===\n");
                summary.append("Service URL: ").append(serviceUrl.get()).append("\n");
                summary.append("Total metrics found: ").append(parsedMetrics.size()).append("\n\n");
                
                // Show rows inserted pattern matches
                summary.append("=== Potential Rows Inserted Metrics ===\n");
                for (String pattern : ROWS_INSERTED_PATTERNS) {
                    if (parsedMetrics.containsKey(pattern)) {
                        summary.append("✅ EXACT: ").append(pattern).append(" = ").append(parsedMetrics.get(pattern)).append("\n");
                    } else {
                        // Check for partial matches
                        String basePattern = pattern.replace("_total", "");
                        for (String metricName : parsedMetrics.keySet()) {
                            if (metricName.contains(basePattern)) {
                                summary.append("🔍 PARTIAL: ").append(metricName).append(" = ").append(parsedMetrics.get(metricName)).append(" (matches ").append(pattern).append(")\n");
                                break;
                            }
                        }
                    }
                }
                
                // Show database error pattern matches
                summary.append("\n=== Potential Database Error Metrics ===\n");
                for (String pattern : DATABASE_ERROR_PATTERNS) {
                    if (parsedMetrics.containsKey(pattern)) {
                        summary.append("✅ EXACT: ").append(pattern).append(" = ").append(parsedMetrics.get(pattern)).append("\n");
                    } else {
                        // Check for partial matches
                        String basePattern = pattern.replace("_total", "");
                        for (String metricName : parsedMetrics.keySet()) {
                            if (metricName.contains(basePattern)) {
                                summary.append("🔍 PARTIAL: ").append(metricName).append(" = ").append(parsedMetrics.get(metricName)).append(" (matches ").append(pattern).append(")\n");
                                break;
                            }
                        }
                    }
                }
                
                // Show first 20 available metrics for reference
                summary.append("\n=== Sample Available Metrics (first 20) ===\n");
                parsedMetrics.entrySet().stream()
                    .limit(20)
                    .forEach(entry -> summary.append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n"));
                
                summary.append("\n=== Raw Prometheus Output ===\n");
                summary.append(rawMetrics);
                
                return summary.toString();
                
            } else {
                return "ERROR: Failed to fetch Prometheus metrics, status: " + response.getStatusCode();
            }
            
        } catch (Exception e) {
            return "ERROR: Exception fetching Prometheus metrics: " + e.getMessage();
        }
    }
    
    private Optional<String> getServiceUrl() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            
            if (instances == null || instances.isEmpty()) {
                log.warn("No instances found for service {}. Available services: {}", serviceName, discoveryClient.getServices());
                return Optional.empty();
            }
            
            // Use the first available instance
            ServiceInstance instance = instances.get(0);
            String serviceUrl = instance.getUri().toString();
            log.info("Found service instance for {}: {} ({}:{})", serviceName, serviceUrl, instance.getHost(), instance.getPort());
            return Optional.of(serviceUrl);
            
        } catch (Exception e) {
            log.error("Failed to discover service {}: {}", serviceName, e.getMessage());
            return Optional.empty();
        }
    }
    
    private Map<String, Double> getPrometheusMetrics(String prometheusUrl) {
        Map<String, Double> metrics = new HashMap<>();
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(prometheusUrl, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String prometheusText = response.getBody();
                
                // Parse Prometheus text format for JDBC sink metrics
                String[] lines = prometheusText.split("\n");
                log.info("Parsing {} lines from JDBC sink Prometheus endpoint", lines.length);
                
                for (String line : lines) {
                    line = line.trim();
                    
                    // Skip comments
                    if (line.startsWith("#")) continue;
                    
                    // Look for various metrics that could indicate rows inserted
                    if (line.startsWith("jdbc_consumer_messages_processed_total")) {
                        parseMetricLine(line, "jdbc_consumer_messages_processed_total", metrics);
                    }
                    if (line.startsWith("rabbitmq_consumed_total")) {
                        parseMetricLine(line, "rabbitmq_consumed_total", metrics);
                    }
                    if (line.startsWith("jdbc_sink_rows_inserted_total")) {
                        parseMetricLine(line, "jdbc_sink_rows_inserted_total", metrics);
                    }
                    if (line.startsWith("spring_data_repository_invocations_total")) {
                        parseMetricLine(line, "spring_data_repository_invocations_total", metrics);
                    }
                    if (line.startsWith("sink_records_sent_total")) {
                        parseMetricLine(line, "sink_records_sent_total", metrics);
                    }
                    if (line.startsWith("sink_records_processed_total")) {
                        parseMetricLine(line, "sink_records_processed_total", metrics);
                    }
                    if (line.startsWith("spring_integration_sends_total")) {
                        parseMetricLine(line, "spring_integration_sends_total", metrics);
                    }
                    if (line.startsWith("spring_integration_receives_total")) {
                        parseMetricLine(line, "spring_integration_receives_total", metrics);
                    }
                    
                    // Look for various metrics that could indicate errors
                    if (line.startsWith("jdbc_sink_errors_total")) {
                        parseMetricLine(line, "jdbc_sink_errors_total", metrics);
                    }
                    if (line.startsWith("jdbc_connections_failed_total")) {
                        parseMetricLine(line, "jdbc_connections_failed_total", metrics);
                    }
                    if (line.startsWith("sink_records_failed_total")) {
                        parseMetricLine(line, "sink_records_failed_total", metrics);
                    }
                    if (line.startsWith("spring_integration_errors_total")) {
                        parseMetricLine(line, "spring_integration_errors_total", metrics);
                    }
                    if (line.startsWith("application_errors_total")) {
                        parseMetricLine(line, "application_errors_total", metrics);
                    }
                    if (line.startsWith("hikaricp_connections_usage")) {
                        parseMetricLine(line, "hikaricp_connections_usage", metrics);
                    }
                }
                
                log.info("Final parsed JDBC sink metrics: {}", metrics);
                
            } else {
                log.warn("Failed to fetch JDBC sink Prometheus metrics, status: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch JDBC sink Prometheus metrics: {}", e.getMessage());
        }
        
        return metrics;
    }
    
    private void parseMetricLine(String line, String metricName, Map<String, Double> metrics) {
        try {
            log.info("Found {} line: {}", metricName, line);
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                String valueStr = parts[parts.length - 1]; // Take the last part as the value
                double value = Double.parseDouble(valueStr);
                metrics.put(metricName, value);
                log.info("Parsed {}: {}", metricName, value);
            } else {
                log.warn("Invalid format for {} line: {}", metricName, line);
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse {} value from line: {}", metricName, line);
        }
    }
    
    private Map<String, Object> createErrorMetrics(String error) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("rows_inserted", 0L);
        metrics.put("database_errors", 0L);
        metrics.put("jdbc_consumer_messages_processed_total", 0L);
        metrics.put("rabbitmq_consumed_total", 0L);
        metrics.put("status", "error");
        metrics.put("error", error);
        metrics.put("timestamp", System.currentTimeMillis());
        return metrics;
    }
    
    private Map<String, Object> createHealthStatus(boolean healthy, String message) {
        Map<String, Object> health = new HashMap<>();
        health.put("healthy", healthy);
        health.put("status", healthy ? "UP" : "DOWN");
        health.put("message", message);
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }
    
    /**
     * Find the best matching metric from available patterns
     */
    private double findBestMetricMatch(Map<String, Double> availableMetrics, String[] patterns) {
        for (String pattern : patterns) {
            // First try exact match
            if (availableMetrics.containsKey(pattern)) {
                double value = availableMetrics.get(pattern);
                log.info("Found exact metric match: {} = {}", pattern, value);
                return value;
            }
            
            // Then try partial match (metrics containing the pattern)
            for (Map.Entry<String, Double> entry : availableMetrics.entrySet()) {
                if (entry.getKey().contains(pattern.replace("_total", ""))) {
                    double value = entry.getValue();
                    log.info("Found partial metric match: {} matches pattern {} = {}", entry.getKey(), pattern, value);
                    return value;
                }
            }
        }
        
        // No matches found
        log.warn("No metrics found matching patterns: {}", String.join(", ", patterns));
        return 0.0;
    }
}