package com.logstream.service;

import com.logstream.dto.ServiceHealthResponse;
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

public class HealthService {
    private final LogEntryRepository logEntryRepository;

    /**
     * Returns a health dashboard for all services.
     * For each service: last log timestamp, 24h error rate, and status.
     * Status thresholds: GREEN (<1%), YELLOW (1-5%), RED (>5%), UNKNOWN (no logs in
     * 24h).
     * Uses aggregated DB queries — never loads entire dataset into memory.
     */
    public List<ServiceHealthResponse> getHealthDashboard() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        List<String> allServices = logEntryRepository.findDistinctServiceNames();
        Map<String, Instant> lastLogMap = buildLastLogMap();
        Map<String, Long> errorMap = toMap(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(since));
        Map<String, Long> totalMap = toMap(logEntryRepository.countByServiceAndCreatedAtAfter(since));

        List<ServiceHealthResponse> responses = allServices.stream()
                .map(service -> buildServiceHealth(service, lastLogMap, errorMap, totalMap, since))
                .collect(Collectors.toList());

        log.debug("Health dashboard generated for {} services", responses.size());
        return responses;
    }

    private ServiceHealthResponse buildServiceHealth(String service,
                                                     Map<String, Instant> lastLogMap,
                                                     Map<String, Long> errorMap,
                                                     Map<String, Long> totalMap,
                                                     Instant since) {
        Instant lastLogTime = lastLogMap.get(service);
        long totalCount = totalMap.getOrDefault(service, 0L);

        if (totalCount == 0) {
            return ServiceHealthResponse.builder()
                    .service(service)
                    .lastLogTime(lastLogTime)
                    .errorRate(0.0)
                    .status("UNKNOWN")
                    .build();
        }

        long errorCount = errorMap.getOrDefault(service, 0L);
        double errorRate = BigDecimal.valueOf(errorCount * 100.0 / totalCount)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        return ServiceHealthResponse.builder()
                .service(service)
                .lastLogTime(lastLogTime)
                .errorRate(errorRate)
                .status(resolveStatus(errorRate))
                .build();
    }

    /**
     * Determines health status from error rate.
     * GREEN: <1%, YELLOW: 1–5%, RED: >5%.
     */
    public String resolveStatus(double errorRate) {
        if (errorRate > 5.0) {
            return "RED";
        }
        if (errorRate >= 1.0) {
            return "YELLOW";
        }
        return "GREEN";
    }


    private Map<String, Instant> buildLastLogMap() {
        List<Object[]> rows = logEntryRepository.findLastLogTimestampByService();
        Map<String, Instant> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], (Instant) row[1]);
        }
        return map;
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], (Long) row[1]);
        }
        return map;
    }
}
