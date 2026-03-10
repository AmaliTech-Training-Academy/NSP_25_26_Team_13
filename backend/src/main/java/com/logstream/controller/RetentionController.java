package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.model.RetentionPolicy;
import com.logstream.service.RetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<ApiResponse<RetentionPolicy>> createPolicy(@RequestBody Map<String, Object> body) {
        String serviceName = (String) body.get("serviceName");
        int days = (int) body.getOrDefault("retentionDays", 30);
        boolean archive = (boolean) body.getOrDefault("archiveEnabled", false);
        RetentionPolicy policy = retentionService.createPolicy(serviceName, days, archive);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Policy created", policy));
    }

    @PutMapping("/{serviceName}")
    public ResponseEntity<ApiResponse<RetentionPolicy>> updatePolicy(
            @PathVariable String serviceName,
            @RequestBody Map<String, Object> body) {
        int days = (int) body.getOrDefault("retentionDays", 30);
        boolean archive = (boolean) body.getOrDefault("archiveEnabled", false);
        RetentionPolicy policy = retentionService.updatePolicy(serviceName, days, archive);
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
