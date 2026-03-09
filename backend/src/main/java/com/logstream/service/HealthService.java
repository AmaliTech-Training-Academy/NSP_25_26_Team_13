package com.logstream.service;

import com.logstream.dto.HealthDashboardResponse;
import com.logstream.dto.ServiceHealthStatus;
import com.logstream.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final LogEntryRepository logEntryRepository;

    public List<HealthDashboardResponse> getHealthDashboard() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        
        Map<String, Instant> lastLogTimes = buildLastLogTimeMap(logEntryRepository.findLastLogTimePerService());
        Map<String, Double> errorRates = buildErrorRateMap(logEntryRepository.findErrorRateDataPerService(since));

        return lastLogTimes.entrySet().stream()
            .map(entry -> buildHealthResponse(entry.getKey(), entry.getValue(), errorRates.get(entry.getKey())))
            .collect(Collectors.toList());
    }

    private Map<String, Instant> buildLastLogTimeMap(List<Object[]> results) {
        Map<String, Instant> map = new HashMap<>();
        for (Object[] row : results) {
            map.put((String) row[0], (Instant) row[1]);
        }
        return map;
    }

    private Map<String, Double> buildErrorRateMap(List<Object[]> results) {
        Map<String, Double> map = new HashMap<>();
        for (Object[] row : results) {
            String service = (String) row[0];
            Long errorCount = ((Number) row[1]).longValue();
            Long totalCount = ((Number) row[2]).longValue();
            double rate = totalCount > 0
                ? BigDecimal.valueOf(errorCount * 100.0 / totalCount)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue()
                : 0.0;
            map.put(service, rate);
        }
        return map;
    }

    private HealthDashboardResponse buildHealthResponse(String service, Instant lastLogTime, Double errorRate) {
        ServiceHealthStatus status = determineStatus(errorRate, lastLogTime);
        return HealthDashboardResponse.builder()
            .service(service)
            .lastLogTime(lastLogTime)
            .errorRate(errorRate != null ? errorRate : 0.0)
            .status(status)
            .build();
    }

    private ServiceHealthStatus determineStatus(Double errorRate, Instant lastLogTime) {
        if (errorRate == null || lastLogTime == null) {
            return ServiceHealthStatus.UNKNOWN;
        }
        if (lastLogTime.isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
            return ServiceHealthStatus.UNKNOWN;
        }
        if (errorRate < 1.0) {
            return ServiceHealthStatus.GREEN;
        }
        if (errorRate <= 5.0) {
            return ServiceHealthStatus.YELLOW;
        }
        return ServiceHealthStatus.RED;
    }
}
