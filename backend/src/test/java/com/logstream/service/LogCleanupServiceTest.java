package com.logstream.service;

import com.logstream.model.ServiceConfig;
import com.logstream.repository.LogEntryRepository;
import com.logstream.repository.ServiceConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogCleanupServiceTest {

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @Mock
    private LogEntryRepository logEntryRepository;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-11T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void cleanup_deletesOnlyOlderThanCutoff_perServiceConfig() {
        LogCleanupService logCleanupService = new LogCleanupService(serviceConfigRepository, logEntryRepository, fixedClock);
        ServiceConfig svcA = ServiceConfig.builder().serviceName("svc-a").retentionDays(30).build();
        ServiceConfig svcB = ServiceConfig.builder().serviceName("svc-b").retentionDays(7).build();
        when(serviceConfigRepository.findAll()).thenReturn(List.of(svcA, svcB));

        when(logEntryRepository.deleteByServiceNameAndCreatedAtBefore(eq("svc-a"), any()))
                .thenReturn(1234);
        when(logEntryRepository.deleteByServiceNameAndCreatedAtBefore(eq("svc-b"), any()))
                .thenReturn(5);

        List<LogCleanupService.CleanupResult> results = logCleanupService.cleanup();

        Instant now = Instant.now(fixedClock);
        verify(logEntryRepository).deleteByServiceNameAndCreatedAtBefore("svc-a", now.minusSeconds(30L * 24 * 60 * 60));
        verify(logEntryRepository).deleteByServiceNameAndCreatedAtBefore("svc-b", now.minusSeconds(7L * 24 * 60 * 60));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getServiceName()).isEqualTo("svc-a");
        assertThat(results.get(0).getDeletedCount()).isEqualTo(1234);
        assertThat(results.get(1).getServiceName()).isEqualTo("svc-b");
        assertThat(results.get(1).getDeletedCount()).isEqualTo(5);
    }

    @Test
    void cleanup_withNoConfigs_doesNothing() {
        LogCleanupService logCleanupService = new LogCleanupService(serviceConfigRepository, logEntryRepository, fixedClock);
        when(serviceConfigRepository.findAll()).thenReturn(List.of());

        List<LogCleanupService.CleanupResult> results = logCleanupService.cleanup();

        verifyNoInteractions(logEntryRepository);
        assertThat(results).isEmpty();
    }
}

