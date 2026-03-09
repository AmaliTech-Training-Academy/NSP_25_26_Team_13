package com.logstream.dto;

import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogVolumeResponse {
    private Instant timestamp;
    private String service;
    private Long count;
}
