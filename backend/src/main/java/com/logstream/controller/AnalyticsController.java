package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.dto.ErrorRateResponse;
import com.logstream.dto.CommonErrorResponse;
import com.logstream.dto.CommonErrorsRequest;
import com.logstream.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

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
        @Valid CommonErrorsRequest request) {
        List<CommonErrorResponse> response = analyticsService.getCommonErrors(request);
        return ResponseEntity.ok(ApiResponse.success("Common errors retrieved successfully", response));
    }
}
