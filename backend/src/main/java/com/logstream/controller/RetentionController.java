package com.logstream.controller;

import com.logstream.model.RetentionPolicy;
import com.logstream.repository.RetentionPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/retention")
@RequiredArgsConstructor
public class RetentionController {
    private final RetentionPolicyRepository retentionPolicyRepository;

    @GetMapping
    public ResponseEntity<List<RetentionPolicy>> getPolicies() {
        return ResponseEntity.ok(retentionPolicyRepository.findAll());
    }

    // TODO: Add CRUD endpoints for retention policies (admin only)
    // TODO: Add trigger cleanup endpoint - POST /api/retention/cleanup
}
