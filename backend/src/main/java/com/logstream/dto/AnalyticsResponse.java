package com.logstream.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalyticsResponse {
    private Map<String, Double> errorRateByService;
    private Map<String, Long> logVolumeByHour;
    private List<Map<String, Object>> topErrors;
    private Map<String, String> serviceHealth;
}
