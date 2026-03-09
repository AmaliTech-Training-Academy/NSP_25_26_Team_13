package com.logstream.dto;

import com.logstream.model.LogLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.Instant;
import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LogEntryRequest {

    @NotBlank(message = "Service name is required")
    private String serviceName;

    @NotNull(message = "Log level is required")
    private LogLevel level;

    @NotBlank(message = "Message is required")
    private String message;

    private Map<String, String> metadata;

    private String source;

    private String traceId;
}