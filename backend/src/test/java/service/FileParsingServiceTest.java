package service;

import com.logstream.exception.FileProcessingException;
import com.logstream.exception.InvalidFileException;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.service.FileParsingService;
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
        String timestamp = "2024-01-15T10:30:00Z";
        String line = "my-service,INFO,Hello world,com.example.Main,trace-abc,{\"key\":\"val\"}," + timestamp;

        LogEntry result = service.parseCSVLine(line);

        assertThat(result.getServiceName()).isEqualTo("my-service");
        assertThat(result.getLevel()).isEqualTo(LogLevel.INFO);
        assertThat(result.getMessage()).isEqualTo("Hello world");
        assertThat(result.getSource()).isEqualTo("com.example.Main");
        assertThat(result.getTraceId()).isEqualTo("trace-abc");
        assertThat(result.getMetadata()).isEqualTo("{\"key\":\"val\"}");
        assertThat(result.getTimestamp()).isEqualTo(Instant.parse(timestamp));
    }

    @Test
    void parseCSVLine_minimumThreeColumns_parsesSuccessfully() {
        LogEntry result = service.parseCSVLine("auth-service,WARN,Low memory");

        assertThat(result.getServiceName()).isEqualTo("auth-service");
        assertThat(result.getLevel()).isEqualTo(LogLevel.WARN);
        assertThat(result.getMessage()).isEqualTo("Low memory");
        assertThat(result.getSource()).isNull();
        assertThat(result.getTraceId()).isNull();
        assertThat(result.getMetadata()).isNull();
    }

    @Test
    void parseCSVLine_missingTimestamp_usesNowApproximately() {
        Instant before = Instant.now();
        LogEntry result = service.parseCSVLine("svc,ERROR,Oops");
        Instant after = Instant.now();

        assertThat(result.getTimestamp())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    void parseCSVLine_blankTimestamp_usesNow() {
        Instant before = Instant.now();
        LogEntry result = service.parseCSVLine("svc,DEBUG,msg,src,tid,meta,   ");
        Instant after = Instant.now();

        assertThat(result.getTimestamp())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INFO", "WARN", "ERROR", "DEBUG"})
    void parseCSVLine_allLogLevels_parsedCorrectly(String level) {
        LogEntry result = service.parseCSVLine("svc," + level + ",msg");

        assertThat(result.getLevel()).isEqualTo(LogLevel.valueOf(level));
    }

    @Test
    void parseCSVLine_lowercaseLogLevel_normalisedToEnum() {
        LogEntry result = service.parseCSVLine("svc,error,Something broke");

        assertThat(result.getLevel()).isEqualTo(LogLevel.ERROR);
    }

    @Test
    void parseCSVLine_mixedCaseLogLevel_normalisedToEnum() {
        LogEntry result = service.parseCSVLine("svc,Warn,Something");

        assertThat(result.getLevel()).isEqualTo(LogLevel.WARN);
    }

    @Test
    void parseCSVLine_whitespaceAroundFields_trimmed() {
        LogEntry result = service.parseCSVLine("  my-svc  ,  INFO  ,  the message  ");

        assertThat(result.getServiceName()).isEqualTo("my-svc");
        assertThat(result.getMessage()).isEqualTo("the message");
    }

    @Test
    void parseCSVLine_quotedFieldWithComma_parsedAsOneField() {
        LogEntry result = service.parseCSVLine("svc,INFO,\"message, with comma\"");

        assertThat(result.getMessage()).isEqualTo("message, with comma");
    }

    @Test
    void parseCSVLine_blankOptionalFields_returnedAsNull() {
        LogEntry result = service.parseCSVLine("svc,INFO,msg,  ,  ,  ");

        assertThat(result.getSource()).isNull();
        assertThat(result.getTraceId()).isNull();
        assertThat(result.getMetadata()).isNull();
    }

    @Test
    void parseCSVLine_fourColumns_onlySourcePopulated() {
        LogEntry result = service.parseCSVLine("svc,INFO,msg,com.example.Foo");

        assertThat(result.getSource()).isEqualTo("com.example.Foo");
        assertThat(result.getTraceId()).isNull();
        assertThat(result.getMetadata()).isNull();
    }

    @Test
    void parseCSVLine_fiveColumns_sourceAndTraceIdPopulated() {
        LogEntry result = service.parseCSVLine("svc,INFO,msg,src,my-trace-id");

        assertThat(result.getSource()).isEqualTo("src");
        assertThat(result.getTraceId()).isEqualTo("my-trace-id");
        assertThat(result.getMetadata()).isNull();
    }

    @Test
    void parseCSVLine_nullLine_throwsFileProcessingException() {
        assertThatThrownBy(() -> service.parseCSVLine(null))
                .isInstanceOf(FileProcessingException.class);
    }

    @Test
    void parseCSVLine_emptyLine_throwsInvalidFileException() {
        assertThatThrownBy(() -> service.parseCSVLine(""))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("insufficient columns");
    }

    @Test
    void parseCSVLine_oneColumn_throwsInvalidFileException() {
        assertThatThrownBy(() -> service.parseCSVLine("only-service"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("insufficient columns");
    }

    @Test
    void parseCSVLine_twoColumns_throwsInvalidFileException() {
        assertThatThrownBy(() -> service.parseCSVLine("svc,INFO"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("insufficient columns");
    }

    @Test
    void parseCSVLine_invalidLogLevel_throwsInvalidFileException() {
        assertThatThrownBy(() -> service.parseCSVLine("svc,NONSENSE,msg"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Invalid log level");
    }

    @Test
    void parseCSVLine_invalidTimestampFormat_throwsInvalidFileException() {
        assertThatThrownBy(() -> service.parseCSVLine("svc,INFO,msg,src,tid,meta,not-a-timestamp"))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Invalid timestamp format");
    }
}