package com.logstream.service;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Arrays;
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

    @BeforeEach
    void setUp() {
        List<Object[]> totalCounts = Arrays.asList(
            new Object[]{"auth-service", 1000L},
            new Object[]{"payment-service", 500L}
        );

        List<Object[]> errorCounts = Arrays.asList(
            new Object[]{"auth-service", 52L}
        );

        when(logEntryRepository.countByServiceSince(any(Instant.class)))
            .thenReturn(totalCounts);
        when(logEntryRepository.countByLevelAndServiceSince(eq(LogLevel.ERROR), any(Instant.class)))
            .thenReturn(errorCounts);
    }

    @Test
    void getErrorRateByService_shouldCalculateCorrectRates() {
        List<ErrorRateResponse> result = analyticsService.getErrorRateByService();

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
    void getErrorRateByService_shouldReturnZeroForNoErrors() {
        List<ErrorRateResponse> result = analyticsService.getErrorRateByService();

        ErrorRateResponse paymentService = result.stream()
            .filter(r -> r.getService().equals("payment-service"))
            .findFirst()
            .orElseThrow();
        
        assertThat(paymentService.getErrorRate()).isEqualTo(0.0);
        assertThat(paymentService.getErrorCount()).isEqualTo(0L);
        assertThat(paymentService.getTotalCount()).isEqualTo(500L);
    }

    @Test
    void getErrorRateByService_shouldRoundToTwoDecimals() {
        List<Object[]> totalCounts = Arrays.asList(
            new Object[]{"test-service", 333L}
        );
        List<Object[]> errorCounts = Arrays.asList(
            new Object[]{"test-service", 17L}
        );

        when(logEntryRepository.countByServiceSince(any(Instant.class)))
            .thenReturn(totalCounts);
        when(logEntryRepository.countByLevelAndServiceSince(eq(LogLevel.ERROR), any(Instant.class)))
            .thenReturn(errorCounts);

        List<ErrorRateResponse> result = analyticsService.getErrorRateByService();

        assertThat(result.get(0).getErrorRate()).isEqualTo(5.11);
    }
}
