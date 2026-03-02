package com.logstream.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BulkImportService {

    // TODO (Dev A): Parse CSV rows into LogEntryRequests and call IngestionService
    public int importFromCsv(MultipartFile file) {
        throw new UnsupportedOperationException("CSV import not yet implemented");
    }

    // TODO (Dev A): Parse JSON array into LogEntryRequests and call IngestionService
    public int importFromJson(MultipartFile file) {
        throw new UnsupportedOperationException("JSON import not yet implemented");
    }
}
