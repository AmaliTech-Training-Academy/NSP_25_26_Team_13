package com.logstream.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logstream.dto.BatchLogEntryResponse;
import com.logstream.dto.BatchLogRequest;
import com.logstream.dto.LogEntryRequest;
import com.logstream.dto.LogEntryResponse;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.model.RetentionPolicy;
import com.logstream.repository.LogEntryRepository;
import com.logstream.repository.RetentionPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final LogEntryRepository logEntryRepository;
    private final ObjectMapper objectMapper;
    private final RetentionPolicyRepository retentionPolicyRepository;
    private final BatchPersistenceService batchPersistenceService;

    public Page<LogEntryResponse> getLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return logEntryRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional
    public LogEntryResponse ingestLog(LogEntryRequest request) {
        LogEntry entry = mapToEntity(request);
        LogEntry saved = logEntryRepository.save(entry);
        createRetentionPolicyFromLogEntry(entry.getServiceName());
        return mapToResponse(saved);
    }

    @Transactional
    public BatchLogEntryResponse ingestBatch(BatchLogRequest request) {
        List<LogEntry> entries = request.getLogs()
                .stream()
                .map(this::mapToEntity)
                .toList();

        batchPersistenceService.persistBatchLogEntriesWithRetentionPolicy(entries, retentionPolicyRepository);
        logEntryRepository.saveAll(entries);
        return new BatchLogEntryResponse(entries.size());
    }

    private String serializeMetadata(java.util.Map<String, String> metadata) {
        if (metadata == null) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private LogEntry mapToEntity(LogEntryRequest request) {
        return LogEntry.builder()
                .serviceName(request.getServiceName())
                .timestamp(Instant.now())
                .level(LogLevel.valueOf(request.getLevel()))
                .message(request.getMessage())
                .source(request.getSource())
                .build();
    }

    private void createRetentionPolicyFromLogEntry(String serviceName) {
        retentionPolicyRepository.findByServiceName(serviceName)
                .orElseGet(() -> {
                    RetentionPolicy policy = new RetentionPolicy();
                    policy.setServiceName(serviceName);
                    policy.setRetentionDays(30);
                    policy.setArchiveEnabled(false);
                    return retentionPolicyRepository.save(policy);
                });
    }

    private LogEntryResponse mapToResponse(LogEntry e) {
        return LogEntryResponse.builder()
                .id(e.getId()).serviceName(e.getServiceName()).timestamp(e.getTimestamp())
                .level(e.getLevel()).message(e.getMessage()).metadata(null)
                .source(e.getSource()).traceId(null).createdAt(e.getCreatedAt())
                .build();
    }
}