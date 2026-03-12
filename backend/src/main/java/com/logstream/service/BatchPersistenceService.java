package com.logstream.service;

import com.logstream.model.LogEntry;
import com.logstream.model.RetentionPolicy;
import com.logstream.repository.RetentionPolicyRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    @Transactional
    public void persistBatchLogEntriesWithRetentionPolicy(List<LogEntry> batch, RetentionPolicyRepository repo) {
        Set<String> names = batch.stream()
                .map(LogEntry::getServiceName)
                .collect(Collectors.toSet());

        // Fetch existing
        Set<String> existing = repo.findByServiceNameIn(names).stream()
                .map(RetentionPolicy::getServiceName)
                .collect(Collectors.toSet());

        // Filter and save
        List<RetentionPolicy> newPolicies = names.stream()
                .filter(name -> !existing.contains(name))
                .map(this::createRetentionPolicyEntity)
                .collect(Collectors.toList());

        if (!newPolicies.isEmpty()) {
            try {
                repo.saveAll(newPolicies);
                repo.flush();
            } catch (DataIntegrityViolationException ignored) {
            }
        }
    }

    private RetentionPolicy createRetentionPolicyEntity(String serviceName) {
        RetentionPolicy policy = new RetentionPolicy();
        policy.setServiceName(serviceName);
        policy.setRetentionDays(30);
        policy.setArchiveEnabled(false);
        policy.setCreatedAt(Instant.now());
        return policy;
    }
}