package com.logstream.service;

import com.logstream.dto.CommonErrorResponse;
import com.logstream.dto.CommonErrorsRequest;
import com.logstream.dto.LogVolumeResponse;
import com.logstream.dto.LogVolumeRequest;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getCommonErrors_shouldReturnTopErrorsByCount() {
        CommonErrorsRequest request = new CommonErrorsRequest("auth-service", 5, null, null);
        
        List<Object[]> mockResults = Arrays.asList(
            new Object[]{"Connection timeout", 123L},
            new Object[]{"Invalid credentials", 87L},
            new Object[]{"Database error", 45L}
        );
        
        when(logEntryRepository.findCommonErrorsByServiceAndTimeRange(eq("auth-service"), any(Instant.class), any(Instant.class)))
            .thenReturn(mockResults);

        List<CommonErrorResponse> result = analyticsService.getCommonErrors(request);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getMessage()).isEqualTo("Connection timeout");
        assertThat(result.get(0).getCount()).isEqualTo(123L);
    }

    @Test
    void getCommonErrors_shouldRespectLimit() {
        CommonErrorsRequest request = new CommonErrorsRequest("auth-service", 2, null, null);
        
        List<Object[]> mockResults = Arrays.asList(
            new Object[]{"Error 1", 100L},
            new Object[]{"Error 2", 80L},
            new Object[]{"Error 3", 60L}
        );
        
        when(logEntryRepository.findCommonErrorsByServiceAndTimeRange(eq("auth-service"), any(Instant.class), any(Instant.class)))
            .thenReturn(mockResults);

        List<CommonErrorResponse> result = analyticsService.getCommonErrors(request);

        assertThat(result).hasSize(2);
    }

    @Test
    void getCommonErrors_shouldReturnEmptyListWhenNoErrors() {
        CommonErrorsRequest request = new CommonErrorsRequest("empty-service", 10, null, null);
        
        when(logEntryRepository.findCommonErrorsByServiceAndTimeRange(eq("empty-service"), any(Instant.class), any(Instant.class)))
            .thenReturn(Collections.emptyList());

        List<CommonErrorResponse> result = analyticsService.getCommonErrors(request);

        assertThat(result).isEmpty();
    }

    @Test
    void getLogVolume_shouldReturnHourlyAggregation() {
        LogVolumeRequest request = new LogVolumeRequest("auth-service", "hour", null, null);
        
        Instant ts1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant ts2 = Instant.parse("2024-01-01T01:00:00Z");
        
        List<Object[]> mockResults = Arrays.asList(
            new Object[]{Timestamp.from(ts1), "auth-service", 100L},
            new Object[]{Timestamp.from(ts2), "auth-service", 150L}
        );
        
        when(logEntryRepository.findLogVolumeByServiceAndGranularity(
            eq("auth-service"), eq("hour"), any(Instant.class), any(Instant.class)))
            .thenReturn(mockResults);

        List<LogVolumeResponse> result = analyticsService.getLogVolume(request);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getCount()).isEqualTo(100L);
    }

    @Test
    void getLogVolume_shouldReturnDailyAggregation() {
        LogVolumeRequest request = new LogVolumeRequest("auth-service", "day", null, null);
        
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        List<Object[]> mockResults = Arrays.asList(
            new Object[]{Timestamp.from(ts), "auth-service", 2400L}
        );
        
        when(logEntryRepository.findLogVolumeByServiceAndGranularity(
            eq("auth-service"), eq("day"), any(Instant.class), any(Instant.class)))
            .thenReturn(mockResults);

        List<LogVolumeResponse> result = analyticsService.getLogVolume(request);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getCount()).isEqualTo(2400L);
    }

    @Test
    void getLogVolume_shouldFillMissingBucketsWithZero() {
        LogVolumeRequest request = new LogVolumeRequest("auth-service", "hour", null, null);
        
        Instant ts = Instant.parse("2024-01-01T00:00:00Z");
        List<Object[]> mockResults = Arrays.asList(
            new Object[]{Timestamp.from(ts), "auth-service", 100L}
        );
        
        when(logEntryRepository.findLogVolumeByServiceAndGranularity(
            eq("auth-service"), eq("hour"), any(Instant.class), any(Instant.class)))
            .thenReturn(mockResults);

        List<LogVolumeResponse> result = analyticsService.getLogVolume(request);

        assertThat(result).anySatisfy(r -> assertThat(r.getCount()).isEqualTo(0L));
    }
}
