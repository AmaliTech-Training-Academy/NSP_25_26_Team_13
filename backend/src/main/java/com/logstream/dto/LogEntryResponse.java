package com.logstream.dto;

import com.logstream.model.LogLevel;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LogEntryResponse {
    private UUID id;
    private String serviceName;
    private Instant timestamp;
    private LogLevel level;
    private String message;
    private String metadata;
    private String source;
    private String traceId;
    private Instant createdAt;
}
