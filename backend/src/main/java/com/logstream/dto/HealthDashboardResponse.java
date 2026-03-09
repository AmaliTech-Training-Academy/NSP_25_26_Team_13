package com.logstream.dto;

import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthDashboardResponse {
    private String service;
    private Instant lastLogTime;
    private Double errorRate;
    private ServiceHealthStatus status;
}
