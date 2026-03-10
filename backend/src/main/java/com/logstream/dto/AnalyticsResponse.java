package com.logstream.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsResponse {
    private Map<String, Double> errorRates;
    private Map<String, Long> logVolume;
    private List<CommonErrorResponse> topErrors;
    private Map<String, String> serviceHealth;
}
