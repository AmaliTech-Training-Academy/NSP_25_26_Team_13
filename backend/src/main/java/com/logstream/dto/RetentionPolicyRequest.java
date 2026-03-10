package com.logstream.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPolicyRequest {
    private String serviceName;
    private int retentionDays = 30;
    private boolean archiveEnabled = false;
}
