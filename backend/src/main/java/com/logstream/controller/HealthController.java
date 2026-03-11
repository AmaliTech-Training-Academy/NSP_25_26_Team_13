package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.dto.ServiceHealthResponse;
import com.logstream.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Service health monitoring")
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get health dashboard", description = "Returns health status for all services")
    public ResponseEntity<ApiResponse<List<ServiceHealthResponse>>> getDashboard() {
        List<ServiceHealthResponse> response = healthService.getHealthDashboard();
        return ResponseEntity.ok(ApiResponse.success("Health dashboard retrieved successfully", response));
    }
}
