package com.logstream.service;

import com.logstream.model.LogEntry;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchPersistenceService {

    private final EntityManager entityManager;

    @Transactional
    public void saveBatch(List<LogEntry> batch) {
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
}