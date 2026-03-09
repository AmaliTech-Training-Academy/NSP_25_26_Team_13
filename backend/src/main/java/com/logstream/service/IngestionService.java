package com.logstream.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logstream.dto.LogEntryRequest;
import com.logstream.dto.LogEntryResponse;
import com.logstream.model.LogEntry;
import com.logstream.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final LogEntryRepository logEntryRepository;
    private final ObjectMapper objectMapper;

    public LogEntryResponse ingestLog(LogEntryRequest request) {
        LogEntry entry = mapToEntity(request);
        LogEntry saved = logEntryRepository.save(entry);
        return mapToResponse(saved);
    }

    // TODO: Implement Batch import

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
                .level(request.getLevel())
                .message(request.getMessage())
                .metadata(serializeMetadata(request.getMetadata()))
                .source(request.getSource())
                .traceId(request.getTraceId())
                .build();
    }

    private LogEntryResponse mapToResponse(LogEntry e) {
        return LogEntryResponse.builder()
                .id(e.getId()).serviceName(e.getServiceName()).timestamp(e.getTimestamp())
                .level(e.getLevel()).message(e.getMessage()).metadata(e.getMetadata())
                .source(e.getSource()).traceId(e.getTraceId()).createdAt(e.getCreatedAt())
                .build();
    }
}