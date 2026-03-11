package com.logstream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logstream.dto.BatchLogEntryResponse;
import com.logstream.dto.BatchLogRequest;
import com.logstream.dto.LogEntryRequest;
import com.logstream.dto.LogEntryResponse;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class IngestionServiceTests {

    @Mock
    private LogEntryRepository logEntryRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IngestionService service;

    private LogEntry savedEntry;

    @BeforeEach
    void setUp() {
        savedEntry = LogEntry.builder()
                .id(UUID.randomUUID())
                .serviceName("auth-service")
                .level(LogLevel.INFO)
                .message("User logged in")
                .source("com.example.Auth")
                .traceId("trace-123")
                .timestamp(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void getLogs_repositoryReturnsEntries_mappedToResponseList() {
        when(logEntryRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(savedEntry)));

        List<LogEntryResponse> result = service.getLogs(0, 20).getContent();

        assertThat(result).hasSize(1);
        LogEntryResponse response = result.get(0);
        assertThat(response.getId()).isEqualTo(savedEntry.getId());
        assertThat(response.getServiceName()).isEqualTo(savedEntry.getServiceName());
        assertThat(response.getLevel()).isEqualTo(savedEntry.getLevel());
        assertThat(response.getMessage()).isEqualTo(savedEntry.getMessage());
        assertThat(response.getSource()).isEqualTo(savedEntry.getSource());
        assertThat(response.getTraceId()).isEqualTo(savedEntry.getTraceId());
    }

    @Test
    void getLogs_repositoryReturnsEmpty_returnsEmptyList() {
        when(logEntryRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.getLogs(0, 20).getContent()).isEmpty();
    }

    @Test
    void getLogs_repositoryReturnsMultipleEntries_allMapped() {
        LogEntry second = LogEntry.builder().id(UUID.randomUUID()).serviceName("svc2")
                .level(LogLevel.ERROR).message("Boom").timestamp(Instant.now()).build();
        when(logEntryRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(savedEntry, second)));

        assertThat(service.getLogs(0, 20).getContent()).hasSize(2);
    }

    @Test
    void ingestLog_validRequest_savedAndResponseReturned() {
        LogEntryRequest request = buildRequest("auth-service", "INFO", "User logged in",
                "com.example.Auth", "trace-123");
        when(logEntryRepository.save(any(LogEntry.class))).thenReturn(savedEntry);

        LogEntryResponse result = service.ingestLog(request);

        assertThat(result.getId()).isEqualTo(savedEntry.getId());
        assertThat(result.getServiceName()).isEqualTo("auth-service");
        assertThat(result.getLevel()).isEqualTo(LogLevel.INFO);
        verify(logEntryRepository, times(1)).save(any(LogEntry.class));
    }

    @Test
    void ingestLog_lowercaseLevel_throwsIllegalArgumentException() {
        LogEntryRequest request = buildRequest("svc", "warn", "msg", null, null);

        assertThatThrownBy(() -> service.ingestLog(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ingestLog_mixedCaseLevel_throwsIllegalArgumentException() {
        LogEntryRequest request = buildRequest("svc", "Debug", "msg", null, null);

        assertThatThrownBy(() -> service.ingestLog(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ingestLog_entityTimestampIsSetToNowApproximately() {
        LogEntryRequest request = buildRequest("svc", "INFO", "msg", null, null);
        Instant before = Instant.now();
        when(logEntryRepository.save(any(LogEntry.class))).thenReturn(savedEntry);

        service.ingestLog(request);

        Instant after = Instant.now();
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getTimestamp())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    void ingestBatch_validRequests_allSavedAndCountReturned() {
        BatchLogRequest batchRequest = new BatchLogRequest(List.of(
                buildRequest("svc1", "INFO", "msg1", null, null),
                buildRequest("svc2", "ERROR", "msg2", null, null)
        ));

        BatchLogEntryResponse result = service.ingestBatch(batchRequest);

        assertThat(result.getCount()).isEqualTo(2);
        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(logEntryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void ingestBatch_emptyList_savesNothingAndReturnsZero() {
        BatchLogRequest batchRequest = new BatchLogRequest(List.of());

        BatchLogEntryResponse result = service.ingestBatch(batchRequest);

        assertThat(result.getCount()).isZero();
        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(logEntryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void ingestBatch_singleEntry_countIsOne() {
        BatchLogRequest batchRequest = new BatchLogRequest(List.of(
                buildRequest("svc", "DEBUG", "msg", null, null)
        ));

        BatchLogEntryResponse result = service.ingestBatch(batchRequest);

        assertThat(result.getCount()).isEqualTo(1);
    }

    @Test
    void ingestLog_nullLevel_throwsBadRequestException() {
        LogEntryRequest request = buildRequest("svc", null, "msg", null, null);

        assertThatThrownBy(() -> service.ingestLog(request))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Name is null");
    }

    @Test
    void ingestLog_blankLevel_throwsBadRequestException() {
        LogEntryRequest request = buildRequest("svc", "   ", "msg", null, null);

        assertThatThrownBy(() -> service.ingestLog(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant com.logstream.model.LogLevel.");
    }

    @Test
    void ingestLog_unknownLevel_throwsBadRequestException() {
        LogEntryRequest request = buildRequest("svc", "VERBOSE", "msg", null, null);

        assertThatThrownBy(() -> service.ingestLog(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant com.logstream.model.LogLevel.VERBOSE");
    }

    @ParameterizedTest
    @ValueSource(strings = { "DEBUG", "INFO", "WARN", "ERROR" })
    void ingestLog_allValidLevels_noExceptionThrown(String level) {
        LogEntryRequest request = buildRequest("svc", level, "msg", null, null);
        when(logEntryRepository.save(any(LogEntry.class))).thenReturn(savedEntry);

        assertThatNoException().isThrownBy(() -> service.ingestLog(request));
    }

    @Test
    void ingestBatch_oneEntryHasInvalidLevel_throwsBadRequestExceptionAndNothingSaved() {
        BatchLogRequest batchRequest = new BatchLogRequest(List.of(
                buildRequest("svc1", "INFO", "msg1", null, null),
                buildRequest("svc2", "INVALID", "msg2", null, null)
        ));

        assertThatThrownBy(() -> service.ingestBatch(batchRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant com.logstream.model.LogLevel.INVALID");
        verify(logEntryRepository, never()).saveAll(any());
    }

    private LogEntryRequest buildRequest(String serviceName, String level, String message,
                                         String source, String traceId) {
        return LogEntryRequest.builder()
                .serviceName(serviceName)
                .level(level)
                .message(message)
                .source(source)
                .traceId(traceId)
                .metadata(null)
                .build();
    }
}