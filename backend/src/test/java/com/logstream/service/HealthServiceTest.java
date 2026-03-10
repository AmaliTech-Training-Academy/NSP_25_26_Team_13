package com.logstream.service;

import com.logstream.dto.ServiceHealthResponse;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    @InjectMocks
    private HealthService healthDashboardService;

    @Test
    void resolveStatus_ErrorRateBelow1_ReturnsGreen() {
        assertEquals("GREEN", healthDashboardService.resolveStatus(0.0));
        assertEquals("GREEN", healthDashboardService.resolveStatus(0.5));
        assertEquals("GREEN", healthDashboardService.resolveStatus(0.9));
        assertEquals("GREEN", healthDashboardService.resolveStatus(0.99));
    }

    @Test
    void resolveStatus_ErrorRateBetween1And5_ReturnsYellow() {
        assertEquals("YELLOW", healthDashboardService.resolveStatus(1.0));
        assertEquals("YELLOW", healthDashboardService.resolveStatus(2.3));
        assertEquals("YELLOW", healthDashboardService.resolveStatus(5.0));
    }

    @Test
    void resolveStatus_ErrorRateAbove5_ReturnsRed() {
        assertEquals("RED", healthDashboardService.resolveStatus(5.01));
        assertEquals("RED", healthDashboardService.resolveStatus(5.1));
        assertEquals("RED", healthDashboardService.resolveStatus(10.0));
        assertEquals("RED", healthDashboardService.resolveStatus(100.0));
    }

    @Test
    void resolveStatus_ExactBoundary0Point9_ReturnsGreen() {
        assertEquals("GREEN", healthDashboardService.resolveStatus(0.9));
    }

    @Test
    void resolveStatus_ExactBoundary1Point0_ReturnsYellow() {
        assertEquals("YELLOW", healthDashboardService.resolveStatus(1.0));
    }

    @Test
    void resolveStatus_ExactBoundary5Point0_ReturnsYellow() {
        assertEquals("YELLOW", healthDashboardService.resolveStatus(5.0));
    }

    @Test
    void resolveStatus_ExactBoundary5Point1_ReturnsRed() {
        assertEquals("RED", healthDashboardService.resolveStatus(5.1));
    }

    @Test
    void getHealthDashboard_ReturnsAllServicesWithCorrectStatus() {
        Instant lastLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("auth-service", "payment-service", "order-service");
        
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "auth-service", lastLog });
        lastLogTimestamps.add(new Object[] { "payment-service", lastLog });
        lastLogTimestamps.add(new Object[] { "order-service", lastLog });
        
        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[] { "auth-service", 5L });
        errorCounts.add(new Object[] { "payment-service", 30L });
        errorCounts.add(new Object[] { "order-service", 0L });
        
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "auth-service", 1000L });
        totalCounts.add(new Object[] { "payment-service", 500L });
        totalCounts.add(new Object[] { "order-service", 200L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(3, result.size());

        ServiceHealthResponse auth = result.stream()
                .filter(r -> "auth-service".equals(r.getService())).findFirst().orElseThrow();
        assertEquals(0.5, auth.getErrorRate());
        assertEquals("GREEN", auth.getStatus());
        assertEquals(lastLog, auth.getLastLogTime());

        ServiceHealthResponse payment = result.stream()
                .filter(r -> "payment-service".equals(r.getService())).findFirst().orElseThrow();
        assertEquals(6.0, payment.getErrorRate());
        assertEquals("RED", payment.getStatus());

        ServiceHealthResponse order = result.stream()
                .filter(r -> "order-service".equals(r.getService())).findFirst().orElseThrow();
        assertEquals(0.0, order.getErrorRate());
        assertEquals("GREEN", order.getStatus());
    }

    @Test
    void getHealthDashboard_ServiceWithNoLogsIn24Hours_ReturnsUnknownStatus() {
        Instant oldLog = Instant.parse("2026-02-01T00:00:00Z");

        List<String> services = List.of("idle-service");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "idle-service", oldLog });
        List<Object[]> errorCounts = new ArrayList<>();
        List<Object[]> totalCounts = new ArrayList<>();

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(1, result.size());
        ServiceHealthResponse idle = result.get(0);
        assertEquals("idle-service", idle.getService());
        assertEquals("UNKNOWN", idle.getStatus());
        assertEquals(0.0, idle.getErrorRate());
        assertEquals(oldLog, idle.getLastLogTime());
    }

    @Test
    void getHealthDashboard_NoServices_ReturnsEmptyList() {
        when(logEntryRepository.findDistinctServiceNames()).thenReturn(Collections.emptyList());
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(Collections.emptyList());
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(Collections.emptyList());
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(Collections.emptyList());

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertTrue(result.isEmpty());
    }

    @Test
    void getHealthDashboard_ErrorRateIsRoundedToTwoDecimalPlaces() {
        Instant lastLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("rounding-service");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "rounding-service", lastLog });
        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[] { "rounding-service", 1L });
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "rounding-service", 3L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(1, result.size());
        assertEquals(33.33, result.get(0).getErrorRate());
        assertEquals("RED", result.get(0).getStatus());
    }

    @Test
    void getHealthDashboard_YellowBoundary_ExactlyOnePercent_ReturnsYellow() {
        Instant lastLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("boundary-service");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "boundary-service", lastLog });
        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[] { "boundary-service", 1L });
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "boundary-service", 100L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(1, result.size());
        assertEquals(1.0, result.get(0).getErrorRate());
        assertEquals("YELLOW", result.get(0).getStatus());
    }

    @Test
    void getHealthDashboard_RedBoundary_JustAboveFivePercent_ReturnsRed() {
        Instant lastLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("failing-service");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "failing-service", lastLog });
        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[] { "failing-service", 51L });
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "failing-service", 1000L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(1, result.size());
        assertEquals(5.1, result.get(0).getErrorRate());
        assertEquals("RED", result.get(0).getStatus());
    }

    @Test
    void getHealthDashboard_ServiceWithLastLogTimeNull_HandlesGracefully() {
        List<String> services = List.of("new-service");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        List<Object[]> errorCounts = new ArrayList<>();
        List<Object[]> totalCounts = new ArrayList<>();

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(1, result.size());
        assertEquals("UNKNOWN", result.get(0).getStatus());
        assertNull(result.get(0).getLastLogTime());
    }

    @Test
    void getHealthDashboard_ExactlyZeroPoint9Percent_ReturnsGreen() {
        Instant lastLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("green-edge");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "green-edge", lastLog });
        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[] { "green-edge", 9L });
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "green-edge", 1000L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(1, result.size());
        assertEquals(0.9, result.get(0).getErrorRate());
        assertEquals("GREEN", result.get(0).getStatus());
    }

    @Test
    void getHealthDashboard_ExactlyFivePoint0Percent_ReturnsYellow() {
        Instant lastLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("yellow-edge");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "yellow-edge", lastLog });
        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[] { "yellow-edge", 50L });
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "yellow-edge", 1000L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(1, result.size());
        assertEquals(5.0, result.get(0).getErrorRate());
        assertEquals("YELLOW", result.get(0).getStatus());
    }

    @Test
    void getHealthDashboard_ExactlyFivePoint1Percent_ReturnsRed() {
        Instant lastLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("red-edge");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "red-edge", lastLog });
        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[] { "red-edge", 51L });
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "red-edge", 1000L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(1, result.size());
        assertEquals(5.1, result.get(0).getErrorRate());
        assertEquals("RED", result.get(0).getStatus());
    }

    @Test
    void getHealthDashboard_AllServicesGreen_NoErrors() {
        Instant lastLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("svc-a", "svc-b", "svc-c");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "svc-a", lastLog });
        lastLogTimestamps.add(new Object[] { "svc-b", lastLog });
        lastLogTimestamps.add(new Object[] { "svc-c", lastLog });
        List<Object[]> errorCounts = new ArrayList<>();
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "svc-a", 500L });
        totalCounts.add(new Object[] { "svc-b", 300L });
        totalCounts.add(new Object[] { "svc-c", 100L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(r -> "GREEN".equals(r.getStatus())));
        assertTrue(result.stream().allMatch(r -> r.getErrorRate() == 0.0));
    }

    @Test
    void getHealthDashboard_AllServicesRed_HighErrorRates() {
        Instant lastLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("failing-a", "failing-b");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "failing-a", lastLog });
        lastLogTimestamps.add(new Object[] { "failing-b", lastLog });
        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[] { "failing-a", 80L });
        errorCounts.add(new Object[] { "failing-b", 100L });
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "failing-a", 100L });
        totalCounts.add(new Object[] { "failing-b", 100L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> "RED".equals(r.getStatus())));

        ServiceHealthResponse a = result.stream()
                .filter(r -> "failing-a".equals(r.getService())).findFirst().orElseThrow();
        assertEquals(80.0, a.getErrorRate());

        ServiceHealthResponse b = result.stream()
                .filter(r -> "failing-b".equals(r.getService())).findFirst().orElseThrow();
        assertEquals(100.0, b.getErrorRate());
    }

    @Test
    void getHealthDashboard_SingleErrorLog_Returns100PercentRed() {
        Instant lastLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("single-log-svc");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "single-log-svc", lastLog });
        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[] { "single-log-svc", 1L });
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "single-log-svc", 1L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(1, result.size());
        assertEquals(100.0, result.get(0).getErrorRate());
        assertEquals("RED", result.get(0).getStatus());
    }

    @Test
    void getHealthDashboard_AllFourStatusesPresentSimultaneously() {
        Instant recentLog = Instant.parse("2026-03-04T12:00:00Z");

        List<String> services = List.of("green-svc", "yellow-svc", "red-svc", "unknown-svc");
        List<Object[]> lastLogTimestamps = new ArrayList<>();
        lastLogTimestamps.add(new Object[] { "green-svc", recentLog });
        lastLogTimestamps.add(new Object[] { "yellow-svc", recentLog });
        lastLogTimestamps.add(new Object[] { "red-svc", recentLog });
        lastLogTimestamps.add(new Object[] { "unknown-svc", recentLog });
        List<Object[]> errorCounts = new ArrayList<>();
        errorCounts.add(new Object[] { "green-svc", 5L });
        errorCounts.add(new Object[] { "yellow-svc", 30L });
        errorCounts.add(new Object[] { "red-svc", 100L });
        List<Object[]> totalCounts = new ArrayList<>();
        totalCounts.add(new Object[] { "green-svc", 1000L });
        totalCounts.add(new Object[] { "yellow-svc", 1000L });
        totalCounts.add(new Object[] { "red-svc", 1000L });

        when(logEntryRepository.findDistinctServiceNames()).thenReturn(services);
        when(logEntryRepository.findLastLogTimestampByService()).thenReturn(lastLogTimestamps);
        when(logEntryRepository.countErrorsByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(errorCounts);
        when(logEntryRepository.countByServiceAndCreatedAtAfter(any(Instant.class))).thenReturn(totalCounts);

        List<ServiceHealthResponse> result = healthDashboardService.getHealthDashboard();

        assertEquals(4, result.size());

        ServiceHealthResponse green = result.stream()
                .filter(r -> "green-svc".equals(r.getService())).findFirst().orElseThrow();
        assertEquals("GREEN", green.getStatus());
        assertEquals(0.5, green.getErrorRate());

        ServiceHealthResponse yellow = result.stream()
                .filter(r -> "yellow-svc".equals(r.getService())).findFirst().orElseThrow();
        assertEquals("YELLOW", yellow.getStatus());
        assertEquals(3.0, yellow.getErrorRate());

        ServiceHealthResponse red = result.stream()
                .filter(r -> "red-svc".equals(r.getService())).findFirst().orElseThrow();
        assertEquals("RED", red.getStatus());
        assertEquals(10.0, red.getErrorRate());

        ServiceHealthResponse unknown = result.stream()
                .filter(r -> "unknown-svc".equals(r.getService())).findFirst().orElseThrow();
        assertEquals("UNKNOWN", unknown.getStatus());
        assertEquals(0.0, unknown.getErrorRate());
    }
}
