package com.logstream.service;

import com.logstream.dto.AnalyticsResponse;
import com.logstream.dto.ErrorRateResponse;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final LogEntryRepository logEntryRepository;

    public List<ErrorRateResponse> getErrorRatePerService() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        List<String> allServices = logEntryRepository.findDistinctServiceNames();
        List<Object[]> errorCounts = logEntryRepository.countErrorsByServiceAndCreatedAtAfter(since);
        List<Object[]> totalCounts = logEntryRepository.countByServiceAndCreatedAtAfter(since);

        Map<String, Long> errorMap = toMap(errorCounts);
        Map<String, Long> totalMap = toMap(totalCounts);

        List<ErrorRateResponse> responses = allServices.stream()
                .map(service -> buildErrorRate(service, errorMap, totalMap))
                .collect(Collectors.toList());

        log.debug("Error rate per service calculated for {} services", responses.size());
        return responses;
    }
    private ErrorRateResponse buildErrorRate(String service,
                                             Map<String, Long> errorMap,
                                             Map<String, Long> totalMap) {
        long errorCount = errorMap.getOrDefault(service, 0L);
        long totalCount = totalMap.getOrDefault(service, 0L);
        double rate = totalCount > 0
                ? BigDecimal.valueOf(errorCount * 100.0 / totalCount)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue()
                : 0.0;

        return ErrorRateResponse.builder()
                .service(service)
                .errorRate(rate)
                .errorCount(errorCount)
                .totalCount(totalCount)
                .build();
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], (Long) row[1]);
        }
        return map;
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
