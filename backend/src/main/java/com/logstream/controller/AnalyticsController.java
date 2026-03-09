package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.dto.ErrorRateResponse;
import com.logstream.dto.CommonErrorResponse;
import com.logstream.dto.LogVolumeResponse;
import com.logstream.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Log analytics and metrics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/error-rate")
    @Operation(summary = "Get error rate per service", description = "Returns error rate for each service in last 24 hours")
    public ResponseEntity<ApiResponse<List<ErrorRateResponse>>> getErrorRate() {
        List<ErrorRateResponse> response = analyticsService.getErrorRatePerService();
        return ResponseEntity.ok(ApiResponse.success("Error rates retrieved successfully", response));
    }

    @GetMapping("/common-errors")
    @Operation(summary = "Get top error messages", description = "Returns most frequent error messages for a service")
    public ResponseEntity<ApiResponse<List<CommonErrorResponse>>> getCommonErrors(
            @Parameter(description = "Service name", required = true, example = "auth-service") @RequestParam String service,
            @Parameter(description = "Max number of results (1-100, default 10)", example = "10") @RequestParam(required = false) Integer limit,
            @Parameter(description = "Start time (ISO-8601 or YYYY-MM-DD)", example = "2026-03-03T00:00:00Z") @RequestParam(required = false) String startTime,
            @Parameter(description = "End time (ISO-8601 or YYYY-MM-DD)", example = "2026-03-04T00:00:00Z") @RequestParam(required = false) String endTime) {
        Instant start = parseDateTime(startTime);
        Instant end = parseDateTime(endTime);
        List<CommonErrorResponse> response = analyticsService.getCommonErrors(service, limit, start, end);
        return ResponseEntity.ok(ApiResponse.success("Common errors retrieved successfully", response));
    }


    @GetMapping("/volume")
    @Operation(summary = "Get log volume by time", description = "Returns log volume aggregated by hour or day")
    public ResponseEntity<ApiResponse<List<LogVolumeResponse>>> getLogVolume(
            @Parameter(description = "Service name", required = true, example = "auth-service") @RequestParam String service,
            @Parameter(description = "Aggregation granularity: 'hour' or 'day'", required = true, example = "hour") @RequestParam String granularity,
            @Parameter(description = "Start time (ISO-8601 or YYYY-MM-DD)", example = "2026-03-01T00:00:00Z") @RequestParam(required = false) String startTime,
            @Parameter(description = "End time (ISO-8601 or YYYY-MM-DD)", example = "2026-03-04T00:00:00Z") @RequestParam(required = false) String endTime) {
        Instant start = parseDateTime(startTime);
        Instant end = parseDateTime(endTime);
        List<LogVolumeResponse> response = analyticsService.getLogVolumeTimeSeries(service, granularity, start, end);
        return ResponseEntity.ok(ApiResponse.success("Log volume retrieved successfully", response));
    }

    private Instant parseDateTime(String dateTime) {
        if (dateTime == null) {
            return null;
        }
        
        try {
            // Try parsing as ISO-8601 Instant first
            return Instant.parse(dateTime);
        } catch (Exception e) {
            try {
                // Try parsing as YYYY-MM-DD (start of day UTC)
                LocalDate date = LocalDate.parse(dateTime);
                return date.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid date format. Use ISO-8601 (e.g., 2026-03-03T00:00:00Z) or YYYY-MM-DD (e.g., 2026-03-03)");
            }
        }
    }
}

