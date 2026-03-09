package com.logstream.service;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.dto.CommonErrorResponse;
import com.logstream.dto.CommonErrorsRequest;
import com.logstream.dto.LogVolumeResponse;
import com.logstream.dto.LogVolumeRequest;
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

    public List<CommonErrorResponse> getCommonErrors(CommonErrorsRequest request) {
        Instant since = request.getStartTime() != null
            ? Instant.ofEpochMilli(request.getStartTime())
            : Instant.now().minus(24, ChronoUnit.HOURS);
        
        Instant until = request.getEndTime() != null
            ? Instant.ofEpochMilli(request.getEndTime())
            : Instant.now();

        List<Object[]> results = logEntryRepository.findCommonErrorsByServiceAndTimeRange(
            request.getService(), since, until);

        return results.stream()
            .limit(request.getLimit())
            .map(row -> CommonErrorResponse.builder()
                .message((String) row[0])
                .count((Long) row[1])
                .build())
            .collect(Collectors.toList());
    }

    public List<LogVolumeResponse> getLogVolume(LogVolumeRequest request) {
        Instant startTime = request.getStartTime() != null
            ? Instant.ofEpochMilli(request.getStartTime())
            : Instant.now().minus(7, ChronoUnit.DAYS);
        
        Instant endTime = request.getEndTime() != null
            ? Instant.ofEpochMilli(request.getEndTime())
            : Instant.now();

        List<Object[]> results = logEntryRepository.findLogVolumeByServiceAndGranularity(
            request.getService(), request.getGranularity(), startTime, endTime);

        return fillMissingBuckets(results, request.getService(), request.getGranularity(), startTime, endTime);
    }

    private List<LogVolumeResponse> fillMissingBuckets(List<Object[]> results, String service, 
        String granularity, Instant startTime, Instant endTime) {
        Map<Instant, Long> resultMap = new HashMap<>();
        for (Object[] row : results) {
            Instant ts = ((Timestamp) row[0]).toInstant();
            Long count = ((Number) row[2]).longValue();
            resultMap.put(ts, count);
        }

        List<LogVolumeResponse> response = new ArrayList<>();
        Instant current = startTime;
        long step = "hour".equals(granularity) ? 3600 : 86400;
        
        while (current.isBefore(endTime) || current.equals(endTime)) {
            Long count = resultMap.getOrDefault(current, 0L);
            response.add(LogVolumeResponse.builder()
                .timestamp(current)
                .service(service)
                .count(count)
                .build());
            current = current.plusSeconds(step);
        }
        
        return response;
    }

    public Map<String, Long> getLogVolumeOld(int hours) {
        throw new UnsupportedOperationException("Log volume not yet implemented");
    }

    public List<Map<String, Object>> getTopErrors(int limit) {
        throw new UnsupportedOperationException("Top errors not yet implemented");
    }

    public Map<String, String> getServiceHealth() {
        throw new UnsupportedOperationException("Service health not yet implemented");
    }
}
