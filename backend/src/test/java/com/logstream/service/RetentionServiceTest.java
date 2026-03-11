package com.logstream.service;

import com.logstream.model.RetentionPolicy;
import com.logstream.repository.LogEntryRepository;
import com.logstream.repository.RetentionPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock
    private RetentionPolicyRepository retentionPolicyRepository;

    @Mock
    private LogEntryRepository logEntryRepository;

    @InjectMocks
    private RetentionService retentionService;

    @Test
    void createPolicy_savesAndReturnsPolicy() {
        RetentionPolicy saved = RetentionPolicy.builder()
                .serviceName("auth-service").retentionDays(7).archiveEnabled(false).build();
        when(retentionPolicyRepository.save(any())).thenReturn(saved);

        RetentionPolicy result = retentionService.createPolicy("auth-service", 7, false);

        assertThat(result.getServiceName()).isEqualTo("auth-service");
        assertThat(result.getRetentionDays()).isEqualTo(7);
        verify(retentionPolicyRepository).save(any());
    }

    @Test
    void updatePolicy_updatesExistingPolicy() {
        RetentionPolicy existing = RetentionPolicy.builder()
                .serviceName("auth-service").retentionDays(30).archiveEnabled(false).build();
        when(retentionPolicyRepository.findByServiceName("auth-service")).thenReturn(Optional.of(existing));
        when(retentionPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RetentionPolicy result = retentionService.updatePolicy("auth-service", 14, true);

        assertThat(result.getRetentionDays()).isEqualTo(14);
        assertThat(result.isArchiveEnabled()).isTrue();
    }

    @Test
    void updatePolicy_notFound_throwsException() {
        when(retentionPolicyRepository.findByServiceName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> retentionService.updatePolicy("unknown", 7, false))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void deletePolicy_deletesExistingPolicy() {
        RetentionPolicy policy = RetentionPolicy.builder().serviceName("auth-service").build();
        when(retentionPolicyRepository.findByServiceName("auth-service")).thenReturn(Optional.of(policy));

        retentionService.deletePolicy("auth-service");

        verify(retentionPolicyRepository).delete(policy);
    }

    @Test
    void deletePolicy_notFound_throwsException() {
        when(retentionPolicyRepository.findByServiceName("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> retentionService.deletePolicy("unknown"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getPolicies_returnsAll() {
        List<RetentionPolicy> policies = List.of(
                RetentionPolicy.builder().serviceName("svc-a").retentionDays(30).build(),
                RetentionPolicy.builder().serviceName("svc-b").retentionDays(7).build()
        );
        when(retentionPolicyRepository.findAll()).thenReturn(policies);

        List<RetentionPolicy> result = retentionService.getPolicies();

        assertThat(result).hasSize(2);
    }

    @Test
    void applyRetention_deletesLogsPerPolicy() {
        RetentionPolicy policy = RetentionPolicy.builder()
                .serviceName("auth-service").retentionDays(7).build();
        when(retentionPolicyRepository.findAll()).thenReturn(List.of(policy));
        when(logEntryRepository.findDistinctServiceNames()).thenReturn(List.of("auth-service"));

        retentionService.applyRetention();

        verify(logEntryRepository).deleteByServiceNameAndCreatedAtBefore(eq("auth-service"), any(Instant.class));
    }

    @Test
    void applyRetention_appliesDefaultRetentionToServicesWithoutPolicy() {
        when(retentionPolicyRepository.findAll()).thenReturn(List.of());
        when(logEntryRepository.findDistinctServiceNames()).thenReturn(List.of("payment-service"));

        retentionService.applyRetention();

        // should use default 30-day cutoff for service with no custom policy
        verify(logEntryRepository).deleteByServiceNameAndCreatedAtBefore(eq("payment-service"), any(Instant.class));
    }
}
