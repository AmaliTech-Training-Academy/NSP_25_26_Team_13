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
import java.util.Set;
import java.util.stream.Collectors;

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

    public List<String> getAllServices() {
        return logEntryRepository.findDistinctServiceNames();
    }

    public RetentionPolicy getPolicyByServiceName(String serviceName) {
        return retentionPolicyRepository.findByServiceName(serviceName)
                .orElse(null);
    }

    @Transactional
    @Scheduled(cron = "0 0 2 * * *") // runs daily at 2am
    public void applyRetention() {
        Instant now = Instant.now();
        List<RetentionPolicy> policies = retentionPolicyRepository.findAll();
        Set<String> servicesWithPolicy = policies.stream()
                .map(RetentionPolicy::getServiceName)
                .collect(Collectors.toSet());

        applyCustomPolicies(policies, now);
        applyDefaultRetention(servicesWithPolicy, now);
    }

    private void applyCustomPolicies(List<RetentionPolicy> policies, Instant now) {
        for (RetentionPolicy policy : policies) {
            Instant cutoff = now.minus(policy.getRetentionDays(), ChronoUnit.DAYS);
            logEntryRepository.deleteByServiceNameOlderThan(policy.getServiceName(), cutoff);
        }
    }

    private void applyDefaultRetention(Set<String> servicesWithPolicy, Instant now) {
        Instant defaultCutoff = now.minus(DEFAULT_RETENTION_DAYS, ChronoUnit.DAYS);
        for (String service : logEntryRepository.findDistinctServiceNames()) {
            if (!servicesWithPolicy.contains(service)) {
                logEntryRepository.deleteByServiceNameOlderThan(service, defaultCutoff);
            }
        }
    }
}
