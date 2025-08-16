package com.insurancemegacorp.monitoring.service;

import com.insurancemegacorp.monitoring.dto.PipelineMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class MetricsCollectorService {

    private final RabbitMetricsService rabbitMetricsService;
    private final String metricsMode;
    
    // Cache for last known values
    private int lastKnownQueueDepth = 0;
    private PipelineMetrics lastMetrics;

    public MetricsCollectorService(
            RabbitMetricsService rabbitMetricsService,
            @Value("${metrics.mode:mock}") String metricsMode) {
        this.rabbitMetricsService = rabbitMetricsService;
        this.metricsMode = metricsMode;
        log.info("MetricsCollectorService initialized in {} mode", metricsMode);
    }

    @Scheduled(fixedRateString = "${metrics.collection.interval:2000}")
    public void collectMetrics() {
        try {
            PipelineMetrics metrics = "real".equalsIgnoreCase(metricsMode) 
                ? collectRealMetrics() 
                : PipelineMetrics.mockData();
            
            this.lastMetrics = metrics;
            log.debug("Collected metrics: queue={}, mode={}", metrics.queueDepth(), metricsMode);
            
            // TODO: Broadcast via WebSocket in next phase
            
        } catch (Exception e) {
            log.error("Error collecting metrics: {}", e.getMessage());
            this.lastMetrics = PipelineMetrics.errorState();
        }
    }

    private PipelineMetrics collectRealMetrics() {
        // Collect RabbitMQ metrics
        int queueDepth = rabbitMetricsService.getQueueDepth();
        if (queueDepth >= 0) {
            lastKnownQueueDepth = queueDepth;
        } else {
            // Use last known value if current fetch failed
            queueDepth = lastKnownQueueDepth;
        }

        // TODO: Collect HDFS metrics
        int hdfsFileCount = (int) (Math.random() * 100) + 50; // Placeholder
        long hdfsBytes = (long) (Math.random() * 1_000_000_000L) + 100_000_000L; // Placeholder

        // TODO: Collect Greenplum metrics  
        long greenplumRowCount = (long) (Math.random() * 1000) + 500; // Placeholder
        double latestScore = 75 + (Math.random() * 25); // Placeholder

        return new PipelineMetrics(
            queueDepth,
            hdfsFileCount,
            hdfsBytes,
            greenplumRowCount,
            latestScore,
            Instant.now(),
            "real"
        );
    }

    public PipelineMetrics getCurrentMetrics() {
        return lastMetrics != null ? lastMetrics : PipelineMetrics.mockData();
    }

    public boolean isRabbitMQHealthy() {
        return "mock".equalsIgnoreCase(metricsMode) || rabbitMetricsService.isRabbitMQHealthy();
    }
}