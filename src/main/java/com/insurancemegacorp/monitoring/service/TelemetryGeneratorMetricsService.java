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
public class TelemetryGeneratorMetricsService {

    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;
    private final String serviceName = "imc-telematics-gen";

    @Autowired
    public TelemetryGeneratorMetricsService(RestTemplate restTemplate,
                                          DiscoveryClient discoveryClient) {
        this.restTemplate = restTemplate;
        this.discoveryClient = discoveryClient;
    }

    /**
     * Get RabbitMQ publishing metrics from the telemetry generator service
     */
    public Map<String, Object> getPublishingMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            Optional<String> serviceUrl = getServiceUrl();
            
            if (serviceUrl.isEmpty()) {
                log.error("No healthy instances found for service: {}. Check if service is registered and running.", serviceName);
                return createErrorMetrics("Service not available - check if " + serviceName + " is running and registered");
            }
            
            log.info("Connecting to telemetry generator service at: {}", serviceUrl.get());
            
            // Use Prometheus endpoint for custom telemetry metrics
            String prometheusUrl = serviceUrl.get() + "/actuator/prometheus";
            
            // Get Prometheus metrics
            Map<String, Double> prometheusMetrics = getPrometheusMetrics(prometheusUrl);
            
            // Extract telemetry-specific metrics
            double totalSent = prometheusMetrics.getOrDefault("telematics_messages_sent_total", 0.0);
            double messageRate = prometheusMetrics.getOrDefault("telematics_messages_rate", 0.0);
            
            metrics.put("messages_published_total", Math.round(totalSent));
            metrics.put("messages_rate_per_sec", messageRate);
            metrics.put("service_url", serviceUrl.get());
            metrics.put("status", "healthy");
            metrics.put("timestamp", System.currentTimeMillis());
            
            log.info("Retrieved telemetry metrics from {}: sent={}, rate={}/sec", 
                serviceName, Math.round(totalSent), messageRate);
            
        } catch (Exception e) {
            log.error("Failed to fetch publishing metrics from {}: {}", serviceName, e.getMessage());
            return createErrorMetrics("Failed to fetch metrics: " + e.getMessage());
        }
        
        return metrics;
    }
    
    /**
     * Get health status of the telemetry generator service
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
    
    private Map<String, Object> getMetric(String baseUrl, String metricName) {
        try {
            String url = baseUrl + "/" + metricName;
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
                url, 
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            
        } catch (Exception e) {
            log.debug("Failed to fetch metric {}: {}", metricName, e.getMessage());
        }
        
        return new HashMap<>();
    }
    
    private Map<String, Double> getPrometheusMetrics(String prometheusUrl) {
        Map<String, Double> metrics = new HashMap<>();
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(prometheusUrl, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String prometheusText = response.getBody();
                
                // Parse Prometheus text format for our specific metrics
                String[] lines = prometheusText.split("\n");
                log.info("Parsing {} lines from Prometheus endpoint", lines.length);
                
                for (String line : lines) {
                    line = line.trim();
                    
                    // Look for our specific metrics (ignore comments and metadata)
                    if (line.startsWith("telematics_messages_sent_total") && !line.startsWith("#")) {
                        log.info("Found telematics_messages_sent_total line: {}", line);
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            try {
                                String valueStr = parts[parts.length - 1]; // Take the last part as the value
                                double value = Double.parseDouble(valueStr);
                                metrics.put("telematics_messages_sent_total", value);
                                log.info("Parsed telematics_messages_sent_total: {}", value);
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse telematics_messages_sent_total value: {} from line: {}", parts[parts.length - 1], line);
                            }
                        } else {
                            log.warn("Invalid format for telematics_messages_sent_total line: {}", line);
                        }
                    }
                    
                    if (line.startsWith("telematics_messages_rate") && !line.startsWith("#")) {
                        log.info("Found telematics_messages_rate line: {}", line);
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            try {
                                String valueStr = parts[parts.length - 1]; // Take the last part as the value
                                double value = Double.parseDouble(valueStr);
                                metrics.put("telematics_messages_rate", value);
                                log.info("Parsed telematics_messages_rate: {}", value);
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse telematics_messages_rate value: {} from line: {}", parts[parts.length - 1], line);
                            }
                        } else {
                            log.warn("Invalid format for telematics_messages_rate line: {}", line);
                        }
                    }
                }
                
                log.info("Final parsed metrics: {}", metrics);
                
                log.debug("Parsed Prometheus metrics: {}", metrics);
            } else {
                log.warn("Failed to fetch Prometheus metrics, status: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch Prometheus metrics: {}", e.getMessage());
        }
        
        return metrics;
    }
    
    private Map<String, Object> getAvailableMetrics(String baseUrl) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
                baseUrl, 
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            
        } catch (Exception e) {
            log.debug("Failed to fetch available metrics: {}", e.getMessage());
        }
        
        return new HashMap<>();
    }
    
    private Object extractMetricValue(Map<String, Object> metric, Object defaultValue) {
        if (metric == null || metric.isEmpty()) {
            return defaultValue;
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> measurements = (List<Map<String, Object>>) metric.get("measurements");
        if (measurements != null && !measurements.isEmpty()) {
            return measurements.get(0).get("value");
        }
        
        return defaultValue;
    }
    
    private Object extractMetricValue(Map<String, Object> metric, String statistic, Object defaultValue) {
        if (metric == null || metric.isEmpty()) {
            return defaultValue;
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> measurements = (List<Map<String, Object>>) metric.get("measurements");
        if (measurements != null) {
            for (Map<String, Object> measurement : measurements) {
                if (statistic.equals(measurement.get("statistic"))) {
                    return measurement.get("value");
                }
            }
        }
        
        return defaultValue;
    }
    
    private Double extractTimerAverage(Map<String, Object> timerMetric, Double defaultValue) {
        return (Double) extractMetricValue(timerMetric, "mean", defaultValue);
    }
    
    private Double extractTimerMax(Map<String, Object> timerMetric, Double defaultValue) {
        return (Double) extractMetricValue(timerMetric, "max", defaultValue);
    }
    
    private Map<String, Object> createErrorMetrics(String error) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("messages_published_total", 0L);
        metrics.put("messages_rate_per_sec", 0.0);
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
}