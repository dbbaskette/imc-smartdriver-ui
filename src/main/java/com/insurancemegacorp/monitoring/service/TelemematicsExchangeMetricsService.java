package com.insurancemegacorp.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Slf4j
@Service
public class TelemematicsExchangeMetricsService {

    private final RestTemplate restTemplate;
    private final String managementApiUrl;
    private final HttpEntity<String> httpEntity;
    private final String exchangeName = "telematics_exchange";
    private final String vhost = "cf986537-69cc-4107-8b66-5542481de9ba";

    public TelemematicsExchangeMetricsService(
            RestTemplate restTemplate,
            @Value("${rabbitmq.management.api-url:}") String managementApiUrl,
            @Value("${spring.rabbitmq.host:${rabbitmq.host:localhost}}") String host,
            @Value("${rabbitmq.management.port:15672}") int managementPort,
            @Value("${spring.rabbitmq.username:${rabbitmq.username:guest}}") String username,
            @Value("${spring.rabbitmq.password:${rabbitmq.password:guest}}") String password) {
        
        this.restTemplate = restTemplate;
        
        // Use full API URL if provided (CF style), otherwise construct from host/port
        String apiUrl;
        HttpHeaders headers = new HttpHeaders();
        
        if (managementApiUrl != null && !managementApiUrl.isEmpty()) {
            // CF service key style - full URL with embedded credentials
            try {
                java.net.URL url = new java.net.URL(managementApiUrl);
                String userInfo = url.getUserInfo();
                if (userInfo != null) {
                    String[] credentials = userInfo.split(":");
                    String extractedUsername = credentials[0];
                    String extractedPassword = credentials[1];
                    
                    // Rebuild URL without credentials
                    apiUrl = managementApiUrl.replace(userInfo + "@", "");
                    
                    // Create Basic Auth header
                    String auth = extractedUsername + ":" + extractedPassword;
                    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                    headers.set("Authorization", "Basic " + encodedAuth);
                    
                    log.info("Using CF-style management API URL for telematics exchange metrics");
                } else {
                    apiUrl = managementApiUrl;
                }
            } catch (Exception e) {
                log.warn("Could not parse CF management API URL for telematics exchange, using as-is: {}", e.getMessage());
                apiUrl = managementApiUrl;
            }
        } else {
            // Legacy style - construct URL and use Basic Auth
            if (host.startsWith("http")) {
                try {
                    java.net.URL url = new java.net.URL(host.replace("https://", "http://"));
                    apiUrl = String.format("http://%s:%d/api", url.getHost(), managementPort);
                } catch (Exception e) {
                    apiUrl = host.endsWith("/api") ? host : host + "/api";
                }
            } else {
                apiUrl = String.format("http://%s:%d/api", host, managementPort);
            }
            
            // Create Basic Auth header
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
        }
        
        this.managementApiUrl = apiUrl;
        this.httpEntity = new HttpEntity<>(headers);
        
        log.info("TelemematicsExchangeMetricsService initialized for exchange: {} at {}", exchangeName, managementApiUrl);
    }

    /**
     * Get RabbitMQ health status
     */
    public Map<String, Object> getRabbitMQHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            String healthUrl = managementApiUrl + "/aliveness-test/" + vhost;
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                healthUrl, 
                HttpMethod.GET, 
                httpEntity, 
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> aliveness = response.getBody();
                String status = (String) aliveness.get("status");
                boolean isHealthy = "ok".equalsIgnoreCase(status);
                
                health.put("healthy", isHealthy);
                health.put("status", isHealthy ? "UP" : "DOWN");
                health.put("rabbitmq_status", status);
                health.put("vhost", vhost);
                health.put("timestamp", System.currentTimeMillis());
                
                log.debug("RabbitMQ health check: {}", status);
                
            } else {
                health.put("healthy", false);
                health.put("status", "DOWN");
                health.put("error", "Health endpoint returned: " + response.getStatusCode());
                health.put("timestamp", System.currentTimeMillis());
            }
            
        } catch (Exception e) {
            log.warn("RabbitMQ health check failed: {}", e.getMessage());
            health.put("healthy", false);
            health.put("status", "DOWN");
            health.put("error", "Health check failed: " + e.getMessage());
            health.put("timestamp", System.currentTimeMillis());
        }
        
        return health;
    }

    /**
     * Get all queues bound to the telematics_exchange with their metrics
     */
    public Map<String, Object> getExchangeQueueMetrics() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get exchange bindings to find queues
            List<Map<String, Object>> boundQueues = getExchangeBindings();
            
            // Get metrics for each queue
            List<Map<String, Object>> queueMetrics = new ArrayList<>();
            for (Map<String, Object> binding : boundQueues) {
                String queueName = (String) binding.get("destination");
                if (queueName != null && !queueName.isEmpty()) {
                    Map<String, Object> queueMetric = getQueueMetrics(queueName);
                    if (!queueMetric.isEmpty()) {
                        queueMetrics.add(queueMetric);
                    }
                }
            }
            
            result.put("exchange_name", exchangeName);
            result.put("total_queues", boundQueues.size());
            result.put("queues", queueMetrics);
            result.put("timestamp", System.currentTimeMillis());
            
            log.debug("Retrieved metrics for {} queues bound to {}", boundQueues.size(), exchangeName);
            
        } catch (Exception e) {
            log.error("Failed to get exchange queue metrics: {}", e.getMessage());
            result.put("error", "Failed to fetch metrics: " + e.getMessage());
            result.put("exchange_name", exchangeName);
            result.put("total_queues", 0);
            result.put("queues", List.of());
            result.put("timestamp", System.currentTimeMillis());
        }
        
        return result;
    }
    
    /**
     * Get all bindings for the telematics_exchange
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getExchangeBindings() {
        try {
            String bindingsUrl = managementApiUrl + "/exchanges/" + vhost + "/" + exchangeName + "/bindings/source";
            log.debug("Fetching exchange bindings from: {}", bindingsUrl);
            
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                bindingsUrl, 
                HttpMethod.GET, 
                httpEntity, 
                (Class<List<Map<String, Object>>>) (Class<?>) List.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> bindings = response.getBody();
                log.debug("Found {} bindings for exchange {}", bindings.size(), exchangeName);
                return bindings;
            } else {
                log.warn("Failed to get exchange bindings, status: {}", response.getStatusCode());
                return List.of();
            }
        } catch (Exception e) {
            log.error("Error fetching exchange bindings: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Get metrics for a specific queue
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getQueueMetrics(String queueName) {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            String queueUrl = managementApiUrl + "/queues/" + vhost + "/" + queueName;
            log.debug("Fetching queue metrics from: {}", queueUrl);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                queueUrl, 
                HttpMethod.GET, 
                httpEntity, 
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> queueInfo = response.getBody();
                
                // Extract basic queue info
                metrics.put("name", queueName);
                metrics.put("messages", getLongValue(queueInfo, "messages"));
                metrics.put("messages_ready", getLongValue(queueInfo, "messages_ready"));
                metrics.put("messages_unacknowledged", getLongValue(queueInfo, "messages_unacknowledged"));
                
                // Extract message stats if available
                Map<String, Object> messageStats = (Map<String, Object>) queueInfo.get("message_stats");
                if (messageStats != null) {
                    // Messages delivered/published to this queue
                    metrics.put("messages_delivered", getLongValue(messageStats, "deliver_get"));
                    metrics.put("messages_published_to_queue", getLongValue(messageStats, "publish"));
                    
                    // Get rates
                    Map<String, Object> deliverDetails = (Map<String, Object>) messageStats.get("deliver_get_details");
                    Map<String, Object> publishDetails = (Map<String, Object>) messageStats.get("publish_details");
                    
                    metrics.put("delivery_rate", deliverDetails != null ? getDoubleValue(deliverDetails, "rate") : 0.0);
                    metrics.put("publish_rate", publishDetails != null ? getDoubleValue(publishDetails, "rate") : 0.0);
                } else {
                    // No message stats available
                    metrics.put("messages_delivered", 0L);
                    metrics.put("messages_published_to_queue", 0L);
                    metrics.put("delivery_rate", 0.0);
                    metrics.put("publish_rate", 0.0);
                }
                
                log.debug("Retrieved metrics for queue {}: {} messages", queueName, metrics.get("messages"));
                
            } else {
                log.warn("Failed to get queue metrics for {}, status: {}", queueName, response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching queue metrics for {}: {}", queueName, e.getMessage());
        }
        
        return metrics;
    }
    
    private long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
    
    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}