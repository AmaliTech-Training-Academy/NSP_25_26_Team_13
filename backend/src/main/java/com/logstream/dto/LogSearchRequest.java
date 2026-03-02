package com.logstream.dto;

import com.logstream.model.LogLevel;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LogSearchRequest {
    private String serviceName;
    private LogLevel level;
    private String startTime;
    private String endTime;
    private String keyword;
    private int page = 0;
    private int size = 20;
}
