package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.dto.ErrorRateResponse;
import com.logstream.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/error-rate")
    public ResponseEntity<ApiResponse<List<ErrorRateResponse>>> getErrorRate() {
        List<ErrorRateResponse> response = analyticsService.getErrorRatePerService();
        return ResponseEntity.ok(ApiResponse.success("Error rates retrieved successfully", response));
    }
}
