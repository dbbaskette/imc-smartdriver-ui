package com.insurancemegacorp.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ComponentHealthService {

    private final RestTemplate restTemplate;
    private final Map<String, Boolean> componentHealthStatus = new ConcurrentHashMap<>();
    private final Map<String, String> componentUrls = new ConcurrentHashMap<>();
    
    public ComponentHealthService(RestTemplate restTemplate,
                                @Value("${component.health.generator-url:http://localhost:8082/actuator/health}") String generatorUrl,
                                @Value("${component.health.processor-url:http://localhost:8080/actuator/health}") String processorUrl,
                                @Value("${component.health.hdfs-url:http://localhost:8081/actuator/health}") String hdfsUrl,
                                @Value("${component.health.jdbc-url:http://localhost:8083/actuator/health}") String jdbcUrl) {
        this.restTemplate = restTemplate;
        
        // Initialize component URLs and default health status
        componentUrls.put("generator", generatorUrl);
        componentUrls.put("processor", processorUrl);
        componentUrls.put("hdfs", hdfsUrl);
        componentUrls.put("jdbc", jdbcUrl);
        
        // Default all components to healthy until first check
        componentHealthStatus.put("generator", true);
        componentHealthStatus.put("processor", true);
        componentHealthStatus.put("hdfs", true);
        componentHealthStatus.put("jdbc", true);
        
        log.info("Component Health Service initialized with URLs:");
        log.info("  Generator: {}", generatorUrl);
        log.info("  Processor: {}", processorUrl);
        log.info("  HDFS: {}", hdfsUrl);
        log.info("  JDBC: {}", jdbcUrl);
    }
    
    public void checkAllComponentHealth() {
        componentUrls.forEach((component, url) -> {
            boolean isHealthy = checkSingleComponentHealth(component, url);
            componentHealthStatus.put(component, isHealthy);
        });
    }
    
    private boolean checkSingleComponentHealth(String componentName, String healthUrl) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(healthUrl, (Class<Map<String, Object>>) (Class<?>) Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> healthInfo = response.getBody();
                String status = (String) healthInfo.get("status");
                boolean isHealthy = "UP".equalsIgnoreCase(status);
                
                log.debug("Component {} health check: status={}, healthy={}", componentName, status, isHealthy);
                return isHealthy;
            } else {
                log.warn("Component {} health check failed with status: {}", componentName, response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.warn("Component {} health check failed: {}", componentName, e.getMessage());
            return false;
        }
    }
    
    public boolean isComponentHealthy(String componentName) {
        return componentHealthStatus.getOrDefault(componentName, false);
    }
    
    public Map<String, Boolean> getAllComponentHealth() {
        return Map.copyOf(componentHealthStatus);
    }
    
    public boolean isFlowPathHealthy(String sourceComponent, String targetComponent) {
        // A flow path is healthy if both source and target components are healthy
        return isComponentHealthy(sourceComponent) && isComponentHealthy(targetComponent);
    }
}