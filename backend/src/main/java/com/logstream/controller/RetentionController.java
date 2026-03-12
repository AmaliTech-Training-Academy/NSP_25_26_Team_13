package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.dto.RetentionPolicyRequest;
import com.logstream.model.RetentionPolicy;
import com.logstream.service.RetentionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/retention")
@RequiredArgsConstructor
@Tag(name = "Retention", description = "Manage log retention policies and trigger cleanup")
public class RetentionController {

    private final RetentionService retentionService;

    @GetMapping
    @Operation(summary = "List retention policies", description = "Returns all configured retention policies for services.")
    public ResponseEntity<ApiResponse<List<RetentionPolicy>>> getPolicies() {
        return ResponseEntity.ok(ApiResponse.success("Retention policies retrieved", retentionService.getPolicies()));
    }

    @PostMapping
    @Operation(summary = "Create a retention policy", description = "Creates a new retention policy for the given service.")
    public ResponseEntity<ApiResponse<RetentionPolicy>> createPolicy(@RequestBody RetentionPolicyRequest request) {
        RetentionPolicy policy = retentionService.createPolicy(request.getServiceName(), request.getRetentionDays(), request.isArchiveEnabled());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Policy created", policy));
    }

    @PutMapping("/{serviceName}")
    @Operation(summary = "Update a retention policy", description = "Updates the retention policy for the specified service.")
    public ResponseEntity<ApiResponse<RetentionPolicy>> updatePolicy(
            @Parameter(description = "Service name whose policy should be updated", example = "auth-service")
            @PathVariable String serviceName,
            @RequestBody RetentionPolicyRequest request) {
        RetentionPolicy policy = retentionService.updatePolicy(serviceName, request.getRetentionDays(), request.isArchiveEnabled());
        return ResponseEntity.ok(ApiResponse.success("Policy updated", policy));
    }

    @DeleteMapping("/{serviceName}")
    @Operation(summary = "Delete a retention policy", description = "Deletes the retention policy for the specified service.")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(
            @Parameter(description = "Service name whose policy should be deleted", example = "auth-service")
            @PathVariable String serviceName) {
        retentionService.deletePolicy(serviceName);
        return ResponseEntity.ok(ApiResponse.success("Policy deleted", null));
    }

    @PostMapping("/cleanup")
    @Operation(summary = "Trigger retention cleanup", description = "Manually triggers the retention cleanup process based on current policies.")
    public ResponseEntity<ApiResponse<String>> triggerCleanup() {
        retentionService.applyRetention();
        return ResponseEntity.ok(ApiResponse.success("Retention cleanup completed", null));
    }
}
