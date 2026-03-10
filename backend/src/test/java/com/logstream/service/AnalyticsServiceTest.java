package com.logstream.service;

import com.logstream.dto.CommonErrorResponse;
import com.logstream.dto.ErrorRateResponse;
import com.logstream.dto.LogVolumeResponse;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getErrorRatePerService_shouldCalculateCorrectRates() {
        when(logEntryRepository.findDistinctServiceNames())
                .thenReturn(Arrays.asList("auth-service", "payment-service"));

        List<Object[]> errors = List.of(
                new Object[]{"auth-service", 52L},
                new Object[]{"payment-service", 0L}
        );
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
                .thenReturn(errors);

        List<Object[]> totals = List.of(
                new Object[]{"auth-service", 1000L},
                new Object[]{"payment-service", 500L}
        );
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
                .thenReturn(totals);

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result).hasSize(2);
        ErrorRateResponse authService = result.stream()
                .filter(r -> r.getService().equals("auth-service"))
                .findFirst()
                .orElseThrow();
        assertThat(authService.getErrorRate()).isEqualTo(5.2);
        assertThat(authService.getErrorCount()).isEqualTo(52L);
        assertThat(authService.getTotalCount()).isEqualTo(1000L);
    }

    @Test
    void getErrorRatePerService_shouldReturnZeroForNoErrors() {
        when(logEntryRepository.findDistinctServiceNames())
                .thenReturn(Arrays.asList("payment-service"));
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        List<Object[]> totals = new ArrayList<>();
        totals.add(new Object[]{"payment-service", 500L});
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
                .thenReturn(totals);

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getErrorRate()).isEqualTo(0.0);
        assertThat(result.get(0).getErrorCount()).isEqualTo(0L);
    }


    @Test
    void getErrorRatePerService_shouldRoundToTwoDecimals() {
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[]{"test-service", 333L});

        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[]{"test-service", 17L});

        when(logEntryRepository.findDistinctServiceNames())
                .thenReturn(Collections.singletonList("test-service"));
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
                .thenReturn(totalCounts);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
                .thenReturn(errorCounts);

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result.get(0).getErrorRate()).isEqualTo(5.11);
    }

    @Test
    void getCommonErrors_shouldReturnTopErrorsByCount() {
        Instant start = Instant.parse("2026-03-06T00:00:00Z");
        Instant end = Instant.parse("2026-03-09T00:00:00Z");

        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{"Connection timeout", 123L});
        mockResults.add(new Object[]{"Invalid credentials", 87L});
        mockResults.add(new Object[]{"Database error", 45L});

        when(logEntryRepository.findCommonErrorsByServiceAndTimeRange(eq("auth-service"), any(Instant.class), any(Instant.class)))
                .thenReturn(mockResults);

        List<CommonErrorResponse> result = analyticsService.getCommonErrors("auth-service", 5, start, end);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getMessage()).isEqualTo("Connection timeout");
        assertThat(result.get(0).getCount()).isEqualTo(123L);
    }

    @Test
    void getCommonErrors_shouldRespectLimit() {
        Instant start = Instant.parse("2026-03-06T00:00:00Z");
        Instant end = Instant.parse("2026-03-09T00:00:00Z");

        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{"Error 1", 100L});
        mockResults.add(new Object[]{"Error 2", 80L});
        mockResults.add(new Object[]{"Error 3", 60L});

        when(logEntryRepository.findCommonErrorsByServiceAndTimeRange(eq("auth-service"), any(Instant.class), any(Instant.class)))
                .thenReturn(mockResults);

        List<CommonErrorResponse> result = analyticsService.getCommonErrors("auth-service", 2, start, end);

        assertThat(result).hasSize(2);
    }

    @Test
    void getCommonErrors_shouldReturnEmptyListWhenNoErrors() {
        Instant start = Instant.parse("2026-03-06T00:00:00Z");
        Instant end = Instant.parse("2026-03-09T00:00:00Z");

        when(logEntryRepository.findCommonErrorsByServiceAndTimeRange(eq("empty-service"), any(Instant.class), any(Instant.class)))
                .thenReturn(new ArrayList<>());

        List<CommonErrorResponse> result = analyticsService.getCommonErrors("empty-service", 10, start, end);

        assertThat(result).isEmpty();
    }

    @Test
    void getLogVolumeTimeSeries_shouldReturnHourlyAggregation() {
        Instant start = Instant.parse("2026-03-06T00:00:00Z");
        Instant end = Instant.parse("2026-03-06T03:00:00Z");

        Instant ts1 = Instant.parse("2026-03-06T00:00:00Z");
        Instant ts2 = Instant.parse("2026-03-06T01:00:00Z");

        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{Timestamp.from(ts1), "auth-service", 100L});
        mockResults.add(new Object[]{Timestamp.from(ts2), "auth-service", 150L});

        when(logEntryRepository.findHourlyVolume(eq("auth-service"), any(Instant.class), any(Instant.class)))
                .thenReturn(mockResults);

        List<LogVolumeResponse> result = analyticsService.getLogVolumeTimeSeries("auth-service", "hour", start, end);

        assertThat(result).isNotEmpty();
    }

    @Test
    void getLogVolumeTimeSeries_shouldReturnDailyAggregation() {
        Instant start = Instant.parse("2026-03-01T00:00:00Z");
        Instant end = Instant.parse("2026-03-09T00:00:00Z");

        Instant ts = Instant.parse("2026-03-06T00:00:00Z");
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{Timestamp.from(ts), "auth-service", 2400L});

        when(logEntryRepository.findDailyVolume(eq("auth-service"), any(Instant.class), any(Instant.class)))
                .thenReturn(mockResults);

        List<LogVolumeResponse> result = analyticsService.getLogVolumeTimeSeries("auth-service", "day", start, end);

        assertThat(result).isNotEmpty();
    }

    @Test
    void getLogVolumeTimeSeries_shouldFillMissingBucketsWithZero() {
        Instant start = Instant.parse("2026-03-06T00:00:00Z");
        Instant end = Instant.parse("2026-03-06T02:00:00Z");

        Instant ts = Instant.parse("2026-03-06T00:00:00Z");
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{Timestamp.from(ts), "auth-service", 100L});

        when(logEntryRepository.findHourlyVolume(eq("auth-service"), any(Instant.class), any(Instant.class)))
                .thenReturn(mockResults);

        List<LogVolumeResponse> result = analyticsService.getLogVolumeTimeSeries("auth-service", "hour", start, end);

        assertThat(result).anyMatch(r -> r.getCount() == 0L);
    }
}
