package com.logstream.service;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.repository.LogEntryRepository;
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
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(Arrays.asList(new Object[]{"auth-service", 52L}));
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(Arrays.asList(
                new Object[]{"auth-service", 1000L},
                new Object[]{"payment-service", 500L}
            ));

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
            .thenReturn(Arrays.asList());
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(Arrays.asList(new Object[]{"payment-service", 500L}));

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getErrorRate()).isEqualTo(0.0);
        assertThat(result.get(0).getErrorCount()).isEqualTo(0L);
    }

    @Test
    void getErrorRatePerService_shouldRoundToTwoDecimals() {
        when(logEntryRepository.findDistinctServiceNames())
            .thenReturn(Arrays.asList("test-service"));
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(Arrays.asList(new Object[]{"test-service", 17L}));
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(Arrays.asList(new Object[]{"test-service", 333L}));

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result.get(0).getErrorRate()).isEqualTo(5.11);
    }
}
