package com.insurancemegacorp.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RabbitMetricsService {

    private final RestTemplate restTemplate;
    private final String managementApiUrl;
    private final String queueName;
    private final HttpEntity<String> httpEntity;
    private final List<String> displayQueues;

    public RabbitMetricsService(
            RestTemplate restTemplate,
            @Value("${rabbitmq.management.api-url:}") String managementApiUrl,
            @Value("${rabbitmq.host}") String host,
            @Value("${rabbitmq.management.port:15672}") int managementPort,
            @Value("${rabbitmq.username}") String username,
            @Value("${rabbitmq.password}") String password,
            @Value("${rabbitmq.queue.name}") String queueName,
            @Value("${rabbitmq.display.queues:}") String displayQueuesStr) {
        
        this.restTemplate = restTemplate;
        this.queueName = queueName;
        
        // Use full API URL if provided (CF style), otherwise construct from host/port
        String apiUrl;
        HttpHeaders headers = new HttpHeaders();
        
        if (managementApiUrl != null && !managementApiUrl.isEmpty()) {
            // CF service key style - full URL with embedded credentials
            // Extract credentials from URL and use Basic Auth instead
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
                    
                    log.info("Using CF-style management API URL with extracted Basic Auth credentials");
                } else {
                    apiUrl = managementApiUrl;
                    log.info("Using CF-style management API URL without embedded credentials");
                }
            } catch (Exception e) {
                log.warn("Could not parse CF management API URL, using as-is: {}", e.getMessage());
                apiUrl = managementApiUrl;
            }
        } else {
            // Legacy style - construct URL and use Basic Auth
            if (host.startsWith("http")) {
                // Full URL provided, extract hostname
                try {
                    java.net.URL url = new java.net.URL(host.replace("https://", "http://"));
                    apiUrl = String.format("http://%s:%d/api", url.getHost(), managementPort);
                } catch (Exception e) {
                    log.warn("Could not parse RabbitMQ URL {}, using as-is", host);
                    apiUrl = host.endsWith("/api") ? host : host + "/api";
                }
            } else {
                // Just hostname provided
                apiUrl = String.format("http://%s:%d/api", host, managementPort);
            }
            
            // Create Basic Auth header
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
        }
        
        this.managementApiUrl = apiUrl;
        this.httpEntity = new HttpEntity<>(headers);
        
        // Parse comma-separated queue names
        if (displayQueuesStr != null && !displayQueuesStr.trim().isEmpty()) {
            this.displayQueues = java.util.Arrays.asList(displayQueuesStr.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        } else {
            this.displayQueues = java.util.Collections.emptyList();
        }
        
        log.info("RabbitMQ Metrics Service initialized for queue: {} at {}", queueName, managementApiUrl);
        log.info("Display queues filter: {}", displayQueues.isEmpty() ? "showing all queues" : displayQueues);
    }

    public int getQueueDepth() {
        try {
            // Use the CF vhost instead of default %2f vhost
            String vhost = "cf986537-69cc-4107-8b66-5542481de9ba";
            String queueUrl = managementApiUrl + "/queues/" + vhost + "/" + queueName;
            log.debug("Fetching queue info from: {}", queueUrl);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                queueUrl, 
                HttpMethod.GET, 
                httpEntity, 
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> queueInfo = response.getBody();
                Integer messages = (Integer) queueInfo.get("messages");
                int queueDepth = messages != null ? messages : 0;
                log.debug("Queue {} has {} messages", queueName, queueDepth);
                return queueDepth;
            } else {
                log.warn("Failed to get queue info, status: {}", response.getStatusCode());
                return -1;
            }
        } catch (Exception e) {
            log.error("Error fetching queue depth for {}: {}", queueName, e.getMessage());
            return -1;
        }
    }

    public boolean isRabbitMQHealthy() {
        try {
            // Use the CF vhost instead of default %2f vhost  
            String vhost = "cf986537-69cc-4107-8b66-5542481de9ba";
            String healthUrl = managementApiUrl + "/aliveness-test/" + vhost;
            ResponseEntity<Map> response = restTemplate.exchange(
                healthUrl, 
                HttpMethod.GET, 
                httpEntity, 
                Map.class
            );
            
            boolean healthy = response.getStatusCode().is2xxSuccessful();
            log.debug("RabbitMQ health check: {}", healthy ? "healthy" : "unhealthy");
            return healthy;
        } catch (Exception e) {
            log.error("RabbitMQ health check failed: {}", e.getMessage());
            return false;
        }
    }

    public String getRabbitMQVersion() {
        try {
            String overviewUrl = managementApiUrl + "/overview";
            ResponseEntity<Map> response = restTemplate.exchange(
                overviewUrl, 
                HttpMethod.GET, 
                httpEntity, 
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> overview = response.getBody();
                String version = (String) overview.get("rabbitmq_version");
                return version != null ? version : "unknown";
            }
        } catch (Exception e) {
            log.debug("Could not fetch RabbitMQ version: {}", e.getMessage());
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAllQueues() {
        try {
            String queuesUrl = managementApiUrl + "/queues";
            log.debug("Fetching all queues from: {}", queuesUrl);
            
            ResponseEntity<List> response = restTemplate.exchange(
                queuesUrl, 
                HttpMethod.GET, 
                httpEntity, 
                List.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> allQueues = response.getBody();
                
                // Filter queues based on display configuration
                List<Map<String, Object>> filteredQueues;
                if (displayQueues.isEmpty()) {
                    // If no filter specified, show all queues
                    filteredQueues = allQueues;
                    log.debug("Found {} queues (showing all)", allQueues.size());
                } else {
                    // Filter to show only specified queues
                    filteredQueues = allQueues.stream()
                        .filter(queue -> displayQueues.contains((String) queue.get("name")))
                        .collect(Collectors.toList());
                    log.debug("Found {} queues, filtered to {} based on configuration", allQueues.size(), filteredQueues.size());
                }
                
                return filteredQueues;
            } else {
                log.warn("Failed to get queues list, status: {}", response.getStatusCode());
                return List.of();
            }
        } catch (Exception e) {
            log.error("Error fetching queues list: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> getExchangeStats(String exchangeName) {
        try {
            String vhost = "cf986537-69cc-4107-8b66-5542481de9ba";
            String exchangeUrl = managementApiUrl + "/exchanges/" + vhost + "/" + exchangeName;
            log.debug("Fetching exchange stats from: {}", exchangeUrl);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                exchangeUrl, 
                HttpMethod.GET, 
                httpEntity, 
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> exchangeInfo = response.getBody();
                log.debug("Exchange {} stats retrieved successfully", exchangeName);
                return exchangeInfo;
            } else {
                log.warn("Failed to get exchange stats for {}, status: {}", exchangeName, response.getStatusCode());
                return Map.of();
            }
        } catch (Exception e) {
            log.error("Error fetching exchange stats for {}: {}", exchangeName, e.getMessage());
            return Map.of();
        }
    }
}