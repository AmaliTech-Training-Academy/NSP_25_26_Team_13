package com.logstream.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonErrorsRequest {
    @NotBlank(message = "Service name is required")
    private String service;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit cannot exceed 100")
    private Integer limit = 10;

    private Long startTime;
    private Long endTime;
}
