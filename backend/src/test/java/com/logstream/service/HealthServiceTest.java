package com.logstream.service;

import com.logstream.dto.HealthDashboardResponse;
import com.logstream.dto.ServiceHealthStatus;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    @InjectMocks
    private HealthService healthService;

    @Test
    void getHealthDashboard_shouldReturnGreenStatusForLowErrorRate() {
        Instant now = Instant.now();
        
        when(logEntryRepository.findLastLogTimePerService())
            .thenReturn(Arrays.asList(new Object[]{"auth-service", now}));
        when(logEntryRepository.findErrorRateDataPerService(any(Instant.class)))
            .thenReturn(Arrays.asList(new Object[]{"auth-service", 5L, 1000L}));

        List<HealthDashboardResponse> result = healthService.getHealthDashboard();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ServiceHealthStatus.GREEN);
        assertThat(result.get(0).getErrorRate()).isEqualTo(0.5);
    }

    @Test
    void getHealthDashboard_shouldReturnYellowStatusForMediumErrorRate() {
        Instant now = Instant.now();
        
        when(logEntryRepository.findLastLogTimePerService())
            .thenReturn(Arrays.asList(new Object[]{"payment-service", now}));
        when(logEntryRepository.findErrorRateDataPerService(any(Instant.class)))
            .thenReturn(Arrays.asList(new Object[]{"payment-service", 30L, 1000L}));

        List<HealthDashboardResponse> result = healthService.getHealthDashboard();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ServiceHealthStatus.YELLOW);
        assertThat(result.get(0).getErrorRate()).isEqualTo(3.0);
    }

    @Test
    void getHealthDashboard_shouldReturnRedStatusForHighErrorRate() {
        Instant now = Instant.now();
        
        when(logEntryRepository.findLastLogTimePerService())
            .thenReturn(Arrays.asList(new Object[]{"user-service", now}));
        when(logEntryRepository.findErrorRateDataPerService(any(Instant.class)))
            .thenReturn(Arrays.asList(new Object[]{"user-service", 100L, 1000L}));

        List<HealthDashboardResponse> result = healthService.getHealthDashboard();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ServiceHealthStatus.RED);
        assertThat(result.get(0).getErrorRate()).isEqualTo(10.0);
    }

    @Test
    void getHealthDashboard_shouldReturnUnknownStatusForNoLogs() {
        when(logEntryRepository.findLastLogTimePerService())
            .thenReturn(Collections.emptyList());
        when(logEntryRepository.findErrorRateDataPerService(any(Instant.class)))
            .thenReturn(Collections.emptyList());

        List<HealthDashboardResponse> result = healthService.getHealthDashboard();

        assertThat(result).isEmpty();
    }

    @Test
    void getHealthDashboard_shouldReturnUnknownStatusForOldLogs() {
        Instant oldTime = Instant.now().minusSeconds(86400 * 2);
        
        when(logEntryRepository.findLastLogTimePerService())
            .thenReturn(Arrays.asList(new Object[]{"old-service", oldTime}));
        when(logEntryRepository.findErrorRateDataPerService(any(Instant.class)))
            .thenReturn(Arrays.asList(new Object[]{"old-service", 10L, 1000L}));

        List<HealthDashboardResponse> result = healthService.getHealthDashboard();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(ServiceHealthStatus.UNKNOWN);
    }
}
