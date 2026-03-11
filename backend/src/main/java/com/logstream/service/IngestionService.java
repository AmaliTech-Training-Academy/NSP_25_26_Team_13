package com.logstream.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logstream.dto.BatchLogEntryResponse;
import com.logstream.dto.BatchLogRequest;
import com.logstream.dto.LogEntryRequest;
import com.logstream.dto.LogEntryResponse;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final LogEntryRepository logEntryRepository;
    private final ObjectMapper objectMapper;

    public Page<LogEntryResponse> getLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return logEntryRepository.findAll(pageable).map(this::mapToResponse);
    }

    public LogEntryResponse ingestLog(LogEntryRequest request) {
        LogEntry entry = mapToEntity(request);
        LogEntry saved = logEntryRepository.save(entry);
        return mapToResponse(saved);
    }

    public BatchLogEntryResponse ingestBatch(BatchLogRequest request) {
        List<LogEntry> entries = request.getLogs().stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
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

    private LogEntryResponse mapToResponse(LogEntry e) {
        return LogEntryResponse.builder()
                .id(e.getId()).serviceName(e.getServiceName()).timestamp(e.getTimestamp())
                .level(e.getLevel()).message(e.getMessage()).metadata(null)
                .source(e.getSource()).traceId(null).createdAt(e.getCreatedAt())
                .build();
    }
}