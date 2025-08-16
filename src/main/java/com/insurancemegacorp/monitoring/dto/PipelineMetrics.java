package com.insurancemegacorp.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record PipelineMetrics(
    @JsonProperty("queue_depth") int queueDepth,
    @JsonProperty("hdfs_file_count") int hdfsFileCount,
    @JsonProperty("hdfs_bytes") long hdfsBytes,
    @JsonProperty("greenplum_row_count") long greenplumRowCount,
    @JsonProperty("latest_score") double latestScore,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("status") String status
) {
    
    public static PipelineMetrics mockData() {
        return new PipelineMetrics(
            (int) (Math.random() * 50) + 10, // Queue depth 10-60
            (int) (Math.random() * 1000) + 500, // File count 500-1500
            (long) (Math.random() * 1_000_000_000L) + 500_000_000L, // Bytes
            (long) (Math.random() * 10000) + 5000, // Row count
            75 + (Math.random() * 25), // Score 75-100
            Instant.now(),
            "mock"
        );
    }

    public static PipelineMetrics errorState() {
        return new PipelineMetrics(
            -1, -1, -1L, -1L, -1.0,
            Instant.now(),
            "error"
        );
    }
}