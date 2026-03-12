package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.dto.*;
import com.logstream.model.User;
import com.logstream.service.AnalyticsService;
import com.logstream.service.IngestionService;
import com.logstream.service.LogImportService;
import com.logstream.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "Logs", description = "Log ingestion, retrieval and search")
public class LogController {
    private final IngestionService ingestionService;
    private final SearchService searchService;
    private final AnalyticsService analyticsService;
    private final LogImportService logImportService;

    @PostMapping
    @Operation(summary = "Ingest a single log entry", description = "Accepts a structured log entry payload and stores it in the system.")
    public ResponseEntity<ApiResponse<LogEntryResponse>> ingestLog(
            @Valid @RequestBody LogEntryRequest request,
            @AuthenticationPrincipal User user) {
        LogEntryResponse response = ingestionService.ingestLog(request);
        ApiResponse<LogEntryResponse> apiResponse = ApiResponse.success("Log ingested successfully", response);
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List logs (paginated)", description = "Returns a paginated list of log entries ordered by most recent first.")
    public ResponseEntity<ApiResponse<Page<LogEntryResponse>>> getLogs(
            @Parameter(description = "Page index (0-based)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        Page<LogEntryResponse> logs = ingestionService.getLogs(page, size);
        ApiResponse<Page<LogEntryResponse>> apiResponse = ApiResponse.success("Logs retrieved successfully", logs);
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/batch")
    @Operation(summary = "Ingest multiple log entries", description = "Accepts a batch of log entries in a single request for efficient ingestion.")
    public ResponseEntity<ApiResponse<BatchLogEntryResponse>> ingestBatch(
            @Valid @RequestBody BatchLogRequest request,
            @AuthenticationPrincipal User user) {
        BatchLogEntryResponse results = ingestionService.ingestBatch(request);
        ApiResponse<BatchLogEntryResponse> apiResponse = ApiResponse.success("Batch log ingestion completed", results);
        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import logs from CSV", description = "Uploads a CSV file containing log entries and starts an asynchronous import.")
    public ResponseEntity<Map<String, String>> importLogs(
            @Parameter(description = "CSV file containing log entries", required = true) @RequestParam("file") MultipartFile file
    ) {
        logImportService.initiateImport(file);
        return ResponseEntity.ok(Map.of("message", "Log import successful"));
    }

    @PostMapping("/search")
    @Operation(summary = "Search logs with advanced filters", description = "Searches logs using a rich filter object (service, level, time range, keyword, pagination).")
    public ResponseEntity<Page<LogEntryResponse>> searchLogs(
            @RequestBody LogSearchRequest request) {
        return ResponseEntity.ok(searchService.searchLogs(request));
    }

}