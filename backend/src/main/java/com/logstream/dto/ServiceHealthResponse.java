package com.logstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Service health status for the dashboard")
public class ServiceHealthResponse {

    @Schema(description = "Service name", example = "auth-service")
    private String service;

    @Schema(description = "Timestamp of the most recent log entry", example = "2026-03-04T12:00:00Z")
    private Instant lastLogTime;

    @Schema(description = "Error rate percentage over the last 24 hours", example = "2.3")
    private double errorRate;

    @Schema(description = "Health status: GREEN (<1%), YELLOW (1-5%), RED (>5%), UNKNOWN (no logs in 24h)", example = "YELLOW", allowableValues = {
            "GREEN", "YELLOW", "RED", "UNKNOWN" })
    private String status;
}
