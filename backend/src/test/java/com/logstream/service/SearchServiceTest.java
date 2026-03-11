package com.logstream.service;

import com.logstream.dto.LogEntryResponse;
import com.logstream.dto.LogSearchRequest;
import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    @InjectMocks
    private SearchService searchService;

    private LogEntry sampleEntry() {
        return LogEntry.builder()
                .id(UUID.randomUUID())
                .serviceName("auth-service")
                .level(LogLevel.ERROR)
                .message("Something failed")
                .timestamp(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void searchLogs_noFilters_returnsAllResults() {
        Page<LogEntry> page = new PageImpl<>(List.of(sampleEntry()));
        when(logEntryRepository.findByTimestampBetween(any(Instant.class), any(Instant.class), any(PageRequest.class)))
                .thenReturn(page);

        LogSearchRequest request = new LogSearchRequest();
        Page<LogEntryResponse> result = searchService.searchLogs(request);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getServiceName()).isEqualTo("auth-service");
    }

    @Test
    void searchLogs_withServiceAndLevel_passesFiltersToRepository() {
        Page<LogEntry> page = new PageImpl<>(List.of(sampleEntry()));
        when(logEntryRepository.findByServiceNameAndLevelAndTimestampBetweenAndMessageContainingIgnoreCase(
                eq("auth-service"), eq(LogLevel.ERROR), any(Instant.class), any(Instant.class), anyString(), any(PageRequest.class)))
                .thenReturn(page);

        LogSearchRequest request = LogSearchRequest.builder()
                .serviceName("auth-service")
                .level(LogLevel.ERROR)
                .keyword("timeout")
                .size(20)
                .build();
        Page<LogEntryResponse> result = searchService.searchLogs(request);

        assertThat(result.getContent()).hasSize(1);
        verify(logEntryRepository).findByServiceNameAndLevelAndTimestampBetweenAndMessageContainingIgnoreCase(
                eq("auth-service"), eq(LogLevel.ERROR), any(Instant.class), any(Instant.class), eq("timeout"), any(PageRequest.class));
    }

    @Test
    void searchLogs_withTimestampRange_parsesIsoStrings() {
        String start = "2025-01-01T00:00:00Z";
        String end = "2025-12-31T23:59:59Z";
        Page<LogEntry> page = new PageImpl<>(List.of(sampleEntry()));
        when(logEntryRepository.findByTimestampBetween(eq(Instant.parse(start)), eq(Instant.parse(end)), any(PageRequest.class)))
                .thenReturn(page);

        LogSearchRequest request = LogSearchRequest.builder()
                .startTime(start)
                .endTime(end)
                .size(20)
                .build();
        Page<LogEntryResponse> result = searchService.searchLogs(request);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void searchLogs_withKeyword_passesKeywordToRepository() {
        Page<LogEntry> page = new PageImpl<>(List.of(sampleEntry()));
        when(logEntryRepository.findByTimestampBetweenAndMessageContainingIgnoreCase(
                any(Instant.class), any(Instant.class), eq("timeout"), any(PageRequest.class)))
                .thenReturn(page);

        LogSearchRequest request = LogSearchRequest.builder().keyword("timeout").size(20).build();
        searchService.searchLogs(request);

        verify(logEntryRepository).findByTimestampBetweenAndMessageContainingIgnoreCase(
                any(Instant.class), any(Instant.class), eq("timeout"), any(PageRequest.class));
    }

    @Test
    void searchLogs_paginationIsApplied() {
        when(logEntryRepository.findByTimestampBetween(any(Instant.class), any(Instant.class), any(PageRequest.class)))
                .thenReturn(Page.empty());

        LogSearchRequest request = LogSearchRequest.builder().page(2).size(10).build();
        searchService.searchLogs(request);

        verify(logEntryRepository).findByTimestampBetween(
                any(Instant.class),
                any(Instant.class),
                eq(PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "timestamp")))
        );
    }

    @Test
    void getLogById_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(logEntryRepository.findById(id)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> searchService.getLogById(id))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getLogById_found_returnsResponse() {
        LogEntry entry = sampleEntry();
        when(logEntryRepository.findById(entry.getId())).thenReturn(java.util.Optional.of(entry));

        LogEntryResponse response = searchService.getLogById(entry.getId());

        assertThat(response.getId()).isEqualTo(entry.getId());
        assertThat(response.getServiceName()).isEqualTo("auth-service");
    }
}
