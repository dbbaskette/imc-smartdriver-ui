package com.insurancemegacorp.monitoring.controller;

import com.insurancemegacorp.monitoring.dto.PipelineMetrics;
import com.insurancemegacorp.monitoring.service.MetricsCollectorService;
import com.insurancemegacorp.monitoring.service.RabbitMetricsService;
import com.insurancemegacorp.monitoring.service.ExchangeMetricsService;
import lombok.extern.slf4j.Slf4j;
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

    public MetricsController(MetricsCollectorService metricsCollectorService, 
                           RabbitMetricsService rabbitMetricsService,
                           ExchangeMetricsService exchangeMetricsService) {
        this.metricsCollectorService = metricsCollectorService;
        this.rabbitMetricsService = rabbitMetricsService;
        this.exchangeMetricsService = exchangeMetricsService;
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
}