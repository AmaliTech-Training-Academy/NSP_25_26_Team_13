package com.logstream.service;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.dto.CommonErrorResponse;
import com.logstream.dto.CommonErrorsRequest;
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
public class AnalyticsService {

    private final LogEntryRepository logEntryRepository;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    public List<ErrorRateResponse> getErrorRatePerService() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        List<String> allServices = logEntryRepository.findDistinctServiceNames();
        Map<String, Long> errorMap = toMap(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(since));
        Map<String, Long> totalMap = toMap(logEntryRepository.countByServiceAndCreatedAtAfter(since));

        return allServices.stream()
            .map(service -> buildErrorRate(service, errorMap, totalMap))
            .collect(Collectors.toList());
    }

    private ErrorRateResponse buildErrorRate(String service, Map<String, Long> errorMap, Map<String, Long> totalMap) {
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

    public List<CommonErrorResponse> getCommonErrors(String service, Integer limit,
                                                     Instant startTime, Instant endTime) {
        int effectiveLimit = resolveLimit(limit);
        Instant end = endTime != null ? endTime : Instant.now();
        Instant start = startTime != null ? startTime : end.minus(24, ChronoUnit.HOURS);

        List<Object[]> results = logEntryRepository
                .findCommonErrorsByServiceAndTimeRange(service, start, end);

        List<CommonErrorResponse> responses = results.stream()
                .limit(effectiveLimit)
                .map(row -> CommonErrorResponse.builder()
                        .message((String) row[0])
                        .count((Long) row[1])
                        .build())
                .collect(Collectors.toList());

        return responses;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "Limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

}
