package com.insurancemegacorp.monitoring.controller;

import com.insurancemegacorp.monitoring.dto.PipelineMetrics;
import com.insurancemegacorp.monitoring.service.MetricsCollectorService;
import com.insurancemegacorp.monitoring.service.RabbitMetricsService;
import com.insurancemegacorp.monitoring.service.ExchangeMetricsService;
import com.insurancemegacorp.monitoring.service.ComponentHealthService;
import com.insurancemegacorp.monitoring.service.ServiceDiscoveryHealthService;
import com.insurancemegacorp.monitoring.service.TelemetryGeneratorMetricsService;
import com.insurancemegacorp.monitoring.service.TelemematicsExchangeMetricsService;
import com.insurancemegacorp.monitoring.service.TelemetryProcessorMetricsService;
import com.insurancemegacorp.monitoring.service.VehicleEventsJdbcSinkService;
import com.insurancemegacorp.monitoring.service.MetricsBaselineService;
import com.insurancemegacorp.monitoring.service.GreenplumService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    
    @Autowired
    private TelemetryProcessorMetricsService telemetryProcessorMetricsService;
    
    @Autowired
    private VehicleEventsJdbcSinkService vehicleEventsJdbcSinkService;
    
    @Autowired
    private MetricsBaselineService metricsBaselineService;
    
    @Autowired
    private GreenplumService greenplumService;

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
        Map<String, Object> rawMetrics = exchangeMetricsService.getExchangeThroughputStats();
        Map<String, Object> adjustedMetrics = metricsBaselineService.getAdjustedMetrics("exchange", rawMetrics);
        return ResponseEntity.ok(adjustedMetrics);
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
        Map<String, Object> rawMetrics = telemetryGeneratorMetricsService.getPublishingMetrics();
        Map<String, Object> adjustedMetrics = metricsBaselineService.getAdjustedMetrics("telemetry_generator", rawMetrics);
        return ResponseEntity.ok(adjustedMetrics);
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
        Map<String, Object> rawMetrics = telemetryProcessorMetricsService.getProcessorMetrics();
        Map<String, Object> adjustedMetrics = metricsBaselineService.getAdjustedMetrics("processor", rawMetrics);
        return ResponseEntity.ok(adjustedMetrics);
    }
    
    @GetMapping("/events-processor/health")
    public ResponseEntity<Map<String, Object>> getEventsProcessorHealth() {
        Map<String, Object> health = telemetryProcessorMetricsService.getHealthStatus();
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/hdfs-sink/metrics")
    public ResponseEntity<Map<String, Object>> getHdfsSinkMetrics() {
        Map<String, Object> rawMetrics = telemematicsExchangeMetricsService.getHdfsSinkMetrics();
        Map<String, Object> adjustedMetrics = metricsBaselineService.getAdjustedMetrics("hdfs_sink", rawMetrics);
        return ResponseEntity.ok(adjustedMetrics);
    }
    
    @GetMapping("/jdbc-sink/metrics")
    public ResponseEntity<Map<String, Object>> getJdbcSinkMetrics() {
        Map<String, Object> rawMetrics = vehicleEventsJdbcSinkService.getJdbcSinkMetrics();
        Map<String, Object> adjustedMetrics = metricsBaselineService.getAdjustedMetrics("jdbc_sink", rawMetrics);
        return ResponseEntity.ok(adjustedMetrics);
    }
    
    @GetMapping("/jdbc-sink/health")
    public ResponseEntity<Map<String, Object>> getJdbcSinkHealth() {
        Map<String, Object> health = vehicleEventsJdbcSinkService.getHealthStatus();
        return ResponseEntity.ok(health);
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
    
    @GetMapping("/debug/jdbc/raw")
    public ResponseEntity<String> getDebugJdbcRaw() {
        try {
            // This endpoint will help debug what's actually being received from JDBC sink
            Map<String, Object> metrics = vehicleEventsJdbcSinkService.getJdbcSinkMetrics();
            return ResponseEntity.ok("Debug JDBC sink metrics: " + metrics.toString());
        } catch (Exception e) {
            return ResponseEntity.ok("Error fetching JDBC sink metrics: " + e.getMessage());
        }
    }
    
    @GetMapping("/debug/jdbc/prometheus")
    public ResponseEntity<String> getDebugJdbcPrometheus() {
        try {
            // This endpoint will show raw Prometheus metrics from JDBC sink
            String rawMetrics = vehicleEventsJdbcSinkService.getRawPrometheusMetrics();
            return ResponseEntity.ok(rawMetrics);
        } catch (Exception e) {
            return ResponseEntity.ok("Error fetching JDBC sink Prometheus metrics: " + e.getMessage());
        }
    }
    
    @PostMapping("/metrics/reset")
    public ResponseEntity<Map<String, Object>> resetMetrics() {
        try {
            metricsBaselineService.captureBaselines();
            
            Map<String, Object> response = Map.of(
                "status", "success",
                "message", "Metrics have been reset - counters will now show values relative to this baseline",
                "reset_timestamp", System.currentTimeMillis(),
                "reset_status", metricsBaselineService.getResetStatus()
            );
            
            log.info("Metrics reset requested and completed");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to reset metrics: {}", e.getMessage());
            
            Map<String, Object> response = Map.of(
                "status", "error",
                "message", "Failed to reset metrics: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
        }
    }
    
    @PostMapping("/metrics/clear-reset")
    public ResponseEntity<Map<String, Object>> clearReset() {
        try {
            metricsBaselineService.clearBaselines();
            
            Map<String, Object> response = Map.of(
                "status", "success",
                "message", "Reset cleared - metrics will now show original raw values",
                "timestamp", System.currentTimeMillis()
            );
            
            log.info("Metrics reset cleared");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to clear reset: {}", e.getMessage());
            
            Map<String, Object> response = Map.of(
                "status", "error",
                "message", "Failed to clear reset: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
        }
    }
    
    @GetMapping("/metrics/reset-status")
    public ResponseEntity<Map<String, Object>> getResetStatus() {
        Map<String, Object> status = metricsBaselineService.getResetStatus();
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/greenplum/health")
    public ResponseEntity<Map<String, Object>> getGreenplumHealth() {
        Map<String, Object> health = greenplumService.getHealthStatus();
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/safe-driver-scoring/fleet-summary")
    public ResponseEntity<Map<String, Object>> getFleetSafetySummary() {
        Map<String, Object> summary = greenplumService.getFleetSafetySummary();
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/safe-driver-scoring/top-performers")
    public ResponseEntity<List<Map<String, Object>>> getTopPerformers() {
        List<Map<String, Object>> drivers = greenplumService.getTopPerformers();
        return ResponseEntity.ok(drivers);
    }
    
    @GetMapping("/safe-driver-scoring/high-risk-drivers")
    public ResponseEntity<List<Map<String, Object>>> getHighRiskDrivers() {
        List<Map<String, Object>> drivers = greenplumService.getHighRiskDrivers();
        return ResponseEntity.ok(drivers);
    }
}