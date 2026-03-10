package service;

import com.logstream.exception.InvalidFileException;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import com.logstream.service.FileProcessingService;
import com.logstream.service.LogImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogImportServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    @Mock
    private FileProcessingService fileProcessingService;

    @InjectMocks
    private LogImportService logImportService;

    @Test
    void initiateImport_nullFile_throwsInvalidFileException() {
        assertThatThrownBy(() -> logImportService.initiateImport(null))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("empty or missing");
    }

    @Test
    void initiateImport_emptyFile_throwsInvalidFileException() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> logImportService.initiateImport(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("empty or missing");
    }

    @Test
    void initiateImport_fileTooLarge_throwsInvalidFileException() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(51L * 1024 * 1024); // 51 MB

        assertThatThrownBy(() -> logImportService.initiateImport(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("50MB");
    }

    @Test
    void initiateImport_unsupportedFileType_throwsInvalidFileException() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("data.xml");
        when(file.getContentType()).thenReturn("application/xml");

        assertThatThrownBy(() -> logImportService.initiateImport(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void initiateImport_csvByExtension_delegatesToProcessCSV() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("logs.csv");
        when(file.getBytes()).thenReturn(new byte[0]);

        logImportService.initiateImport(file);

        verify(fileProcessingService).processCSVFile(any(byte[].class));
    }

    @Test
    void initiateImport_jsonByExtension_delegatesToProcessJSON() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("logs.json");
        when(file.getBytes()).thenReturn(new byte[0]);

        logImportService.initiateImport(file);

        verify(fileProcessingService).processJSONFile(any(byte[].class));
    }

    @Test
    void initiateImport_csvByContentType_whenNoExtension() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(512L);
        when(file.getOriginalFilename()).thenReturn("logfile");
        when(file.getContentType()).thenReturn("text/csv");
        when(file.getBytes()).thenReturn(new byte[0]);

        logImportService.initiateImport(file);

        verify(fileProcessingService).processCSVFile(any(byte[].class));
    }

    private LogEntry mockLogEntry() {
        return LogEntry.builder()
                .serviceName("svc")
                .level(LogLevel.INFO)
                .message("msg")
                .timestamp(Instant.now())
                .build();
    }
}