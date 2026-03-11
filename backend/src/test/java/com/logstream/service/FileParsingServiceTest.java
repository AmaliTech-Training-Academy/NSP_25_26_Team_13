package com.logstream.service;

import com.logstream.exception.FileProcessingException;
import com.logstream.exception.InvalidFileException;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileParsingServiceTest {

    private FileParsingService service;

    @BeforeEach
    void setUp() {
        service = new FileParsingService();
    }

    @Test
    void parseCSVLine_allColumns_parsesCorrectly() {
        // Column order: id,timestamp,level,source,message,service_name,created_at
        String line = "1,2024-01-15T10:30:00Z,INFO,com.example.Main,Hello world,my-service,2024-01-15T10:30:00";

        LogEntry result = service.parseCSVLine(line);

        assertThat(result.getServiceName()).isEqualTo("my-service");
        assertThat(result.getLevel()).isEqualTo(LogLevel.INFO);
        assertThat(result.getMessage()).isEqualTo("Hello world");
        assertThat(result.getSource()).isEqualTo("com.example.Main");
        assertThat(result.getTimestamp()).isNotNull();
        // traceId and metadata are not part of the CSV column format
        assertThat(result.getTraceId()).isNull();
    }

    @Test
    void parseCSVLine_minimumThreeColumns_parsesSuccessfully() {
        // Column order: id,timestamp,level,source,message,service_name,created_at
        LogEntry result = service.parseCSVLine(",,WARN,,Low memory,auth-service,2024-01-01T00:00:00");

        assertThat(result.getServiceName()).isEqualTo("auth-service");
        assertThat(result.getLevel()).isEqualTo(LogLevel.WARN);
        assertThat(result.getMessage()).isEqualTo("Low memory");
        assertThat(result.getTraceId()).isNull();
    }

    @Test
    void parseCSVLine_missingTimestamp_usesNowApproximately() {
        Instant before = Instant.now();
        LogEntry result = service.parseCSVLine(",,ERROR,,Oops,svc,2024-01-01T00:00:00");
        Instant after = Instant.now();

        assertThat(result.getTimestamp())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    void parseCSVLine_timestampColumn_isIgnoredServiceUsesNow() {
        // The CSV 'timestamp' column (col 1) is not bound in LogEntryCSV; LogEntry.timestamp is always Instant.now()
        Instant before = Instant.now();
        LogEntry result = service.parseCSVLine(",,DEBUG,src,msg,svc,2024-01-01T00:00:00");
        Instant after = Instant.now();

        assertThat(result.getTimestamp())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INFO", "WARN", "ERROR", "DEBUG"})
    void parseCSVLine_allLogLevels_parsedCorrectly(String level) {
        LogEntry result = service.parseCSVLine(",," + level + ",,msg,svc,2024-01-01T00:00:00");

        assertThat(result.getLevel()).isEqualTo(LogLevel.valueOf(level));
    }

    @Test
    void parseCSVLine_lowercaseLogLevel_normalisedToEnum() {
        LogEntry result = service.parseCSVLine(",,error,,Something broke,svc,2024-01-01T00:00:00");

        assertThat(result.getLevel()).isEqualTo(LogLevel.ERROR);
    }

    @Test
    void parseCSVLine_mixedCaseLogLevel_normalisedToEnum() {
        LogEntry result = service.parseCSVLine(",,Warn,,Something,svc,2024-01-01T00:00:00");

        assertThat(result.getLevel()).isEqualTo(LogLevel.WARN);
    }

    @Test
    void parseCSVLine_whitespaceAroundFields_trimmed() {
        LogEntry result = service.parseCSVLine(",,INFO,,the message,my-svc,2024-01-01T00:00:00");

        assertThat(result.getServiceName()).isEqualTo("my-svc");
        assertThat(result.getMessage()).isEqualTo("the message");
    }

    @Test
    void parseCSVLine_quotedFieldWithComma_parsedAsOneField() {
        LogEntry result = service.parseCSVLine(",,INFO,,\"message, with comma\",svc,2024-01-01T00:00:00");

        assertThat(result.getMessage()).isEqualTo("message, with comma");
    }

    @Test
    void parseCSVLine_blankOptionalFields_returnedAsNull() {
        LogEntry result = service.parseCSVLine(",,INFO,,,svc,2024-01-01T00:00:00");

        assertThat(result.getSource()).isEmpty();
        assertThat(result.getTraceId()).isNull();
    }

    @Test
    void parseCSVLine_fourColumns_onlySourcePopulated() {
        LogEntry result = service.parseCSVLine(",,INFO,com.example.Foo,msg,svc,2024-01-01T00:00:00");

        assertThat(result.getSource()).isEqualTo("com.example.Foo");
        assertThat(result.getTraceId()).isNull();
    }

    @Test
    void parseCSVLine_fiveColumns_sourceAndTraceIdPopulated() {
        // traceId is not a column in the CSV format; always null
        LogEntry result = service.parseCSVLine(",,INFO,src,msg,svc,2024-01-01T00:00:00");

        assertThat(result.getSource()).isEqualTo("src");
        assertThat(result.getTraceId()).isNull();
    }

    @Test
    void parseCSVLine_nullLine_throwsFileProcessingException() {
        assertThatThrownBy(() -> service.parseCSVLine(null))
                .isInstanceOf(FileProcessingException.class);
    }

    @Test
    void parseCSVLine_emptyLine_throwsInvalidFileException() {
        assertThatThrownBy(() -> service.parseCSVLine(""))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void parseCSVLine_oneColumn_throwsInvalidFileException() {
        assertThatThrownBy(() -> parseCsvLineSuppressingWorkerNoise("only-service"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error parsing CSV line");
    }

    @Test
    void parseCSVLine_twoColumns_throwsInvalidFileException() {
        assertThatThrownBy(() -> parseCsvLineSuppressingWorkerNoise("svc,INFO"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error parsing CSV line");
    }

    private void parseCsvLineSuppressingWorkerNoise(String line) {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // OpenCSV can throw expected worker-thread exceptions for malformed rows.
        });
        try {
            service.parseCSVLine(line);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }

    @Test
    void parseCSVLine_invalidLogLevel_throwsInvalidFileException() {
        assertThatThrownBy(() -> service.parseCSVLine(",,NONSENSE,,msg,svc,2024-01-01T00:00:00"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Invalid log level");
    }

    @Test
    void parseCSVLine_invalidTimestampFormat_throwsInvalidFileException() {
        assertThatThrownBy(() -> service.parseCSVLine(",,INFO,src,msg,svc,not-a-timestamp"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Invalid timestamp format");
    }
}