package com.logstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Error rate for a service over the last 24 hours")
public class ErrorRateResponse {

    @Schema(description = "Service name", example = "auth-service")
    private String service;

    @Schema(description = "Error rate percentage (0-100), rounded to 2 decimal places", example = "5.2")
    private double errorRate;

    @Schema(description = "Number of ERROR-level logs in the last 24 hours", example = "52")
    private long errorCount;

    @Schema(description = "Total number of logs in the last 24 hours", example = "1000")
    private long totalCount;


}
