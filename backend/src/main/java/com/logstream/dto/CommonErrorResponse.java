package com.logstream.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonErrorResponse {
    private String message;
    private Long count;
}
