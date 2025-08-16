package com.insurancemegacorp.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HealthCheckScheduler {

    private final ComponentHealthService componentHealthService;
    
    @Autowired(required = false)
    private ServiceDiscoveryHealthService serviceDiscoveryHealthService;

    public HealthCheckScheduler(ComponentHealthService componentHealthService) {
        this.componentHealthService = componentHealthService;
    }

    @Scheduled(fixedRate = 8000) // Check every 8 seconds
    public void performHealthChecks() {
        log.debug("Performing scheduled component health checks");
        
        // Use service discovery if available, otherwise fall back to static URLs
        if (serviceDiscoveryHealthService != null) {
            log.debug("Using service discovery for health checks");
            serviceDiscoveryHealthService.checkAllServiceHealth();
        } else {
            log.debug("Using static URLs for health checks");
            componentHealthService.checkAllComponentHealth();
        }
    }
}