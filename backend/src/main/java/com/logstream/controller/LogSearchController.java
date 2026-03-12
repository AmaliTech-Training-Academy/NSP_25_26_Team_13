package com.logstream.controller;

import com.logstream.dto.LogEntryResponse;
import com.logstream.dto.LogSearchRequest;
import com.logstream.model.LogLevel;
import com.logstream.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "Logs Search", description = "Query logs using simple query parameters")
public class LogSearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    @Operation(summary = "Search logs (query params)", description = "Search logs using query parameters instead of a JSON body.")
    public Page<LogEntryResponse> searchLogs(
            @Parameter(description = "Filter by service name", example = "auth-service")
            @RequestParam(name = "service", required = false) String service,
            @Parameter(description = "Filter by log level", example = "ERROR")
            @RequestParam(name = "level", required = false) LogLevel level,
            @Parameter(description = "Start of time range (ISO-8601 or YYYY-MM-DD)", example = "2026-03-03T00:00:00Z")
            @RequestParam(name = "startTime", required = false) String startTime,
            @Parameter(description = "End of time range (ISO-8601 or YYYY-MM-DD)", example = "2026-03-04T00:00:00Z")
            @RequestParam(name = "endTime", required = false) String endTime,
            @Parameter(description = "Keyword to search in message/context", example = "timeout")
            @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(description = "Page index (0-based)", example = "0")
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        LogSearchRequest request = LogSearchRequest.builder()
                .serviceName(service)
                .level(level)
                .startTime(startTime)
                .endTime(endTime)
                .keyword(keyword)
                .page(page)
                .size(size)
                .build();

        return searchService.searchLogs(request);
    }
}

