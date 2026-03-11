package com.logstream.controller;

import com.logstream.common.response.ApiResponse;
import com.logstream.service.LogCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCleanupController {

    private final LogCleanupService logCleanupService;

    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<String>> triggerCleanup() {
        logCleanupService.scheduledCleanup();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Cleanup started", null));
    }
}

