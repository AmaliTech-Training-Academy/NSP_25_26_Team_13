package com.logstream.dto;

import com.logstream.model.LogLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEntryRequest {

    @NotBlank(message = "Service name is required")
    @Pattern(regexp = "^[a-zA-Z0-9-_]+$", message = "Service name can only contain letters, numbers, hyphens, and underscores")
    private String serviceName;

    @NotNull(message = "Log level is required")
    private LogLevel level;

    @NotBlank(message = "Message is required")
    @Pattern(regexp = "^.{1,2000}$", message = "Message must be between 1 and 2000 characters")
    private String message;

    private Map<String, String> metadata;

    @Pattern(regexp = "^[a-zA-Z0-9-_]+$", message = "Source can only contain letters, numbers, hyphens, and underscores")
    private String source;

    @Pattern(regexp = "^[a-zA-Z0-9-_]+$", message = "Trace ID can only contain letters, numbers, hyphens, and underscores")
    private String traceId;
}