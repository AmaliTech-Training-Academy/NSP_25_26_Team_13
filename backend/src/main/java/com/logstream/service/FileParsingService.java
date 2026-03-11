package com.logstream.service;

import com.logstream.dto.LogEntryCSV;
import com.logstream.exception.FileProcessingException;
import com.logstream.exception.InvalidFileException;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.opencsv.CSVReader;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileParsingService {

    public LogEntry parseCSVLine(String line) {
        if (line == null) {
            throw new FileProcessingException("CSV line is null");
        }

        String header = "id,timestamp,level,source,message,service_name,created_at";
        String csvWithHeader = header + "\n" + line;

        try (CSVReader reader = new CSVReader(new StringReader(csvWithHeader))) {

            HeaderColumnNameMappingStrategy<LogEntryCSV> strategy =
                    new HeaderColumnNameMappingStrategy<>();
            strategy.setType(LogEntryCSV.class);

            CsvToBean<LogEntryCSV> csvToBean =
                    new CsvToBeanBuilder<LogEntryCSV>(reader)
                            .withMappingStrategy(strategy)
                            .build();

            List<LogEntryCSV> results = csvToBean.parse();
            if (results.isEmpty()) {
                throw new InvalidFileException("Failed to parse CSV line (no data parsed): " + line);
            }

            return mapCsvToEntity(results.get(0));

        } catch (NoSuchElementException e) {
            log.error("Failed to parse CSV line: {}, {}", line, e.getMessage(), e);
            throw new InvalidFileException("Failed to parse CSV line: " + line + ": " + e.getMessage());
        } catch (DateTimeParseException e) {
            throw new InvalidFileException("Invalid timestamp format in CSV line: " + line + ": " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new InvalidFileException("Invalid log level in CSV line: " + line + ": " + e.getMessage());
        } catch (IOException e) {
            throw new FileProcessingException("Failed to parse CSV line: " + line + ": " + e.getMessage());
        }
    }

    private LogEntry mapCsvToEntity(LogEntryCSV entryCSV) {
        return LogEntry.builder()
                .serviceName(entryCSV.getServiceName())
                .level(LogLevel.valueOf(entryCSV.getLevel().toUpperCase()))
                .message(entryCSV.getMessage())
                .source(entryCSV.getSource())
                .timestamp(Instant.now())
                .createdAt(entryCSV.getCreatedAt() != null
                        ? LocalDateTime.parse(entryCSV.getCreatedAt()).toInstant(ZoneOffset.UTC)
                        : null)
                .build();
    }

    private String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}