package com.logstream.service;

import com.logstream.dto.CommonErrorResponse;
import com.logstream.dto.ErrorRateResponse;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        when(logEntryRepository.findDistinctServiceNames())
            .thenReturn(Arrays.asList("test-service"));

        List<Object[]> errors = new ArrayList<>();
        errors.add(new Object[]{"test-service", 17L});
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(errors);

        List<Object[]> totals = new ArrayList<>();
        totals.add(new Object[]{"test-service", 333L});
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(totals);

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result.get(0).getErrorRate()).isEqualTo(5.11);
    }

    @Test
    void getCommonErrors_shouldReturnTopErrorsByCount() {
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{"Connection timeout", 123L});
        mockResults.add(new Object[]{"Invalid credentials", 87L});
        mockResults.add(new Object[]{"Database error", 45L});

        when(logEntryRepository.findCommonErrorsByServiceAndTimeRange(eq("auth-service"), any(Instant.class), any(Instant.class)))
            .thenReturn(mockResults);

        List<CommonErrorResponse> result = analyticsService.getCommonErrors("auth-service", 10, null, null);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getMessage()).isEqualTo("Connection timeout");
        assertThat(result.get(0).getCount()).isEqualTo(123L);
        assertThat(result.get(1).getMessage()).isEqualTo("Invalid credentials");
        assertThat(result.get(1).getCount()).isEqualTo(87L);
    }

    @Test
    void getCommonErrors_shouldRespectLimit() {
        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{"Error 1", 100L});
        mockResults.add(new Object[]{"Error 2", 80L});
        mockResults.add(new Object[]{"Error 3", 60L});

        when(logEntryRepository.findCommonErrorsByServiceAndTimeRange(eq("auth-service"), any(Instant.class), any(Instant.class)))
            .thenReturn(mockResults);

        List<CommonErrorResponse> result = analyticsService.getCommonErrors("auth-service", 2, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMessage()).isEqualTo("Error 1");
        assertThat(result.get(1).getMessage()).isEqualTo("Error 2");
    }

    @Test
    void getCommonErrors_shouldReturnEmptyListWhenNoErrors() {
        when(logEntryRepository.findCommonErrorsByServiceAndTimeRange(eq("empty-service"), any(Instant.class), any(Instant.class)))
            .thenReturn(Collections.emptyList());

        List<CommonErrorResponse> result = analyticsService.getCommonErrors("empty-service", 10, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getCommonErrors_shouldUseCustomTimeRangeWhenProvided() {
        Instant start = Instant.ofEpochMilli(1609459200000L);
        Instant end = Instant.ofEpochMilli(1609545600000L);

        List<Object[]> mockResults = new ArrayList<>();
        mockResults.add(new Object[]{"Timeout", 50L});

        when(logEntryRepository.findCommonErrorsByServiceAndTimeRange(eq("auth-service"), eq(start), eq(end)))
            .thenReturn(mockResults);

        List<CommonErrorResponse> result = analyticsService.getCommonErrors("auth-service", 10, start, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("Timeout");
    }
}
