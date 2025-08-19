package com.insurancemegacorp.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TelemetryProcessorMetricsService {

    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;
    private final String serviceName = "imc-telemetry-processor";

    @Autowired
    public TelemetryProcessorMetricsService(RestTemplate restTemplate,
                                          DiscoveryClient discoveryClient) {
        this.restTemplate = restTemplate;
        this.discoveryClient = discoveryClient;
    }

    /**
     * Get telemetry processor metrics (messages in, events detected, messages out)
     * Aggregates metrics from all available instances
     */
    public Map<String, Object> getProcessorMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            List<String> serviceUrls = getAllServiceUrls();
            
            if (serviceUrls.isEmpty()) {
                log.warn("No healthy instances found for service: {}. Using fallback calculation.", serviceName);
                return createFallbackMetrics();
            }
            
            log.debug("Found {} instances of {}, aggregating metrics from all instances", serviceUrls.size(), serviceName);
            
            // Aggregate metrics from all instances
            double totalMessages = 0.0;
            double vehicleEvents = 0.0;
            double invalidMessages = 0.0;
            int successfulInstances = 0;
            
            for (String serviceUrl : serviceUrls) {
                try {
                    // Use Prometheus endpoint for processor metrics
                    String prometheusUrl = serviceUrl + "/actuator/prometheus";
                    Map<String, Double> instanceMetrics = getPrometheusMetrics(prometheusUrl);
                    
                    // Sum metrics from this instance
                    totalMessages += instanceMetrics.getOrDefault("telemetry_messages_total", 0.0);
                    vehicleEvents += instanceMetrics.getOrDefault("telemetry_vehicle_events_total", 0.0);
                    invalidMessages += instanceMetrics.getOrDefault("telemetry_invalid_messages_total", 0.0);
                    
                    successfulInstances++;
                    log.debug("Successfully retrieved metrics from instance: {}", serviceUrl);
                    
                } catch (Exception e) {
                    log.warn("Failed to get metrics from instance {}: {}", serviceUrl, e.getMessage());
                }
            }
            
            if (successfulInstances == 0) {
                log.error("Failed to retrieve metrics from any instances of {}", serviceName);
                return createFallbackMetrics();
            }
            
            // Build aggregated metrics
            metrics.put("messages_in", Math.round(totalMessages));
            metrics.put("events_captured", Math.round(vehicleEvents)); // This is the key metric for "events detected"
            metrics.put("messages_out", Math.round(totalMessages - invalidMessages)); // Valid processed messages
            metrics.put("invalid_messages", Math.round(invalidMessages));
            metrics.put("total_instances", serviceUrls.size());
            metrics.put("successful_instances", successfulInstances);
            metrics.put("status", "healthy");
            metrics.put("timestamp", System.currentTimeMillis());
            
            log.debug("Aggregated telemetry processor metrics from {} instances: messages_in={}, events_captured={}, messages_out={}, invalid={}", 
                successfulInstances, Math.round(totalMessages), Math.round(vehicleEvents), Math.round(totalMessages - invalidMessages), Math.round(invalidMessages));
            
        } catch (Exception e) {
            log.error("Failed to fetch telemetry processor metrics from {}: {}", serviceName, e.getMessage());
            return createFallbackMetrics();
        }
        
        return metrics;
    }
    
    /**
     * Get health status of the telemetry processor service
     * Checks all instances and reports aggregated health
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            List<String> serviceUrls = getAllServiceUrls();
            
            if (serviceUrls.isEmpty()) {
                return createHealthStatus(false, "No instances available");
            }
            
            int healthyInstances = 0;
            int totalInstances = serviceUrls.size();
            List<Map<String, Object>> instanceDetails = new ArrayList<>();
            
            for (String serviceUrl : serviceUrls) {
                try {
                    String healthUrl = serviceUrl + "/actuator/health";
                    @SuppressWarnings("unchecked")
                    ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
                        healthUrl, 
                        (Class<Map<String, Object>>) (Class<?>) Map.class
                    );
                    
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        Map<String, Object> healthInfo = response.getBody();
                        String status = (String) healthInfo.get("status");
                        boolean isHealthy = "UP".equalsIgnoreCase(status);
                        
                        if (isHealthy) {
                            healthyInstances++;
                        }
                        
                        // Add instance details
                        Map<String, Object> instanceDetail = new HashMap<>();
                        instanceDetail.put("service_url", serviceUrl);
                        instanceDetail.put("status", status);
                        instanceDetail.put("healthy", isHealthy);
                        instanceDetails.add(instanceDetail);
                        
                        log.debug("Health check for {} instance {}: {}", serviceName, serviceUrl, status);
                    }
                    
                } catch (Exception e) {
                    log.warn("Health check failed for {} instance {}: {}", serviceName, serviceUrl, e.getMessage());
                    
                    // Add failed instance details
                    Map<String, Object> instanceDetail = new HashMap<>();
                    instanceDetail.put("service_url", serviceUrl);
                    instanceDetail.put("status", "DOWN");
                    instanceDetail.put("healthy", false);
                    instanceDetail.put("error", e.getMessage());
                    instanceDetails.add(instanceDetail);
                }
            }
            
            // Service is considered healthy if at least one instance is healthy
            boolean overallHealthy = healthyInstances > 0;
            String overallStatus = healthyInstances == totalInstances ? "UP" : 
                                 healthyInstances > 0 ? "PARTIAL" : "DOWN";
            
            health.put("healthy", overallHealthy);
            health.put("status", overallStatus);
            health.put("total_instances", totalInstances);
            health.put("healthy_instances", healthyInstances);
            health.put("instance_details", instanceDetails);
            health.put("timestamp", System.currentTimeMillis());
            
            log.debug("Overall health for {}: {} ({}/{} instances healthy)", serviceName, overallStatus, healthyInstances, totalInstances);
            
        } catch (Exception e) {
            log.warn("Health check failed for {}: {}", serviceName, e.getMessage());
            return createHealthStatus(false, "Health check failed: " + e.getMessage());
        }
        
        return health;
    }
    
    /**
     * Get all service URLs for instances of the telemetry processor
     */
    private List<String> getAllServiceUrls() {
        List<String> serviceUrls = new ArrayList<>();
        
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            
            if (instances == null || instances.isEmpty()) {
                log.debug("No instances found for service {}. Available services: {}", serviceName, discoveryClient.getServices());
                return serviceUrls;
            }
            
            // Get all available instances
            for (ServiceInstance instance : instances) {
                String serviceUrl = instance.getUri().toString();
                serviceUrls.add(serviceUrl);
                log.debug("Found service instance for {}: {} ({}:{})", serviceName, serviceUrl, instance.getHost(), instance.getPort());
            }
            
            log.debug("Found {} instances of {}", serviceUrls.size(), serviceName);
            
        } catch (Exception e) {
            log.error("Failed to discover service {}: {}", serviceName, e.getMessage());
        }
        
        return serviceUrls;
    }
    
    
    private Map<String, Double> getPrometheusMetrics(String prometheusUrl) {
        Map<String, Double> metrics = new HashMap<>();
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(prometheusUrl, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String prometheusText = response.getBody();
                
                // Parse Prometheus text format for processor metrics
                String[] lines = prometheusText.split("\n");
                log.debug("Parsing {} lines from telemetry processor Prometheus endpoint", lines.length);
                
                for (String line : lines) {
                    line = line.trim();
                    
                    // Skip comments
                    if (line.startsWith("#")) continue;
                    
                    // Look for telemetry processor specific metrics
                    if (line.startsWith("telemetry_messages_total")) {
                        parseMetricLine(line, "telemetry_messages_total", metrics);
                    }
                    if (line.startsWith("telemetry_vehicle_events_total")) {
                        parseMetricLine(line, "telemetry_vehicle_events_total", metrics);
                    }
                    if (line.startsWith("telemetry_invalid_messages_total")) {
                        parseMetricLine(line, "telemetry_invalid_messages_total", metrics);
                    }
                }
                
                log.debug("Parsed telemetry processor metrics: {}", metrics);
                
            } else {
                log.warn("Failed to fetch telemetry processor Prometheus metrics, status: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch telemetry processor Prometheus metrics: {}", e.getMessage());
        }
        
        return metrics;
    }
    
    private void parseMetricLine(String line, String metricName, Map<String, Double> metrics) {
        try {
            log.debug("Found {} line: {}", metricName, line);
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                String valueStr = parts[parts.length - 1]; // Take the last part as the value
                double value = Double.parseDouble(valueStr);
                
                // For the tagged telemetry_messages_total, extract the total value
                if (metricName.equals("telemetry_messages_total")) {
                    // Sum all binding values if there are multiple
                    Double existingValue = metrics.get(metricName);
                    value = (existingValue != null ? existingValue : 0.0) + value;
                }
                
                metrics.put(metricName, value);
                log.debug("Parsed {}: {}", metricName, value);
            } else {
                log.warn("Invalid format for {} line: {}", metricName, line);
            }
        } catch (NumberFormatException e) {
            log.warn("Could not parse {} value from line: {}", metricName, line);
        }
    }
    
    private Map<String, Object> createFallbackMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("messages_in", 0L);
        metrics.put("events_captured", 0L); 
        metrics.put("messages_out", 0L);
        metrics.put("invalid_messages", 0L);
        metrics.put("status", "fallback");
        metrics.put("error", "Service not available via discovery - using fallback");
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
}