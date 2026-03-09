package com.logstream.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogVolumeRequest {
    @NotBlank(message = "Service name is required")
    private String service;

    @NotBlank(message = "Granularity is required")
    @Pattern(regexp = "^(hour|day)$", message = "Granularity must be 'hour' or 'day'")
    private String granularity;

    private Long startTime;
    private Long endTime;
}
