package com.logstream.service;

import com.logstream.model.RetentionPolicy;
import com.logstream.repository.RetentionPolicyRepository;
import com.logstream.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RetentionService {

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final LogEntryRepository logEntryRepository;

    // TODO (Dev B): For each policy, delete logs older than retentionDays for that service
    public void applyRetention() {
        throw new UnsupportedOperationException("Retention not yet implemented");
    }

    // TODO (Dev B): Create new retention policy
    public RetentionPolicy createPolicy(String serviceName, int days, boolean archive) {
        throw new UnsupportedOperationException("Create policy not yet implemented");
    }

    // TODO (Dev B): List all retention policies
    public List<RetentionPolicy> getPolicies() {
        throw new UnsupportedOperationException("Get policies not yet implemented");
    }
}
