package com.logstream.service;

import com.logstream.model.LogEntry;
import com.logstream.model.RetentionPolicy;
import com.logstream.repository.RetentionPolicyRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BatchPersistenceService {

    private final EntityManager entityManager;
    private final RetentionPolicyRepository retentionPolicyRepository;

    @Transactional
    public void saveBatch(List<LogEntry> batch) {

        persistBatchLogEntriesWithRetentionPolicy(batch, retentionPolicyRepository);

        for (int i = 0; i < batch.size(); i++) {
            entityManager.persist(batch.get(i));

            if ((i + 1) % 500 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        entityManager.flush();
        entityManager.clear();
    }

    public void persistBatchLogEntriesWithRetentionPolicy(List<LogEntry> batch, RetentionPolicyRepository retentionPolicyRepository) {
        Set<String> serviceNames = batch.stream()
                .map(LogEntry::getServiceName)
                .collect(Collectors.toSet());

        Set<String> existingPolicies = retentionPolicyRepository
                .findByServiceNameIn(serviceNames)
                .stream()
                .map(RetentionPolicy::getServiceName)
                .collect(Collectors.toSet());

        List<RetentionPolicy> newPolicies = serviceNames.stream()
                .filter(service -> !existingPolicies.contains(service))
                .map(service -> {
                    RetentionPolicy policy = new RetentionPolicy();
                    policy.setServiceName(service);
                    policy.setRetentionDays(30);
                    policy.setArchiveEnabled(false);
                    return policy;
                })
                .toList();

        if (!newPolicies.isEmpty()) {
            retentionPolicyRepository.saveAll(newPolicies);
        }
    }
}