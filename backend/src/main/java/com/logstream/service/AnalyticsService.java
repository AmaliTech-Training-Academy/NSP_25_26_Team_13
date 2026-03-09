package com.logstream.service;

import com.logstream.dto.AnalyticsResponse;
import com.logstream.dto.ErrorRateResponse;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final LogEntryRepository logEntryRepository;

    public List<ErrorRateResponse> getErrorRateByService() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        
        Map<String, Long> totalCountsByService = buildServiceCountMap(
            logEntryRepository.countByServiceSince(since)
        );
        
        Map<String, Long> errorCountsByService = buildServiceCountMap(
            logEntryRepository.countByLevelAndServiceSince(LogLevel.ERROR, since)
        );
        
        return totalCountsByService.entrySet().stream()
            .map(entry -> buildErrorRateResponse(
                entry.getKey(),
                errorCountsByService.getOrDefault(entry.getKey(), 0L),
                entry.getValue()
            ))
            .collect(Collectors.toList());
    }

    private Map<String, Long> buildServiceCountMap(List<Object[]> results) {
        return results.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> (Long) row[1]
            ));
    }

    private ErrorRateResponse buildErrorRateResponse(String service, Long errorCount, Long totalCount) {
        double errorRate = calculateErrorRate(errorCount, totalCount);
        
        return ErrorRateResponse.builder()
            .service(service)
            .errorRate(errorRate)
            .errorCount(errorCount)
            .totalCount(totalCount)
            .build();
    }

    private double calculateErrorRate(Long errorCount, Long totalCount) {
        if (totalCount == 0) {
            return 0.0;
        }
        double rate = (errorCount * 100.0) / totalCount;
        return Math.round(rate * 100.0) / 100.0;
    }

    // TODO (Dev C): Group logs by hour, count
    public Map<String, Long> getLogVolume(int hours) {
        throw new UnsupportedOperationException("Log volume not yet implemented");
    }

    // TODO (Dev C): Group ERROR messages, count, order desc
    public List<Map<String, Object>> getTopErrors(int limit) {
        throw new UnsupportedOperationException("Top errors not yet implemented");
    }

    // TODO (Dev C): error rate > 10% UNHEALTHY, > 5% DEGRADED, else HEALTHY
    public Map<String, String> getServiceHealth() {
        throw new UnsupportedOperationException("Service health not yet implemented");
    }
}
