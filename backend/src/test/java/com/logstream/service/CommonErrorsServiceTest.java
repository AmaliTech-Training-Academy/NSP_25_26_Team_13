package com.logstream.service;

import com.logstream.dto.CommonErrorResponse;
import com.logstream.dto.CommonErrorsRequest;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonErrorsServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getCommonErrors_shouldReturnTopErrorsByCount() {
        CommonErrorsRequest request = new CommonErrorsRequest("auth-service", 5, null, null);
        
        List<Object[]> mockResults = Arrays.asList(
            new Object[]{"Connection timeout", 123L},
            new Object[]{"Invalid credentials", 87L},
            new Object[]{"Database error", 45L}
        );
        
        when(logEntryRepository.findTopErrorMessagesByService(eq("auth-service"), any(Instant.class)))
            .thenReturn(mockResults);

        List<CommonErrorResponse> result = analyticsService.getCommonErrors(request);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getMessage()).isEqualTo("Connection timeout");
        assertThat(result.get(0).getCount()).isEqualTo(123L);
        assertThat(result.get(1).getMessage()).isEqualTo("Invalid credentials");
        assertThat(result.get(1).getCount()).isEqualTo(87L);
    }

    @Test
    void getCommonErrors_shouldRespectLimit() {
        CommonErrorsRequest request = new CommonErrorsRequest("auth-service", 2, null, null);
        
        List<Object[]> mockResults = Arrays.asList(
            new Object[]{"Error 1", 100L},
            new Object[]{"Error 2", 80L},
            new Object[]{"Error 3", 60L}
        );
        
        when(logEntryRepository.findTopErrorMessagesByService(eq("auth-service"), any(Instant.class)))
            .thenReturn(mockResults);

        List<CommonErrorResponse> result = analyticsService.getCommonErrors(request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMessage()).isEqualTo("Error 1");
        assertThat(result.get(1).getMessage()).isEqualTo("Error 2");
    }

    @Test
    void getCommonErrors_shouldReturnEmptyListWhenNoErrors() {
        CommonErrorsRequest request = new CommonErrorsRequest("empty-service", 10, null, null);
        
        when(logEntryRepository.findTopErrorMessagesByService(eq("empty-service"), any(Instant.class)))
            .thenReturn(Collections.emptyList());

        List<CommonErrorResponse> result = analyticsService.getCommonErrors(request);

        assertThat(result).isEmpty();
    }

    @Test
    void getCommonErrors_shouldUseCustomTimeRangeWhenProvided() {
        Long startTime = 1609459200000L;
        Long endTime = 1609545600000L;
        CommonErrorsRequest request = new CommonErrorsRequest("auth-service", 10, startTime, endTime);
        
        List<Object[]> mockResults = Arrays.asList(
            new Object[]{"Timeout", 50L}
        );
        
        when(logEntryRepository.findTopErrorMessagesByServiceAndTimeRange(
            eq("auth-service"), any(Instant.class), any(Instant.class)))
            .thenReturn(mockResults);

        List<CommonErrorResponse> result = analyticsService.getCommonErrors(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("Timeout");
    }
}
