package com.logstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Log volume data point for a time bucket")
public class LogVolumeResponse {

    @Schema(description = "Start of the time bucket", example = "2026-03-03T00:00:00Z")
    private Instant timestamp;

    @Schema(description = "Service name", example = "auth-service")
    private String service;

    @Schema(description = "Number of log entries in this time bucket", example = "42")
    private long count;
}
