package com.logstream.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class BatchLogRequest {

    @NotEmpty(message = "Logs list cannot be empty")
    @Valid
    private List<LogEntryRequest> logs;
}
