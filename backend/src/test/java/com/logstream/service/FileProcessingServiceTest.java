package com.logstream.service;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.logstream.dto.LogEntryDTO;
import com.logstream.exception.FileProcessingException;
import com.logstream.exception.InvalidFileException;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class FileProcessingServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private FileParsingService fileParsingService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private BatchPersistenceService batchPersistenceService;

    @InjectMocks
    private FileProcessingService fileProcessingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileProcessingService, "batchSize", 3);
    }

    @Test
    void processCSVFile_emptyFile_throwsInvalidFileException() {
        assertThatThrownBy(() -> fileProcessingService.processCSVFile(new byte[0]))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void processCSVFile_singleBatch_savesAllEntries() throws Exception {
        byte[] fileBytes = "header\nline1\nline2\n".getBytes(StandardCharsets.UTF_8);
        when(fileParsingService.parseCSVLine("line1")).thenReturn(mockLogEntry());
        when(fileParsingService.parseCSVLine("line2")).thenReturn(mockLogEntry());

        fileProcessingService.processCSVFile(fileBytes);

        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchPersistenceService).saveBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void processCSVFile_multipleBatches_savesInChunks() throws Exception {
        // batchSize = 3; 4 lines → 2 saves
        byte[] fileBytes = "header\nline1\nline2\nline3\nline4\n".getBytes(StandardCharsets.UTF_8);
        when(fileParsingService.parseCSVLine(anyString())).thenReturn(mockLogEntry());

        fileProcessingService.processCSVFile(fileBytes);

        verify(batchPersistenceService, times(2)).saveBatch(anyList());
    }

    @Test
    void processCSVFile_skipsBlankLines() throws Exception {
        byte[] fileBytes = "header\nline1\n\n   \nline2\n".getBytes(StandardCharsets.UTF_8);
        when(fileParsingService.parseCSVLine("line1")).thenReturn(mockLogEntry());
        when(fileParsingService.parseCSVLine("line2")).thenReturn(mockLogEntry());

        fileProcessingService.processCSVFile(fileBytes);

        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchPersistenceService).saveBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void processCSVFile_callsBatchPersistenceForEachBatch() throws Exception {
        byte[] fileBytes = "header\nline1\nline2\nline3\nline4\n".getBytes(StandardCharsets.UTF_8); // 2 batches
        when(fileParsingService.parseCSVLine(anyString())).thenReturn(mockLogEntry());

        fileProcessingService.processCSVFile(fileBytes);

        verify(batchPersistenceService, times(2)).saveBatch(anyList());
    }

    @Test
    void processJSONFile_emptyStream_savesNothing() throws Exception {
        MappingIterator<LogEntryDTO> iterator = mock(MappingIterator.class);
        when(iterator.hasNext()).thenReturn(false);
        ObjectReader reader = mock(ObjectReader.class);
        when(objectMapper.readerFor(LogEntryDTO.class)).thenReturn(reader);
        doReturn(iterator).when(reader).readValues(any(byte[].class));

        fileProcessingService.processJSONFile(new byte[0]);

        verify(batchPersistenceService, never()).saveBatch(anyList());
    }

    @Test
    void processJSONFile_singleBatch_savesAllEntries() throws Exception {
        LogEntryDTO dto1 = mockLogEntryDTO();
        LogEntryDTO dto2 = mockLogEntryDTO();

        byte[] fileBytes = mockJsonFile(List.of(dto1, dto2));

        fileProcessingService.processJSONFile(fileBytes);

        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchPersistenceService).saveBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void processJSONFile_multipleBatches_savesInChunks() throws Exception {
        // batchSize=3, 4 entries → 2 saves
        List<LogEntryDTO> dtos = List.of(
                mockLogEntryDTO(), mockLogEntryDTO(), mockLogEntryDTO(), mockLogEntryDTO());

        byte[] fileBytes = mockJsonFile(dtos);

        fileProcessingService.processJSONFile(fileBytes);

        verify(batchPersistenceService, times(2)).saveBatch(anyList());
    }

    @Test
    void processJSONFile_ioException_throwsFileProcessingException() throws Exception {
        ObjectReader reader = mock(ObjectReader.class);
        when(objectMapper.readerFor(LogEntryDTO.class)).thenReturn(reader);
        doThrow(new IOException("stream error")).when(reader).readValues(any(byte[].class));

        assertThatThrownBy(() -> fileProcessingService.processJSONFile(new byte[0]))
                .isInstanceOf(FileProcessingException.class)
                .hasMessageContaining("stream error");
    }

    @Test
    void processJSONFile_nullMetadata_setsNullOnEntity() throws Exception {
        LogEntryDTO dto = mockLogEntryDTO();
        dto.setMetadata(null);

        byte[] fileBytes = mockJsonFile(List.of(dto));
        fileProcessingService.processJSONFile(fileBytes);

        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchPersistenceService).saveBatch(captor.capture());
        assertThat(captor.getValue().get(0).getMetadata()).isNull();
    }

    @Test
    void processJSONFile_withMetadata_serializesMetadataToJson() throws Exception {
        LogEntryDTO dto = mockLogEntryDTO();
        dto.setMetadata(Map.of("key", "value"));

        byte[] fileBytes = mockJsonFile(List.of(dto));
        fileProcessingService.processJSONFile(fileBytes);

        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchPersistenceService).saveBatch(captor.capture());
        assertThat(captor.getValue().get(0).getMetadata()).contains("key");
    }

    @Test
    void processJSONFile_invalidLogLevel_throwsIllegalArgumentException() throws Exception {
        LogEntryDTO dto = mockLogEntryDTO();
        dto.setLevel("INVALID_LEVEL");

        byte[] fileBytes = mockJsonFile(List.of(dto));

        assertThatThrownBy(() -> fileProcessingService.processJSONFile(fileBytes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void processJSONFile_mapsAllFieldsCorrectly() throws Exception {
        LogEntryDTO dto = new LogEntryDTO();
        dto.setServiceName("auth-service");
        dto.setLevel("ERROR");
        dto.setMessage("Something failed");
        dto.setSource("AuthController.java");
        dto.setTraceId("abc-123");
        dto.setMetadata(null);

        byte[] fileBytes = mockJsonFile(List.of(dto));
        fileProcessingService.processJSONFile(fileBytes);

        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchPersistenceService).saveBatch(captor.capture());

        LogEntry entry = captor.getValue().get(0);
        assertThat(entry.getServiceName()).isEqualTo("auth-service");
        assertThat(entry.getLevel()).isEqualTo(LogLevel.ERROR);
        assertThat(entry.getMessage()).isEqualTo("Something failed");
        assertThat(entry.getSource()).isEqualTo("AuthController.java");
        assertThat(entry.getTraceId()).isEqualTo("abc-123");
        assertThat(entry.getTimestamp()).isNotNull();
    }

    private byte[] mockJsonFile(List<LogEntryDTO> dtos) throws Exception {
        Iterator<LogEntryDTO> listIter = dtos.iterator();

        @SuppressWarnings("unchecked")
        MappingIterator<LogEntryDTO> mappingIterator = mock(MappingIterator.class);
        when(mappingIterator.hasNext()).thenAnswer(inv -> listIter.hasNext());
        when(mappingIterator.next()).thenAnswer(inv -> listIter.next());

        ObjectReader reader = mock(ObjectReader.class);
        when(objectMapper.readerFor(LogEntryDTO.class)).thenReturn(reader);
        doReturn(mappingIterator).when(reader).readValues(any(byte[].class));

        return new byte[0];
    }

    private LogEntry mockLogEntry() {
        return LogEntry.builder()
                .serviceName("svc")
                .level(LogLevel.INFO)
                .message("msg")
                .timestamp(Instant.now())
                .build();
    }

    private LogEntryDTO mockLogEntryDTO() {
        LogEntryDTO dto = new LogEntryDTO();
        dto.setServiceName("svc");
        dto.setLevel("INFO");
        dto.setMessage("msg");
        dto.setSource("src");
        dto.setTraceId("trace-1");
        dto.setMetadata(Collections.emptyMap());
        return dto;
    }
}