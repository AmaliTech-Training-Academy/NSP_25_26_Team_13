package com.logstream.service;

import com.logstream.exception.InvalidFileException;
import com.logstream.model.FileType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LogImportService {

    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
    private final FileProcessingService fileProcessingService;


    public void initiateImport(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file is empty or missing.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("File exceeds the maximum allowed size of 50MB.");
        }

        FileType fileType = detectFileType(file);

        try {
            byte[] fileBytes = file.getBytes();
            if (fileType.equals(FileType.CSV)) {
                fileProcessingService.processCSVFile(fileBytes);
            } else if (fileType.equals(FileType.JSON)) {
                fileProcessingService.processJSONFile(fileBytes);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private FileType detectFileType(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String contentType = file.getContentType();

        if (originalName != null) {
            String lower = originalName.toLowerCase();
            if (lower.endsWith(".csv")) return FileType.CSV;
            if (lower.endsWith(".json")) return FileType.JSON;
        }
        if (contentType != null) {
            if (contentType.contains("csv")) return FileType.CSV;
            if (contentType.contains("json")) return FileType.JSON;
        }
        throw new InvalidFileException(
                "Unsupported file type. Please upload a .csv or .json file");
    }
}