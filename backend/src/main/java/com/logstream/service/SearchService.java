package com.logstream.service;

import com.logstream.dto.LogEntryResponse;
import com.logstream.dto.LogSearchRequest;
import com.logstream.model.LogEntry;
import com.logstream.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final LogEntryRepository logEntryRepository;

    public Page<LogEntryResponse> searchLogs(LogSearchRequest request) {
        PageRequest pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(Sort.Direction.DESC, "timestamp")
        );

        Instant start = request.getStartTime() != null
                ? Instant.parse(request.getStartTime())
                : Instant.EPOCH;
        Instant end = request.getEndTime() != null
                ? Instant.parse(request.getEndTime())
                : Instant.now();

        String service = request.getServiceName();
        var level = request.getLevel();
        String keyword = request.getKeyword();

        Page<LogEntry> page;

        if (service != null && level != null && keyword != null && !keyword.isBlank()) {
            page = logEntryRepository
                    .findByServiceNameAndLevelAndTimestampBetweenAndMessageContainingIgnoreCase(
                            service, level, start, end, keyword, pageable);
        } else if (service != null && level != null) {
            page = logEntryRepository
                    .findByServiceNameAndLevelAndTimestampBetween(service, level, start, end, pageable);
        } else if (service != null) {
            page = logEntryRepository
                    .findByServiceNameAndTimestampBetween(service, start, end, pageable);
        } else if (level != null) {
            page = logEntryRepository
                    .findByLevelAndTimestampBetween(level, start, end, pageable);
        } else if (keyword != null && !keyword.isBlank()) {
            page = logEntryRepository
                    .findByTimestampBetweenAndMessageContainingIgnoreCase(start, end, keyword, pageable);
        } else {
            page = logEntryRepository
                    .findByTimestampBetween(start, end, pageable);
        }

        return page.map(this::mapToResponse);
    }

    public LogEntryResponse getLogById(UUID id) {
        LogEntry entry = logEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Log entry not found: " + id));
        return mapToResponse(entry);
    }

    private LogEntryResponse mapToResponse(LogEntry e) {
        return LogEntryResponse.builder()
                .id(e.getId()).serviceName(e.getServiceName()).timestamp(e.getTimestamp())
                .level(e.getLevel()).message(e.getMessage()).metadata(null)
                .source(e.getSource()).traceId(e.getTraceId()).createdAt(e.getCreatedAt())
                .build();
    }
}