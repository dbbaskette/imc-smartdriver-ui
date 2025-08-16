package com.insurancemegacorp.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@ConditionalOnProperty(name = "component.health.discovery.enabled", havingValue = "true", matchIfMissing = false)
public class ServiceDiscoveryHealthService {

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;
    private final Map<String, Boolean> componentHealthStatus = new ConcurrentHashMap<>();
    
    // Service name patterns to health check - configurable mapping
    private final Map<String, String> serviceNamePatterns;

    @Autowired
    public ServiceDiscoveryHealthService(DiscoveryClient discoveryClient, 
                                       RestTemplate restTemplate,
                                       @Value("${component.health.service-mappings:}") String serviceMappings) {
        this.discoveryClient = discoveryClient;
        this.restTemplate = restTemplate;
        
        // Parse service mappings from configuration
        this.serviceNamePatterns = parseServiceMappings(serviceMappings);
        
        // Initialize health status
        serviceNamePatterns.keySet().forEach(component -> 
            componentHealthStatus.put(component, false)
        );
        
        log.info("Service Discovery Health Service initialized");
        log.info("Component to service mappings: {}", serviceNamePatterns);
    }
    
    public void checkAllServiceHealth() {
        serviceNamePatterns.forEach((component, serviceName) -> {
            boolean isHealthy = checkServiceHealth(component, serviceName);
            componentHealthStatus.put(component, isHealthy);
        });
    }
    
    private boolean checkServiceHealth(String componentName, String serviceName) {
        try {
            // Find service instances
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            
            if (instances == null || instances.isEmpty()) {
                log.debug("No instances found for service: {}", serviceName);
                return false;
            }
            
            // Use the first healthy instance
            for (ServiceInstance instance : instances) {
                String healthUrl = instance.getUri() + "/actuator/health";
                
                try {
                    ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
                        healthUrl, 
                        (Class<Map<String, Object>>) (Class<?>) Map.class
                    );
                    
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        Map<String, Object> healthInfo = response.getBody();
                        String status = (String) healthInfo.get("status");
                        boolean isHealthy = "UP".equalsIgnoreCase(status);
                        
                        log.debug("Service {} (instance {}) health: {}", serviceName, instance.getInstanceId(), status);
                        
                        if (isHealthy) {
                            return true; // At least one instance is healthy
                        }
                    }
                } catch (Exception e) {
                    log.debug("Health check failed for {} instance {}: {}", serviceName, instance.getInstanceId(), e.getMessage());
                }
            }
            
            return false; // No healthy instances found
            
        } catch (Exception e) {
            log.warn("Failed to check health for service {}: {}", serviceName, e.getMessage());
            return false;
        }
    }
    
    public boolean isComponentHealthy(String componentName) {
        return componentHealthStatus.getOrDefault(componentName, false);
    }
    
    public Map<String, Boolean> getAllComponentHealth() {
        return Map.copyOf(componentHealthStatus);
    }
    
    public Map<String, List<ServiceInstance>> getDiscoveredServices() {
        Map<String, List<ServiceInstance>> services = new ConcurrentHashMap<>();
        serviceNamePatterns.values().forEach(serviceName -> {
            try {
                List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
                services.put(serviceName, instances);
            } catch (Exception e) {
                log.debug("Failed to get instances for service {}: {}", serviceName, e.getMessage());
            }
        });
        return services;
    }
    
    public Map<String, List<ServiceInstance>> getAllAvailableServices() {
        Map<String, List<ServiceInstance>> allServices = new ConcurrentHashMap<>();
        try {
            List<String> serviceNames = discoveryClient.getServices();
            for (String serviceName : serviceNames) {
                List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
                allServices.put(serviceName, instances);
            }
        } catch (Exception e) {
            log.error("Failed to get all available services: {}", e.getMessage());
        }
        return allServices;
    }
    
    private Map<String, String> parseServiceMappings(String serviceMappings) {
        Map<String, String> mappings = new HashMap<>();
        
        if (serviceMappings == null || serviceMappings.trim().isEmpty()) {
            // Default mappings if none configured
            mappings.put("generator", "imc-telematics-gen");
            mappings.put("processor", "imc-telemetry-processor");
            mappings.put("hdfs", "imc-hdfs-sink");
            mappings.put("jdbc", "vehicle-events-sink");
            log.info("Using default service mappings");
        } else {
            // Parse format: "component1:service1,component2:service2"
            String[] pairs = serviceMappings.split(",");
            for (String pair : pairs) {
                String[] parts = pair.trim().split(":");
                if (parts.length == 2) {
                    String component = parts[0].trim();
                    String serviceName = parts[1].trim();
                    mappings.put(component, serviceName);
                    log.debug("Mapped component '{}' to service '{}'", component, serviceName);
                } else {
                    log.warn("Invalid service mapping format: '{}'. Expected 'component:service'", pair);
                }
            }
            log.info("Using configured service mappings from properties");
        }
        
        return mappings;
    }
    
    public Map<String, String> getServiceMappings() {
        return Map.copyOf(serviceNamePatterns);
    }
}