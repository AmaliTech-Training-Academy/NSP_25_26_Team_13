package com.logstream.service;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService Tests")
class AnalyticsServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private Instant testTime;

    @BeforeEach
    void setUp() {
        testTime = Instant.now();
    }

    @Test
    @DisplayName("Should calculate correct error rates for multiple services")
    void getErrorRatePerService_shouldCalculateCorrectRates() {
        when(logEntryRepository.findDistinctServiceNames())
            .thenReturn(Arrays.asList("auth-service", "payment-service", "user-service"));
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn((List) Arrays.asList(
                new Object[]{"auth-service", 52L},
                new Object[]{"payment-service", 25L}
            ));
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn((List) Arrays.asList(
                new Object[]{"auth-service", 1000L},
                new Object[]{"payment-service", 500L},
                new Object[]{"user-service", 200L}
            ));

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result).hasSize(3);
        
        ErrorRateResponse authService = findByService(result, "auth-service");
        assertThat(authService.getErrorRate()).isEqualTo(5.2);
        assertThat(authService.getErrorCount()).isEqualTo(52L);
        assertThat(authService.getTotalCount()).isEqualTo(1000L);
        
        ErrorRateResponse paymentService = findByService(result, "payment-service");
        assertThat(paymentService.getErrorRate()).isEqualTo(5.0);
        assertThat(paymentService.getErrorCount()).isEqualTo(25L);
        assertThat(paymentService.getTotalCount()).isEqualTo(500L);
        
        ErrorRateResponse userService = findByService(result, "user-service");
        assertThat(userService.getErrorRate()).isEqualTo(0.0);
        assertThat(userService.getErrorCount()).isEqualTo(0L);
        assertThat(userService.getTotalCount()).isEqualTo(200L);
    }
    
    private ErrorRateResponse findByService(List<ErrorRateResponse> responses, String service) {
        return responses.stream()
            .filter(r -> r.getService().equals(service))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Service not found: " + service));
    }

    @Test
    @DisplayName("Should return zero error rate for services with no errors")
    void getErrorRatePerService_shouldReturnZeroForNoErrors() {
        when(logEntryRepository.findDistinctServiceNames())
            .thenReturn(Arrays.asList("payment-service", "notification-service"));
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(Collections.emptyList());
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn((List) Arrays.asList(
                new Object[]{"payment-service", 500L},
                new Object[]{"notification-service", 1000L}
            ));

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result).hasSize(2);
        result.forEach(response -> {
            assertThat(response.getErrorRate()).isEqualTo(0.0);
            assertThat(response.getErrorCount()).isEqualTo(0L);
            assertThat(response.getTotalCount()).isGreaterThan(0L);
        });
    }

    @Test
    @DisplayName("Should round error rates to two decimal places correctly")
    void getErrorRatePerService_shouldRoundToTwoDecimals() {
        when(logEntryRepository.findDistinctServiceNames())
            .thenReturn(Arrays.asList("test-service"));
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn((List) Arrays.asList(new Object[]{"test-service", 17L}));
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn((List) Arrays.asList(new Object[]{"test-service", 333L}));

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getErrorRate()).isEqualTo(5.11);
    }
    
    @Test
    @DisplayName("Should handle rounding up correctly")
    void getErrorRatePerService_shouldRoundUpCorrectly() {
        when(logEntryRepository.findDistinctServiceNames())
            .thenReturn(Arrays.asList("test-service"));
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn((List) Arrays.asList(new Object[]{"test-service", 1L}));
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn((List) Arrays.asList(new Object[]{"test-service", 3L}));

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getErrorRate()).isEqualTo(33.33);
    }
    
    @Test
    @DisplayName("Should handle zero total logs gracefully")
    void getErrorRatePerService_shouldHandleZeroTotalLogs() {
        when(logEntryRepository.findDistinctServiceNames())
            .thenReturn(Arrays.asList("empty-service"));
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(Collections.emptyList());
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn((List) Arrays.asList(new Object[]{"empty-service", 0L}));

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result).hasSize(1);
        ErrorRateResponse response = result.get(0);
        assertThat(response.getService()).isEqualTo("empty-service");
        assertThat(response.getErrorRate()).isEqualTo(0.0);
        assertThat(response.getErrorCount()).isEqualTo(0L);
        assertThat(response.getTotalCount()).isEqualTo(0L);
    }
    
    @Test
    @DisplayName("Should return empty list when no services exist")
    void getErrorRatePerService_shouldReturnEmptyListWhenNoServices() {
        when(logEntryRepository.findDistinctServiceNames())
            .thenReturn(Collections.emptyList());
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(Collections.emptyList());
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn(Collections.emptyList());

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("Should handle 100% error rate correctly")
    void getErrorRatePerService_shouldHandleHundredPercentErrorRate() {
        when(logEntryRepository.findDistinctServiceNames())
            .thenReturn(Arrays.asList("failing-service"));
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn((List) Arrays.asList(new Object[]{"failing-service", 100L}));
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class)))
            .thenReturn((List) Arrays.asList(new Object[]{"failing-service", 100L}));

        List<ErrorRateResponse> result = analyticsService.getErrorRatePerService();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getErrorRate()).isEqualTo(100.0);
        assertThat(result.get(0).getErrorCount()).isEqualTo(100L);
        assertThat(result.get(0).getTotalCount()).isEqualTo(100L);
    }
    
    @Test
    @DisplayName("Should throw UnsupportedOperationException for getLogVolume")
    void getLogVolume_shouldThrowUnsupportedOperationException() {
        assertThatThrownBy(() -> analyticsService.getLogVolume(24))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Log volume not yet implemented");
    }
    
    @Test
    @DisplayName("Should throw UnsupportedOperationException for getTopErrors")
    void getTopErrors_shouldThrowUnsupportedOperationException() {
        assertThatThrownBy(() -> analyticsService.getTopErrors(10))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Top errors not yet implemented");
    }
    
    @Test
    @DisplayName("Should throw UnsupportedOperationException for getServiceHealth")
    void getServiceHealth_shouldThrowUnsupportedOperationException() {
        assertThatThrownBy(() -> analyticsService.getServiceHealth())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Service health not yet implemented");
    }
}
