package service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logstream.dto.BatchLogEntryResponse;
import com.logstream.dto.BatchLogRequest;
import com.logstream.dto.LogEntryRequest;
import com.logstream.dto.LogEntryResponse;
import com.logstream.exception.BadRequestException;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import com.logstream.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTests {

    @Mock
    private LogEntryRepository logEntryRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IngestionService ingestionService;

    @Test
    void ingestLog_shouldSaveAndReturnResponse() throws Exception {
        LogEntryRequest request = LogEntryRequest.builder()
                .serviceName("payment-service")
                .level("INFO")
                .message("Payment processed")
                .source("api")
                .traceId("trace123")
                .metadata(Map.of("orderId", "123"))
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"orderId\":\"123\"}");

        LogEntry saved = LogEntry.builder()
                .id(UUID.randomUUID())
                .serviceName("payment-service")
                .level(LogLevel.INFO)
                .message("Payment processed")
                .metadata("{\"orderId\":\"123\"}")
                .source("api")
                .traceId("trace123")
                .timestamp(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(logEntryRepository.save(any(LogEntry.class))).thenReturn(saved);

        LogEntryResponse response = ingestionService.ingestLog(request);

        assertNotNull(response);
        assertEquals("payment-service", response.getServiceName());
        assertEquals("INFO", response.getLevel().name());
        assertEquals("Payment processed", response.getMessage());

        verify(logEntryRepository, times(1)).save(any(LogEntry.class));
        verify(objectMapper, times(1)).writeValueAsString(request.getMetadata());
    }

    @Test
    void ingestBatch_shouldSaveAllLogs() throws Exception {
        LogEntryRequest req1 = LogEntryRequest.builder()
                .serviceName("serviceA")
                .level("INFO")
                .message("log1")
                .metadata(Map.of("key1", "value1"))
                .build();

        LogEntryRequest req2 = LogEntryRequest.builder()
                .serviceName("serviceB")
                .level("ERROR")
                .message("log2")
                .metadata(Map.of("key2", "value2"))
                .build();

        BatchLogRequest batchRequest = new BatchLogRequest();
        batchRequest.setLogs(List.of(req1, req2));

        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"key1\":\"value1\"}")
                .thenReturn("{\"key2\":\"value2\"}");

        when(logEntryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BatchLogEntryResponse response = ingestionService.ingestBatch(batchRequest);

        assertEquals(2, response.getCount());

        verify(logEntryRepository, times(1)).saveAll(anyList());
        verify(objectMapper, times(2)).writeValueAsString(any());
    }

    @Test
    void ingestLog_shouldReturnEmptyMetadataIfSerializationFails() throws Exception {
        LogEntryRequest request = LogEntryRequest.builder()
                .serviceName("test-service")
                .level("INFO")
                .message("test message")
                .metadata(Map.of("key", "value"))
                .build();

        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Serialization failed") {
                });

        LogEntry saved = LogEntry.builder()
                .id(UUID.randomUUID())
                .serviceName("test-service")
                .level(LogLevel.INFO)
                .message("test message")
                .metadata("{}")
                .timestamp(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(logEntryRepository.save(any())).thenReturn(saved);

        LogEntryResponse response = ingestionService.ingestLog(request);

        assertEquals("{}", response.getMetadata());
        verify(logEntryRepository).save(any(LogEntry.class));
    }

    @Test
    void ingestLog_shouldThrowBadRequestForInvalidLogLevel() {
        LogEntryRequest request = LogEntryRequest.builder()
                .serviceName("service")
                .level("INVALID")
                .message("test")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> ingestionService.ingestLog(request));

        assertEquals("Log level must be one of: DEBUG, INFO, WARN, ERROR", ex.getMessage());
    }

    @Test
    void ingestLog_shouldThrowBadRequestForBlankLogLevel() {
        LogEntryRequest request = LogEntryRequest.builder()
                .serviceName("service")
                .level("  ")
                .message("test")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> ingestionService.ingestLog(request));

        assertEquals("Log level is required", ex.getMessage());
    }

    @Test
    void getLogs_shouldReturnMappedLogEntryResponses() {
        LogEntry entry = LogEntry.builder()
                .id(UUID.randomUUID())
                .serviceName("service")
                .level(LogLevel.INFO)
                .message("log message")
                .timestamp(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(logEntryRepository.findAll()).thenReturn(List.of(entry));

        List<LogEntryResponse> responses = ingestionService.getLogs();

        assertEquals(1, responses.size());
        assertEquals("service", responses.getFirst().getServiceName());
        assertEquals("INFO", responses.getFirst().getLevel().name());
    }
}