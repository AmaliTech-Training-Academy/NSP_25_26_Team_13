package com.logstream.controller;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/error-rate")
    public ResponseEntity<List<ErrorRateResponse>> getErrorRate() {
        List<ErrorRateResponse> errorRates = analyticsService.getErrorRateByService();
        return ResponseEntity.ok(errorRates);
    }
}
