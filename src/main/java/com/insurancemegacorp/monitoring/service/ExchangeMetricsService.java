package com.insurancemegacorp.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class ExchangeMetricsService {

    private final RabbitMetricsService rabbitMetricsService;
    
    // Rolling totals since UI startup
    private final AtomicLong totalPublishIn = new AtomicLong(0);
    private final AtomicLong totalPublishOut = new AtomicLong(0);
    private final AtomicLong currentPublishInRate = new AtomicLong(0);
    private final AtomicLong currentPublishOutRate = new AtomicLong(0);
    
    // Track previous values to calculate deltas
    private long previousPublishIn = 0;
    private long previousPublishOut = 0;
    private boolean initialized = false;

    @Autowired
    public ExchangeMetricsService(RabbitMetricsService rabbitMetricsService) {
        this.rabbitMetricsService = rabbitMetricsService;
    }

    public Map<String, Object> getExchangeThroughputStats() {
        Map<String, Object> exchangeStats = rabbitMetricsService.getExchangeStats("telematics_exchange");
        
        if (exchangeStats.isEmpty()) {
            return createEmptyStats();
        }

        // Extract message stats
        Map<String, Object> messageStats = (Map<String, Object>) exchangeStats.get("message_stats");
        if (messageStats == null) {
            return createEmptyStats();
        }

        // Get current totals from RabbitMQ
        long currentPublishIn = getLongValue(messageStats, "publish_in");
        long currentPublishOut = getLongValue(messageStats, "publish_out");
        
        // Get current rates from RabbitMQ
        Map<String, Object> publishInDetails = (Map<String, Object>) messageStats.get("publish_in_details");
        Map<String, Object> publishOutDetails = (Map<String, Object>) messageStats.get("publish_out_details");
        
        double publishInRate = publishInDetails != null ? getDoubleValue(publishInDetails, "rate") : 0.0;
        double publishOutRate = publishOutDetails != null ? getDoubleValue(publishOutDetails, "rate") : 0.0;

        if (!initialized) {
            // First run - initialize totals
            previousPublishIn = currentPublishIn;
            previousPublishOut = currentPublishOut;
            totalPublishIn.set(0);
            totalPublishOut.set(0);
            initialized = true;
            log.info("Exchange metrics initialized. Starting counters at publish_in={}, publish_out={}", 
                    currentPublishIn, currentPublishOut);
        } else {
            // Calculate deltas since last check
            long deltaIn = Math.max(0, currentPublishIn - previousPublishIn);
            long deltaOut = Math.max(0, currentPublishOut - previousPublishOut);
            
            // Add deltas to rolling totals
            totalPublishIn.addAndGet(deltaIn);
            totalPublishOut.addAndGet(deltaOut);
            
            // Update previous values
            previousPublishIn = currentPublishIn;
            previousPublishOut = currentPublishOut;
        }
        
        // Update current rates
        currentPublishInRate.set(Math.round(publishInRate * 10)); // Store as tenths for precision
        currentPublishOutRate.set(Math.round(publishOutRate * 10));

        // Return stats
        Map<String, Object> result = new HashMap<>();
        result.put("total_publish_in", totalPublishIn.get());
        result.put("total_publish_out", totalPublishOut.get());
        result.put("current_rate_in", publishInRate);
        result.put("current_rate_out", publishOutRate);
        result.put("amplification_ratio", currentPublishIn > 0 ? (double) currentPublishOut / currentPublishIn : 0.0);
        
        return result;
    }

    private Map<String, Object> createEmptyStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("total_publish_in", 0L);
        result.put("total_publish_out", 0L);
        result.put("current_rate_in", 0.0);
        result.put("current_rate_out", 0.0);
        result.put("amplification_ratio", 0.0);
        return result;
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