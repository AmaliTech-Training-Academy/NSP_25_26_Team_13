package com.logstream.service;

import com.logstream.model.RetentionPolicy;
import com.logstream.repository.LogEntryRepository;
import com.logstream.repository.RetentionPolicyRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RetentionService {

    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final LogEntryRepository logEntryRepository;

    public RetentionPolicy createPolicy(String serviceName, int days, boolean archive) {
        RetentionPolicy policy = RetentionPolicy.builder()
                .serviceName(serviceName)
                .retentionDays(days)
                .archiveEnabled(archive)
                .build();
        return retentionPolicyRepository.save(policy);
    }

    public RetentionPolicy updatePolicy(String serviceName, int days, boolean archive) {
        RetentionPolicy policy = retentionPolicyRepository.findByServiceName(serviceName)
                .orElseThrow(() -> new RuntimeException("No policy found for service: " + serviceName));
        policy.setRetentionDays(days);
        policy.setArchiveEnabled(archive);
        return retentionPolicyRepository.save(policy);
    }

    public void deletePolicy(String serviceName) {
        RetentionPolicy policy = retentionPolicyRepository.findByServiceName(serviceName)
                .orElseThrow(() -> new RuntimeException("No policy found for service: " + serviceName));
        retentionPolicyRepository.delete(policy);
    }

    public List<RetentionPolicy> getPolicies() {
        return retentionPolicyRepository.findAll();
    }

    @Transactional
    @Scheduled(cron = "0 0 2 * * *") // runs daily at 2am
    public void applyRetention() {
        List<RetentionPolicy> policies = retentionPolicyRepository.findAll();
        List<String> servicesWithPolicy = policies.stream().map(RetentionPolicy::getServiceName).toList();

        // apply per-service policies
        for (RetentionPolicy policy : policies) {
            Instant cutoff = Instant.now().minus(policy.getRetentionDays(), ChronoUnit.DAYS);
            logEntryRepository.deleteByServiceNameOlderThan(policy.getServiceName(), cutoff);
        }

        // apply default 30-day retention to services without a custom policy
        List<String> allServices = logEntryRepository.findDistinctServiceNames();
        Instant defaultCutoff = Instant.now().minus(DEFAULT_RETENTION_DAYS, ChronoUnit.DAYS);
        for (String service : allServices) {
            if (!servicesWithPolicy.contains(service)) {
                logEntryRepository.deleteByServiceNameOlderThan(service, defaultCutoff);
            }
        }
    }
}
