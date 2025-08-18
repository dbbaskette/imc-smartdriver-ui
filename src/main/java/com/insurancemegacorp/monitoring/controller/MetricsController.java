package com.insurancemegacorp.monitoring.controller;

import com.insurancemegacorp.monitoring.dto.PipelineMetrics;
import com.insurancemegacorp.monitoring.service.MetricsCollectorService;
import com.insurancemegacorp.monitoring.service.RabbitMetricsService;
import com.insurancemegacorp.monitoring.service.ExchangeMetricsService;
import com.insurancemegacorp.monitoring.service.ComponentHealthService;
import com.insurancemegacorp.monitoring.service.ServiceDiscoveryHealthService;
import com.insurancemegacorp.monitoring.service.TelemetryGeneratorMetricsService;
import com.insurancemegacorp.monitoring.service.TelemematicsExchangeMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class MetricsController {

    private final MetricsCollectorService metricsCollectorService;
    private final RabbitMetricsService rabbitMetricsService;
    private final ExchangeMetricsService exchangeMetricsService;
    private final ComponentHealthService componentHealthService;
    
    @Autowired(required = false)
    private ServiceDiscoveryHealthService serviceDiscoveryHealthService;
    
    @Autowired
    private TelemetryGeneratorMetricsService telemetryGeneratorMetricsService;
    
    @Autowired
    private TelemematicsExchangeMetricsService telemematicsExchangeMetricsService;

    public MetricsController(MetricsCollectorService metricsCollectorService, 
                           RabbitMetricsService rabbitMetricsService,
                           ExchangeMetricsService exchangeMetricsService,
                           ComponentHealthService componentHealthService) {
        this.metricsCollectorService = metricsCollectorService;
        this.rabbitMetricsService = rabbitMetricsService;
        this.exchangeMetricsService = exchangeMetricsService;
        this.componentHealthService = componentHealthService;
    }

    @GetMapping("/metrics")
    public ResponseEntity<PipelineMetrics> getCurrentMetrics() {
        PipelineMetrics metrics = metricsCollectorService.getCurrentMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/rabbitmq/health")
    public ResponseEntity<Map<String, Object>> getRabbitMQHealth() {
        boolean healthy = metricsCollectorService.isRabbitMQHealthy();
        String version = rabbitMetricsService.getRabbitMQVersion();
        
        Map<String, Object> response = Map.of(
            "healthy", healthy,
            "version", version,
            "status", healthy ? "UP" : "DOWN"
        );
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rabbitmq/queue")
    public ResponseEntity<Map<String, Object>> getQueueInfo() {
        int queueDepth = rabbitMetricsService.getQueueDepth();
        
        Map<String, Object> response = Map.of(
            "queue_depth", queueDepth,
            "status", queueDepth >= 0 ? "accessible" : "error"
        );
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rabbitmq/queues")
    public ResponseEntity<List<Map<String, Object>>> getAllQueues() {
        List<Map<String, Object>> queues = rabbitMetricsService.getAllQueues();
        return ResponseEntity.ok(queues);
    }

    @GetMapping("/rabbitmq/exchange/throughput")
    public ResponseEntity<Map<String, Object>> getExchangeThroughput() {
        Map<String, Object> throughputStats = exchangeMetricsService.getExchangeThroughputStats();
        return ResponseEntity.ok(throughputStats);
    }

    @GetMapping("/components/health")
    public ResponseEntity<Map<String, Object>> getComponentHealth() {
        Map<String, Boolean> healthStatus;
        String discoveryMode;
        
        // Use service discovery if available, otherwise fall back to static URLs
        if (serviceDiscoveryHealthService != null) {
            healthStatus = serviceDiscoveryHealthService.getAllComponentHealth();
            discoveryMode = "service_discovery";
        } else {
            healthStatus = componentHealthService.getAllComponentHealth();
            discoveryMode = "static_urls";
        }
        
        Map<String, Object> response = Map.of(
            "component_health", healthStatus,
            "discovery_mode", discoveryMode,
            "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/components/services")
    public ResponseEntity<Map<String, Object>> getDiscoveredServices() {
        if (serviceDiscoveryHealthService != null) {
            Map<String, Object> response = Map.of(
                "discovered_services", serviceDiscoveryHealthService.getDiscoveredServices(),
                "service_mappings", serviceDiscoveryHealthService.getServiceMappings(),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> response = Map.of(
                "error", "Service discovery not enabled",
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.ok(response);
        }
    }
    
    @GetMapping("/components/all-services")
    public ResponseEntity<Map<String, Object>> getAllAvailableServices() {
        if (serviceDiscoveryHealthService != null) {
            Map<String, Object> response = Map.of(
                "all_available_services", serviceDiscoveryHealthService.getAllAvailableServices(),
                "configured_mappings", serviceDiscoveryHealthService.getServiceMappings(),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> response = Map.of(
                "error", "Service discovery not enabled",
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.ok(response);
        }
    }
    
    @GetMapping("/telemetry/generator/metrics")
    public ResponseEntity<Map<String, Object>> getTelemetryGeneratorMetrics() {
        Map<String, Object> metrics = telemetryGeneratorMetricsService.getPublishingMetrics();
        return ResponseEntity.ok(metrics);
    }
    
    @GetMapping("/telemetry/generator/health")
    public ResponseEntity<Map<String, Object>> getTelemetryGeneratorHealth() {
        Map<String, Object> health = telemetryGeneratorMetricsService.getHealthStatus();
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/telematics/exchange/queues")
    public ResponseEntity<Map<String, Object>> getTelemematicsExchangeQueues() {
        Map<String, Object> queueMetrics = telemematicsExchangeMetricsService.getExchangeQueueMetrics();
        return ResponseEntity.ok(queueMetrics);
    }
    
    @GetMapping("/rabbitmq/exchange/health")
    public ResponseEntity<Map<String, Object>> getRabbitMQExchangeHealth() {
        Map<String, Object> health = telemematicsExchangeMetricsService.getRabbitMQHealthStatus();
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/rabbitmq/management/url")
    public ResponseEntity<Map<String, Object>> getRabbitMQManagementUrl() {
        Map<String, Object> urlInfo = telemematicsExchangeMetricsService.getManagementDashboardUrl();
        return ResponseEntity.ok(urlInfo);
    }
    
    @GetMapping("/vehicle-events/queue/metrics")
    public ResponseEntity<Map<String, Object>> getVehicleEventsQueueMetrics() {
        Map<String, Object> queueMetrics = telemematicsExchangeMetricsService.getVehicleEventsQueueMetrics();
        return ResponseEntity.ok(queueMetrics);
    }
    
    @GetMapping("/events-processor/metrics")
    public ResponseEntity<Map<String, Object>> getEventsProcessorMetrics() {
        Map<String, Object> processorMetrics = telemematicsExchangeMetricsService.getEventsProcessorMetrics();
        return ResponseEntity.ok(processorMetrics);
    }
    
    @GetMapping("/debug/telemetry/raw")
    public ResponseEntity<String> getDebugTelemetryRaw() {
        try {
            // This endpoint will help debug what's actually being received
            Map<String, Object> metrics = telemetryGeneratorMetricsService.getPublishingMetrics();
            return ResponseEntity.ok("Debug telemetry metrics: " + metrics.toString());
        } catch (Exception e) {
            return ResponseEntity.ok("Error fetching telemetry metrics: " + e.getMessage());
        }
    }
}