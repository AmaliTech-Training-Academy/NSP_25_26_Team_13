package com.logstream.controller;

import com.logstream.common.response.ApiResponse;

import com.logstream.dto.ErrorRateResponse;

import com.logstream.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Operation(summary = "Get error rate per service", description = "Returns the error rate for each service over the last 24 hours. "
            + "Error rate is calculated as (ERROR count / total count) * 100, rounded to 2 decimal places.", responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Error rates per service", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ErrorRateResponse.class)), examples = @ExampleObject(value = "[{\"service\":\"auth-service\","
            + "\"errorRate\":5.2,\"errorCount\":52,\"totalCount\":1000}]"))))
    @GetMapping("/error-rate")
    public ResponseEntity<ApiResponse<List<ErrorRateResponse>>> getErrorRate() {
        List<ErrorRateResponse> response = analyticsService.getErrorRatePerService();
        return ResponseEntity.ok(ApiResponse.success("Error rates retrieved successfully", response));
    }

}
