package com.logstream.service;

import com.logstream.model.ServiceConfig;
import com.logstream.repository.LogEntryRepository;
import com.logstream.repository.ServiceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogCleanupService {

    private final ServiceConfigRepository serviceConfigRepository;
    private final LogEntryRepository logEntryRepository;
    private final Clock clock;

    @Async
    @Scheduled(cron = "0 0 2 * * *") // runs daily at 2am
    public void scheduledCleanup() {
        cleanup();
    }

    @Transactional
    public List<CleanupResult> cleanup() {
        Instant now = Instant.now(clock);
        List<ServiceConfig> configs = serviceConfigRepository.findAll();

        List<CleanupResult> results = new ArrayList<>(configs.size());
        for (ServiceConfig config : configs) {
            int retentionDays = config.getRetentionDays();
            Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
            int deleted = logEntryRepository.deleteByServiceNameAndCreatedAtBefore(config.getServiceName(), cutoff);

            log.info("Deleted {} logs from service {} (retention: {} days)", deleted, config.getServiceName(), retentionDays);
            results.add(new CleanupResult(config.getServiceName(), retentionDays, deleted));
        }

        return results;
    }

    @Value
    public static class CleanupResult {
        String serviceName;
        int retentionDays;
        int deletedCount;
    }
}

