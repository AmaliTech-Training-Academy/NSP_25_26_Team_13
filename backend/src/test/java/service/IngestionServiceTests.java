package service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logstream.dto.BatchLogEntryResponse;
import com.logstream.dto.BatchLogRequest;
import com.logstream.dto.LogEntryRequest;
import com.logstream.dto.LogEntryResponse;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import com.logstream.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class IngestionServiceTests {

    private LogEntryRepository logEntryRepository;
    private ObjectMapper objectMapper;
    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        logEntryRepository = mock(LogEntryRepository.class);
        objectMapper = mock(ObjectMapper.class);

        ingestionService = new IngestionService(logEntryRepository, objectMapper);
    }

    @Test
    void ingestLog_shouldSaveLogAndReturnResponse() throws Exception {

        LogEntryRequest request = new LogEntryRequest();
        request.setServiceName("payment-service");
        request.setLevel(LogLevel.INFO);
        request.setMessage("Payment processed");
        request.setSource("api");
        request.setTraceId("trace123");
        request.setMetadata(Map.of("orderId", "123"));

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
    }

    @Test
    void ingestBatch_shouldSaveAllLogs() throws Exception {

        LogEntryRequest req1 = new LogEntryRequest();
        req1.setServiceName("serviceA");
        req1.setLevel(LogLevel.INFO);
        req1.setMessage("log1");

        LogEntryRequest req2 = new LogEntryRequest();
        req2.setServiceName("serviceB");
        req2.setLevel(LogLevel.ERROR);
        req2.setMessage("log2");

        BatchLogRequest batchRequest = new BatchLogRequest();
        batchRequest.setLogs(List.of(req1, req2));

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        BatchLogEntryResponse response = ingestionService.ingestBatch(batchRequest);

        assertEquals(2, response.getCount());

        verify(logEntryRepository, times(1)).saveAll(anyList());
    }

    @Test
    void ingestLog_shouldReturnEmptyMetadataIfSerializationFails() throws Exception {

        LogEntryRequest request = new LogEntryRequest();
        request.setServiceName("test-service");
        request.setLevel(LogLevel.INFO);
        request.setMessage("test message");
        request.setMetadata(Map.of("key", "value"));

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
}