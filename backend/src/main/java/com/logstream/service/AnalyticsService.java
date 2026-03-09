package com.logstream.service;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.dto.CommonErrorResponse;
import com.logstream.dto.LogVolumeResponse;
import com.logstream.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
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
    private static final int DEFAULT_VOLUME_DAYS = 7;
    private static final Set<String> VALID_GRANULARITIES = Set.of("hour", "day");

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



    /**
     * Returns log volume time series aggregated by hour or day for a given service.
     * Uses native PostgreSQL date_trunc for efficient grouping.
     * Missing time buckets are filled with count: 0.
     * Time range defaults to last 7 days if not specified.
     */
    public List<LogVolumeResponse> getLogVolumeTimeSeries(String service, String granularity,
                                                          Instant startTime, Instant endTime) {
        if (!VALID_GRANULARITIES.contains(granularity)) {
            throw new IllegalArgumentException(
                    "Granularity must be 'hour' or 'day'");
        }

        Instant end = endTime != null ? endTime : Instant.now();
        Instant start = startTime != null ? startTime
                : end.minus(DEFAULT_VOLUME_DAYS, ChronoUnit.DAYS);

        List<Object[]> results = "hour".equals(granularity)
                ? logEntryRepository.findHourlyVolume(service, start, end)
                : logEntryRepository.findDailyVolume(service, start, end);

        Map<Instant, Long> countsByBucket = new LinkedHashMap<>();
        for (Object[] row : results) {
            Instant bucket = toInstant(row[0]);
            Long count = ((Number) row[2]).longValue();
            countsByBucket.put(bucket, count);
        }

        return fillGaps(service, granularity, start, end, countsByBucket);
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toInstant();
        }
        if (value instanceof Instant) {
            return (Instant) value;
        }
        throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
    }

    private List<LogVolumeResponse> fillGaps(String service, String granularity,
                                             Instant start, Instant end, Map<Instant, Long> countsByBucket) {
        ChronoUnit unit = "hour".equals(granularity) ? ChronoUnit.HOURS : ChronoUnit.DAYS;
        Instant truncatedStart = truncate(start, granularity);

        List<LogVolumeResponse> responses = new ArrayList<>();
        Instant current = truncatedStart;

        while (!current.isAfter(end)) {
            long count = countsByBucket.getOrDefault(current, 0L);
            responses.add(LogVolumeResponse.builder()
                    .timestamp(current)
                    .service(service)
                    .count(count)
                    .build());
            current = current.plus(1, unit);
        }

        return responses;
    }

    private Instant truncate(Instant instant, String granularity) {
        if ("hour".equals(granularity)) {
            return instant.truncatedTo(ChronoUnit.HOURS);
        }
        return instant.truncatedTo(ChronoUnit.DAYS);
    }


}
