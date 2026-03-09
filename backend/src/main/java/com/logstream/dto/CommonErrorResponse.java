package com.logstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A common error message with its occurrence count")
public class CommonErrorResponse {

    @Schema(description = "Error message text", example = "Connection timeout")
    private String message;

    @Schema(description = "Number of occurrences", example = "123")
    private long count;
}
