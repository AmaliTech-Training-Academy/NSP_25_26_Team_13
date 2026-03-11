package com.logstream.controller;

import com.logstream.dto.LogEntryResponse;
import com.logstream.dto.LogSearchRequest;
import com.logstream.model.LogLevel;
import com.logstream.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogSearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    public Page<LogEntryResponse> searchLogs(
            @RequestParam(name = "service", required = false) String service,
            @RequestParam(name = "level", required = false) LogLevel level,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        LogSearchRequest request = LogSearchRequest.builder()
                .serviceName(service)
                .level(level)
                .startTime(startTime)
                .endTime(endTime)
                .keyword(keyword)
                .page(page)
                .size(size)
                .build();

        return searchService.searchLogs(request);
    }
}

