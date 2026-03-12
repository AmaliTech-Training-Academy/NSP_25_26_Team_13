package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.service.LogCleanupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative maintenance operations")
public class AdminCleanupController {

    private final LogCleanupService logCleanupService;

    @PostMapping("/cleanup")
    @Operation(summary = "Trigger full log cleanup", description = "Starts the background job that performs log cleanup according to retention policies.")
    public ResponseEntity<ApiResponse<String>> triggerCleanup() {
        logCleanupService.scheduledCleanup();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Cleanup started", null));
    }
}

