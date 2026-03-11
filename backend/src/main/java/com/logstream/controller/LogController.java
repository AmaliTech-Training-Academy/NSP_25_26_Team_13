package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.dto.*;
import com.logstream.model.User;
import com.logstream.service.AnalyticsService;
import com.logstream.service.IngestionService;
import com.logstream.service.LogImportService;
import com.logstream.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {
    private final IngestionService ingestionService;
    private final SearchService searchService;
    private final AnalyticsService analyticsService;
    private final LogImportService logImportService;

    @PostMapping
    public ResponseEntity<ApiResponse<LogEntryResponse>> ingestLog(
            @Valid @RequestBody LogEntryRequest request,
            @AuthenticationPrincipal User user) {
        LogEntryResponse response = ingestionService.ingestLog(request);
        ApiResponse<LogEntryResponse> apiResponse = ApiResponse.success("Log ingested successfully", response);
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<LogEntryResponse>>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<LogEntryResponse> logs = ingestionService.getLogs(page, size);
        ApiResponse<Page<LogEntryResponse>> apiResponse = ApiResponse.success("Logs retrieved successfully", logs);
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<BatchLogEntryResponse>> ingestBatch(
            @Valid @RequestBody BatchLogRequest request,
            @AuthenticationPrincipal User user) {
        BatchLogEntryResponse results = ingestionService.ingestBatch(request);
        ApiResponse<BatchLogEntryResponse> apiResponse = ApiResponse.success("Batch log ingestion completed", results);
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> importLogs(
            @RequestParam("file") MultipartFile file
    ) {
        logImportService.initiateImport(file);
        return ResponseEntity.ok(Map.of("message", "Log import successful"));
    }

    @PostMapping("/search")
    public ResponseEntity<Page<LogEntryResponse>> searchLogs(
            @RequestBody LogSearchRequest request) {
        return ResponseEntity.ok(searchService.searchLogs(request));
    }

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(null/*analyticsService.getAnalytics(startDate, endDate)*/);
    }

    // TODO: Add GET /api/logs/{id} endpoint
    // TODO: Add DELETE /api/logs/{id} endpoint (admin only)
    // TODO: Add GET /api/logs/stream (SSE endpoint for real-time log tailing)
}