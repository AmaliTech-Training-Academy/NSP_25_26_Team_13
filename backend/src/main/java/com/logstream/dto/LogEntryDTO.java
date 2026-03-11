package com.logstream.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogEntryDTO {

    @JsonProperty("service_name")
    private String serviceName;

    private String level;
    private String message;
    private Map<String, String> metadata;
    private String source;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("created_at")
    private String createdAt;
}