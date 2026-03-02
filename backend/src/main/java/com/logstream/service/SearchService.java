package com.logstream.service;

import com.logstream.dto.LogEntryResponse;
import com.logstream.dto.LogSearchRequest;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final LogEntryRepository logEntryRepository;

    public Page<LogEntryResponse> searchLogs(LogSearchRequest request) {
        PageRequest pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<LogEntry> results;

        if (request.getServiceName() != null && request.getLevel() != null) {
            results = logEntryRepository.findByServiceNameAndLevel(
                request.getServiceName(), request.getLevel(), pageable);
        } else if (request.getServiceName() != null) {
            results = logEntryRepository.findByServiceName(request.getServiceName(), pageable);
        } else if (request.getLevel() != null) {
            results = logEntryRepository.findByLevel(request.getLevel(), pageable);
        } else {
            results = logEntryRepository.findAll(pageable);
        }

        // TODO (Dev B): Add time range filter, keyword full-text search,
        // combine multiple filters with Specification pattern

        return results.map(this::mapToResponse);
    }

    public LogEntryResponse getLogById(UUID id) {
        LogEntry entry = logEntryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Log entry not found: " + id));
        return mapToResponse(entry);
    }

    private LogEntryResponse mapToResponse(LogEntry e) {
        return LogEntryResponse.builder()
            .id(e.getId()).serviceName(e.getServiceName()).timestamp(e.getTimestamp())
            .level(e.getLevel()).message(e.getMessage()).metadata(e.getMetadata())
            .source(e.getSource()).traceId(e.getTraceId()).createdAt(e.getCreatedAt())
            .build();
    }
}
