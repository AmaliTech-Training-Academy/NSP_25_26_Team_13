package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.dto.RetentionPolicyRequest;
import com.logstream.model.RetentionPolicy;
import com.logstream.service.RetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/retention")
@RequiredArgsConstructor
public class RetentionController {

    private final RetentionService retentionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RetentionPolicy>>> getPolicies() {
        return ResponseEntity.ok(ApiResponse.success("Retention policies retrieved", retentionService.getPolicies()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RetentionPolicy>> createPolicy(@RequestBody RetentionPolicyRequest request) {
        RetentionPolicy policy = retentionService.createPolicy(request.getServiceName(), request.getRetentionDays(), request.isArchiveEnabled());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Policy created", policy));
    }

    @PutMapping("/{serviceName}")
    public ResponseEntity<ApiResponse<RetentionPolicy>> updatePolicy(
            @PathVariable String serviceName,
            @RequestBody RetentionPolicyRequest request) {
        RetentionPolicy policy = retentionService.updatePolicy(serviceName, request.getRetentionDays(), request.isArchiveEnabled());
        return ResponseEntity.ok(ApiResponse.success("Policy updated", policy));
    }

    @DeleteMapping("/{serviceName}")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable String serviceName) {
        retentionService.deletePolicy(serviceName);
        return ResponseEntity.ok(ApiResponse.success("Policy deleted", null));
    }

    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<String>> triggerCleanup() {
        retentionService.applyRetention();
        return ResponseEntity.ok(ApiResponse.success("Retention cleanup completed", null));
    }
}
