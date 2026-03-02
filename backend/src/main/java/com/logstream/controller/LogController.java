package com.logstream.controller;

import com.logstream.dto.*;
import com.logstream.model.User;
import com.logstream.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {
    private final IngestionService ingestionService;
    private final SearchService searchService;
    private final AnalyticsService analyticsService;

    @PostMapping
    public ResponseEntity<LogEntryResponse> ingestLog(
            @Valid @RequestBody LogEntryRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ingestionService.ingestLog(request));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(
            @Valid @RequestBody BatchLogRequest request,
            @AuthenticationPrincipal User user) {
        List<LogEntryResponse> results = ingestionService.ingestBatch(request);
        return ResponseEntity.ok(Map.of("ingested", results.size(), "logs", results));
    }

    @PostMapping("/search")
    public ResponseEntity<List<LogEntryResponse>> searchLogs(
            @RequestBody LogSearchRequest request) {
        return ResponseEntity.ok(searchService.searchLogs(request));
    }

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(analyticsService.getAnalytics(startDate, endDate));
    }

    // TODO: Add GET /api/logs/{id} endpoint
    // TODO: Add DELETE /api/logs/{id} endpoint (admin only)
    // TODO: Add GET /api/logs/stream (SSE endpoint for real-time log tailing)
}
