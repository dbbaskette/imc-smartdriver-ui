package com.insurancemegacorp.monitoring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to manage metric baselines for "resetting" counters.
 * Since we can't actually reset external counters (Prometheus, RabbitMQ),
 * we capture baseline values and subtract them from current metrics for display.
 */
@Slf4j
@Service
public class MetricsBaselineService {

    private final Map<String, Double> baselines = new HashMap<>();
    private long baselineTimestamp = 0;

    @Autowired
    private TelemetryGeneratorMetricsService telemetryGeneratorMetricsService;

    @Autowired
    private TelemetryProcessorMetricsService telemetryProcessorMetricsService;

    @Autowired
    private TelemematicsExchangeMetricsService telemematicsExchangeMetricsService;

    @Autowired
    private VehicleEventsJdbcSinkService vehicleEventsJdbcSinkService;

    @Autowired
    private ExchangeMetricsService exchangeMetricsService;

    /**
     * Capture current metric values as baselines for future calculations
     */
    public void captureBaselines() {
        log.info("Capturing metric baselines for reset functionality...");
        
        try {
            // Capture telemetry generator baselines
            Map<String, Object> telemetryMetrics = telemetryGeneratorMetricsService.getPublishingMetrics();
            if (telemetryMetrics != null && !telemetryMetrics.containsKey("error")) {
                baselines.put("telemetry_messages_published_total", getDoubleValue(telemetryMetrics, "messages_published_total"));
                baselines.put("telemetry_messages_rate_per_sec", getDoubleValue(telemetryMetrics, "messages_rate_per_sec"));
                log.debug("Captured telemetry generator baselines");
            }

            // Capture events processor baselines
            Map<String, Object> processorMetrics = telemetryProcessorMetricsService.getProcessorMetrics();
            if (processorMetrics != null && !processorMetrics.containsKey("error")) {
                baselines.put("processor_messages_in", getDoubleValue(processorMetrics, "messages_in"));
                baselines.put("processor_events_captured", getDoubleValue(processorMetrics, "events_captured"));
                baselines.put("processor_messages_out", getDoubleValue(processorMetrics, "messages_out"));
                baselines.put("processor_invalid_messages", getDoubleValue(processorMetrics, "invalid_messages"));
                log.debug("Captured processor baselines");
            }

            // Capture HDFS Sink baselines
            Map<String, Object> hdfsMetrics = telemematicsExchangeMetricsService.getHdfsSinkMetrics();
            if (hdfsMetrics != null && !hdfsMetrics.containsKey("error")) {
                baselines.put("hdfs_messages_in", getDoubleValue(hdfsMetrics, "messages_in"));
                baselines.put("hdfs_files_written", getDoubleValue(hdfsMetrics, "files_written"));
                log.debug("Captured HDFS Sink baselines");
            }

            // Capture JDBC Sink baselines
            Map<String, Object> jdbcMetrics = vehicleEventsJdbcSinkService.getJdbcSinkMetrics();
            if (jdbcMetrics != null && !jdbcMetrics.containsKey("error")) {
                baselines.put("jdbc_rows_inserted", getDoubleValue(jdbcMetrics, "rows_inserted"));
                baselines.put("jdbc_database_errors", getDoubleValue(jdbcMetrics, "database_errors"));
                log.debug("Captured JDBC Sink baselines");
            }

            // Capture Exchange throughput baselines
            Map<String, Object> exchangeMetrics = exchangeMetricsService.getExchangeThroughputStats();
            if (exchangeMetrics != null && !exchangeMetrics.containsKey("error")) {
                baselines.put("exchange_total_publish_in", getDoubleValue(exchangeMetrics, "total_publish_in"));
                baselines.put("exchange_total_publish_out", getDoubleValue(exchangeMetrics, "total_publish_out"));
                log.debug("Captured exchange throughput baselines");
            }

            baselineTimestamp = System.currentTimeMillis();
            
            log.info("Successfully captured {} metric baselines at timestamp {}", baselines.size(), baselineTimestamp);
            
        } catch (Exception e) {
            log.error("Failed to capture metric baselines: {}", e.getMessage(), e);
        }
    }

    /**
     * Get baseline-adjusted metrics for display
     */
    public Map<String, Object> getAdjustedMetrics(String component, Map<String, Object> rawMetrics) {
        if (rawMetrics == null || baselineTimestamp == 0) {
            return rawMetrics; // No baselines captured yet
        }

        Map<String, Object> adjustedMetrics = new HashMap<>(rawMetrics);

        try {
            switch (component) {
                case "telemetry_generator":
                    adjustValue(adjustedMetrics, "messages_published_total", "telemetry_messages_published_total");
                    adjustValue(adjustedMetrics, "messages_rate_per_sec", "telemetry_messages_rate_per_sec");
                    break;

                case "processor":
                    adjustValue(adjustedMetrics, "messages_in", "processor_messages_in");
                    adjustValue(adjustedMetrics, "events_captured", "processor_events_captured");
                    adjustValue(adjustedMetrics, "messages_out", "processor_messages_out");
                    adjustValue(adjustedMetrics, "invalid_messages", "processor_invalid_messages");
                    break;

                case "hdfs_sink":
                    adjustValue(adjustedMetrics, "messages_in", "hdfs_messages_in");
                    adjustValue(adjustedMetrics, "files_written", "hdfs_files_written");
                    break;

                case "jdbc_sink":
                    adjustValue(adjustedMetrics, "rows_inserted", "jdbc_rows_inserted");
                    adjustValue(adjustedMetrics, "database_errors", "jdbc_database_errors");
                    break;

                case "exchange":
                    adjustValue(adjustedMetrics, "total_publish_in", "exchange_total_publish_in");
                    adjustValue(adjustedMetrics, "total_publish_out", "exchange_total_publish_out");
                    break;
            }

            // Add baseline info to the response
            adjustedMetrics.put("baseline_captured", true);
            adjustedMetrics.put("baseline_timestamp", baselineTimestamp);

        } catch (Exception e) {
            log.warn("Failed to adjust metrics for component {}: {}", component, e.getMessage());
        }

        return adjustedMetrics;
    }

    /**
     * Get reset status and baseline information
     */
    public Map<String, Object> getResetStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("baselines_captured", baselineTimestamp > 0);
        status.put("baseline_timestamp", baselineTimestamp);
        status.put("baseline_count", baselines.size());
        status.put("baseline_keys", baselines.keySet());
        
        if (baselineTimestamp > 0) {
            long timeSinceReset = System.currentTimeMillis() - baselineTimestamp;
            status.put("time_since_reset_ms", timeSinceReset);
            status.put("time_since_reset_seconds", timeSinceReset / 1000);
        }
        
        return status;
    }

    /**
     * Clear all baselines (return to showing raw metrics)
     */
    public void clearBaselines() {
        baselines.clear();
        baselineTimestamp = 0;
        log.info("Cleared all metric baselines - returning to raw metrics display");
    }

    private void adjustValue(Map<String, Object> metrics, String key, String baselineKey) {
        if (metrics.containsKey(key) && baselines.containsKey(baselineKey)) {
            double currentValue = getDoubleValue(metrics, key);
            double baselineValue = baselines.get(baselineKey);
            double adjustedValue = Math.max(0, currentValue - baselineValue); // Don't go negative
            
            metrics.put(key, Math.round(adjustedValue));
            log.debug("Adjusted {}: {} - {} = {}", key, currentValue, baselineValue, adjustedValue);
        }
    }

    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}