package com.logstream.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.logstream.dto.LogEntryDTO;
import com.logstream.exception.FileProcessingException;
import com.logstream.exception.InvalidFileException;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final FileParsingService fileParsingService;
    private final LogEntryRepository logEntryRepository;
    private final BatchPersistenceService batchPersistenceService;
    private static final ObjectWriter METADATA_WRITER = new ObjectMapper().writer();

    @Value("${log.import.batch.size:500}")
    private int batchSize;

    @Async("fileProcessingExecutor")
    public void processCSVFile(byte[] fileBytes) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (header == null) {
                throw new InvalidFileException("CSV file is empty.");
            }

            List<LogEntry> batch = new ArrayList<>(batchSize);
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                LogEntry entry = fileParsingService.parseCSVLine(line);
                batch.add(entry);
                if (batch.size() >= batchSize) {
                    batchPersistenceService.saveBatch(batch);
                    batch = new ArrayList<>(batchSize);
                }
            }

            if (!batch.isEmpty()) batchPersistenceService.saveBatch(batch);

        } catch (IOException e) {
            throw new FileProcessingException("Failed to process CSV file: " + e.getMessage());
        }
    }

    @Async("fileProcessingExecutor")
    public void processJSONFile(byte[] fileBytes) {
        try (MappingIterator<LogEntryDTO> iterator =
                     objectMapper.readerFor(LogEntryDTO.class)
                             .readValues(fileBytes)) {

            List<LogEntry> batch = new ArrayList<>(batchSize);
            while (iterator.hasNext()) {
                batch.add(mapToEntity(iterator.next()));
                if (batch.size() >= batchSize) {
                    batchPersistenceService.saveBatch(batch);
                    batch = new ArrayList<>(batchSize);
                }
            }
            if (!batch.isEmpty()) batchPersistenceService.saveBatch(batch);

        } catch (IOException e) {
            throw new FileProcessingException("Failed to process JSON file: " + e.getMessage());
        }
    }

    private LogEntry mapToEntity(LogEntryDTO request) {
        String metadataJson = null;
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            try {
                metadataJson = METADATA_WRITER.writeValueAsString(request.getMetadata());
            } catch (JsonProcessingException e) {
                throw new FileProcessingException("Failed to serialize metadata to JSON");
            }
        }

        return LogEntry.builder()
                .serviceName(request.getServiceName())
                .level(LogLevel.valueOf(request.getLevel().toUpperCase()))
                .message(request.getMessage())
                .source(request.getSource())
                .traceId(request.getTraceId())
                .metadata(metadataJson)
                .timestamp(Instant.now())
                .createdAt(request.getCreatedAt() != null
                        ? LocalDateTime.parse(request.getCreatedAt()).toInstant(ZoneOffset.UTC)
                        : null)
                .build();
    }

    private void saveBatch(List<LogEntry> batch) {
        logEntryRepository.saveAll(batch);
        entityManager.flush();
        entityManager.clear();
    }
}