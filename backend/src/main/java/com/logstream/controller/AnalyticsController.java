package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.dto.ErrorRateResponse;
import com.logstream.dto.CommonErrorResponse;
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

import java.time.Instant;
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
    public ResponseEntity<ApiResponse<List<CommonErrorResponse>>> getCommonErrors(
            @Parameter(description = "Service name", required = true, example = "auth-service") @RequestParam String service,
            @Parameter(description = "Max number of results (1-100, default 10)", example = "10") @RequestParam(required = false) Integer limit,
            @Parameter(description = "Start time (ISO-8601)", example = "2026-03-03T00:00:00Z") @RequestParam(required = false) Instant startTime,
            @Parameter(description = "End time (ISO-8601)", example = "2026-03-04T00:00:00Z") @RequestParam(required = false) Instant endTime) {
        List<CommonErrorResponse> response = analyticsService.getCommonErrors(service, limit, startTime,endTime);
        return ResponseEntity.ok(ApiResponse.success("Common errors retrieved successfully", response));
    }
}
