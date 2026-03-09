1. TASK UNDERSTANDING
   What the task is:
   Implement an analytics endpoint that calculates and returns the error rate for each service over the last 24 hours.

Goal:
Provide visibility into service health by exposing error metrics through a REST API endpoint.

Key requirements:
Query logs filtered by ERROR level within the last 24 hours

Calculate error rate as (error_count / total_count) * 100 per service

Return structured data with service name, error rate, error count, and total count

Handle services with no logs (return 0% error rate)

Round error rates to 2 decimal places

Ensure performant queries using indexes

2. IMPLEMENTATION PLAN
   Files to create:

controllers/AnalyticsController.java - REST controller with GET endpoint

services/AnalyticsService.java - Business logic for error rate calculation

dto/ErrorRateResponse.java - Response DTO

controllers/AnalyticsControllerTest.java - Unit tests

Files to modify:

Repository interface (if custom query needed)

Main components:

AnalyticsController - Exposes /api/analytics/error-rate endpoint

AnalyticsService - Queries logs, groups by service, calculates error rates

ErrorRateResponse DTO - Structures the response data

Logic decisions:

Use repository to query logs with timestamp and level filters

Group results by service name

Calculate error rate with proper null/zero handling

Round to 2 decimal places using BigDecimal or String.format

Dependencies:
Existing Log entity and repository

Spring Boot Web, JPA

3. CODE IMPLEMENTATION
   1 file read
   system.md
   2 directories listed
   backend
   com
   5 files read
   LogEntry.java
   LogEntryRepository.java
   AnalyticsService.java
   LogLevel.java
   AnalyticsResponse.java
   Now I'll implement the error rate analytics feature following the system.md guidelines and the universal prompt template structure.

File: src/main/java/com/logstream/dto/ErrorRateResponse.java

package com.logstream.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorRateResponse {
private String service;
private Double errorRate;
private Long errorCount;
private Long totalCount;
}

Copy
File: src/main/java/com/logstream/repository/LogEntryRepository.java

package com.logstream.repository;

import com.logstream.model.LogEntry;
import com.logstream.model.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, UUID> {

    Page<LogEntry> findByServiceName(String serviceName, Pageable pageable);

    Page<LogEntry> findByLevel(LogLevel level, Pageable pageable);

    Page<LogEntry> findByServiceNameAndLevel(String serviceName, LogLevel level, Pageable pageable);

    Page<LogEntry> findByTimestampBetween(Instant start, Instant end, Pageable pageable);

    Page<LogEntry> findByMessageContainingIgnoreCase(String keyword, Pageable pageable);

    long countByServiceNameAndLevel(String serviceName, LogLevel level);

    long countByServiceName(String serviceName);

    @Query("SELECT l.serviceName, l.level, COUNT(l) FROM LogEntry l GROUP BY l.serviceName, l.level")
    List<Object[]> countGroupByServiceAndLevel();

    @Query("SELECT l.serviceName, COUNT(l) FROM LogEntry l WHERE l.createdAt >= :since GROUP BY l.serviceName")
    List<Object[]> countByServiceSince(Instant since);

    @Query("SELECT l.serviceName, COUNT(l) FROM LogEntry l WHERE l.level = :level AND l.createdAt >= :since GROUP BY l.serviceName")
    List<Object[]> countByLevelAndServiceSince(LogLevel level, Instant since);
}


Copy
File: src/main/java/com/logstream/service/AnalyticsService.java

package com.logstream.service;

import com.logstream.dto.AnalyticsResponse;
import com.logstream.dto.ErrorRateResponse;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final LogEntryRepository logEntryRepository;

    public List<ErrorRateResponse> getErrorRateByService() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        
        Map<String, Long> totalCountsByService = buildServiceCountMap(
            logEntryRepository.countByServiceSince(since)
        );
        
        Map<String, Long> errorCountsByService = buildServiceCountMap(
            logEntryRepository.countByLevelAndServiceSince(LogLevel.ERROR, since)
        );
        
        return totalCountsByService.entrySet().stream()
            .map(entry -> buildErrorRateResponse(
                entry.getKey(),
                errorCountsByService.getOrDefault(entry.getKey(), 0L),
                entry.getValue()
            ))
            .collect(Collectors.toList());
    }

    private Map<String, Long> buildServiceCountMap(List<Object[]> results) {
        return results.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> (Long) row[1]
            ));
    }

    private ErrorRateResponse buildErrorRateResponse(String service, Long errorCount, Long totalCount) {
        double errorRate = calculateErrorRate(errorCount, totalCount);
        
        return ErrorRateResponse.builder()
            .service(service)
            .errorRate(errorRate)
            .errorCount(errorCount)
            .totalCount(totalCount)
            .build();
    }

    private double calculateErrorRate(Long errorCount, Long totalCount) {
        if (totalCount == 0) {
            return 0.0;
        }
        double rate = (errorCount * 100.0) / totalCount;
        return Math.round(rate * 100.0) / 100.0;
    }

    // TODO (Dev C): Group logs by hour, count
    public Map<String, Long> getLogVolume(int hours) {
        throw new UnsupportedOperationException("Log volume not yet implemented");
    }

    // TODO (Dev C): Group ERROR messages, count, order desc
    public List<Map<String, Object>> getTopErrors(int limit) {
        throw new UnsupportedOperationException("Top errors not yet implemented");
    }

    // TODO (Dev C): error rate > 10% UNHEALTHY, > 5% DEGRADED, else HEALTHY
    public Map<String, String> getServiceHealth() {
        throw new UnsupportedOperationException("Service health not yet implemented");
    }
}


Copy
java
File: src/main/java/com/logstream/controller/AnalyticsController.java

package com.logstream.controller;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/error-rate")
    public ResponseEntity<List<ErrorRateResponse>> getErrorRate() {
        List<ErrorRateResponse> errorRates = analyticsService.getErrorRateByService();
        return ResponseEntity.ok(errorRates);
    }
}

Copy
java
File: src/test/java/com/logstream/controller/AnalyticsControllerTest.java

package com.logstream.controller;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    @WithMockUser
    void getErrorRate_shouldReturnErrorRates() throws Exception {
        List<ErrorRateResponse> mockResponse = Arrays.asList(
            ErrorRateResponse.builder()
                .service("auth-service")
                .errorRate(5.2)
                .errorCount(52L)
                .totalCount(1000L)
                .build(),
            ErrorRateResponse.builder()
                .service("payment-service")
                .errorRate(0.0)
                .errorCount(0L)
                .totalCount(500L)
                .build()
        );

        when(analyticsService.getErrorRateByService()).thenReturn(mockResponse);

        mockMvc.perform(get("/api/analytics/error-rate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].service").value("auth-service"))
            .andExpect(jsonPath("$[0].errorRate").value(5.2))
            .andExpect(jsonPath("$[0].errorCount").value(52))
            .andExpect(jsonPath("$[0].totalCount").value(1000))
            .andExpect(jsonPath("$[1].service").value("payment-service"))
            .andExpect(jsonPath("$[1].errorRate").value(0.0));
    }
}


Copy
java
File: src/test/java/com/logstream/service/AnalyticsServiceTest.java

package com.logstream.service;

import com.logstream.dto.ErrorRateResponse;
import com.logstream.model.LogLevel;
import com.logstream.repository.LogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        List<Object[]> totalCounts = Arrays.asList(
            new Object[]{"auth-service", 1000L},
            new Object[]{"payment-service", 500L}
        );

        List<Object[]> errorCounts = Arrays.asList(
            new Object[]{"auth-service", 52L}
        );

        when(logEntryRepository.countByServiceSince(any(Instant.class)))
            .thenReturn(totalCounts);
        when(logEntryRepository.countByLevelAndServiceSince(eq(LogLevel.ERROR), any(Instant.class)))
            .thenReturn(errorCounts);
    }

    @Test
    void getErrorRateByService_shouldCalculateCorrectRates() {
        List<ErrorRateResponse> result = analyticsService.getErrorRateByService();

        assertThat(result).hasSize(2);
        
        ErrorRateResponse authService = result.stream()
            .filter(r -> r.getService().equals("auth-service"))
            .findFirst()
            .orElseThrow();
        
        assertThat(authService.getErrorRate()).isEqualTo(5.2);
        assertThat(authService.getErrorCount()).isEqualTo(52L);
        assertThat(authService.getTotalCount()).isEqualTo(1000L);
    }

    @Test
    void getErrorRateByService_shouldReturnZeroForNoErrors() {
        List<ErrorRateResponse> result = analyticsService.getErrorRateByService();

        ErrorRateResponse paymentService = result.stream()
            .filter(r -> r.getService().equals("payment-service"))
            .findFirst()
            .orElseThrow();
        
        assertThat(paymentService.getErrorRate()).isEqualTo(0.0);
        assertThat(paymentService.getErrorCount()).isEqualTo(0L);
        assertThat(paymentService.getTotalCount()).isEqualTo(500L);
    }

    @Test
    void getErrorRateByService_shouldRoundToTwoDecimals() {
        List<Object[]> totalCounts = Arrays.asList(
            new Object[]{"test-service", 333L}
        );
        List<Object[]> errorCounts = Arrays.asList(
            new Object[]{"test-service", 17L}
        );

        when(logEntryRepository.countByServiceSince(any(Instant.class)))
            .thenReturn(totalCounts);
        when(logEntryRepository.countByLevelAndServiceSince(eq(LogLevel.ERROR), any(Instant.class)))
            .thenReturn(errorCounts);

        List<ErrorRateResponse> result = analyticsService.getErrorRateByService();

        assertThat(result.get(0).getErrorRate()).isEqualTo(5.11);
    }
}


Copy
java
Now let me create these files:

Creating ErrorRateResponse DTO to structure the error rate analytics response

ErrorRateResponse.java
+14
-0

Undo
Adding custom query methods to LogEntryRepository for efficient error rate calculation with indexed queries

LogEntryRepository.java
+6
-0

Undo
Implementing getErrorRateByService method in AnalyticsService with modular helper methods for clean separation of concerns

AnalyticsService.java
+50
-3

Undo
Creating AnalyticsController to expose the error rate endpoint

AnalyticsController.java
+22
-0

Undo
Creating unit tests for AnalyticsController with mocked service layer


Undo all changes
AnalyticsControllerTest.java
Error
Creating the test directory structure for controller and service tests

shell

Completed
mkdir com && mkdir com\logstream && mkdir com\logstream\controller && mkdir com\logstream\service
Creating unit tests for AnalyticsController with mocked service layer

AnalyticsControllerTest.java
+56
-0

Undo
Creating unit tests for AnalyticsService with mocked repository queries

AnalyticsServiceTest.java
+95
-0

Undo

Undo all changes
4. GIT COMMITS
   Commit 1:

feat(analytics): add error rate DTO

Copy
Description:

Created ErrorRateResponse DTO with service, errorRate, errorCount, and totalCount fields

Used Lombok annotations for clean boilerplate reduction

Follows existing DTO pattern in the project

Commit 2:

feat(repository): add queries for error rate calculation

Copy
Description:

Added countByServiceSince query to get total log counts per service in last 24 hours

Added countByLevelAndServiceSince query to get error counts per service

Queries use indexed columns (createdAt, level) for performance

Leverages existing indexes on level and timestamp

Commit 3:

feat(analytics): implement error rate calculation service

Copy
Description:

Implemented getErrorRateByService method in AnalyticsService

Queries logs from last 24 hours using repository methods

Calculates error rate as (errorCount / totalCount) * 100

Returns 0% for services with no errors

Rounds error rates to 2 decimal places

Modular design with helper methods: buildServiceCountMap, buildErrorRateResponse, calculateErrorRate

Commit 4:

feat(controller): add error rate analytics endpoint

Copy
Description:

Created AnalyticsController with GET /api/analytics/error-rate endpoint

Returns list of ErrorRateResponse objects

Follows Controller → Service architecture pattern

Minimal controller logic, delegates to service layer

Commit 5:

test(analytics): add unit tests for error rate feature

Copy
Description:

Added AnalyticsControllerTest with MockMvc for endpoint testing

Added AnalyticsServiceTest with mocked repository queries

Tests cover: correct rate calculation, zero error handling, decimal rounding

All tests use mocked dependencies for isolation

5. PULL REQUEST SUMMARY
   Title:
   feat: Implement error rate analytics endpoint for service monitoring

Overview:
This PR implements the error rate analytics feature that allows users to retrieve error rates for each service over the last 24 hours. The endpoint provides visibility into service health by calculating the percentage of ERROR-level logs relative to total logs per service.

Changes Made:

Feature additions:

New GET /api/analytics/error-rate endpoint

Error rate calculation logic with 24-hour time window

ErrorRateResponse DTO for structured response

Repository queries optimized with indexed columns

Architecture:

AnalyticsController handles HTTP requests

AnalyticsService contains business logic

Custom repository queries for efficient data aggregation

Modular helper methods for maintainability

Testing:

Unit tests for controller with MockMvc

Unit tests for service with mocked repository

Test coverage for edge cases (zero errors, decimal rounding)

Files Affected:

dto/ErrorRateResponse.java (new)

controller/AnalyticsController.java (new)

service/AnalyticsService.java (modified)

repository/LogEntryRepository.java (modified)

controller/AnalyticsControllerTest.java (new)

service/AnalyticsServiceTest.java (new)

Testing Instructions:

Start the application

Ensure database has log entries with various services and error levels

Call the endpoint:

GET /api/analytics/error-rate

Copy
bash
Verify response format:

[
{
"service": "auth-service",
"errorRate": 5.2,
"errorCount": 52,
"totalCount": 1000
}
]

Copy
json
Verify services with no errors return errorRate: 0.0

Verify error rates are rounded to 2 decimal places

Run unit tests: mvn test

6. POTENTIAL IMPROVEMENTS
   Performance optimizations:

Add caching layer (Redis) for error rate results with 5-minute TTL

Create materialized view for pre-aggregated error counts

Add pagination if service count grows large

Better architecture:

Extract calculation logic into separate ErrorRateCalculator class

Use Strategy pattern for different time window calculations (1h, 24h, 7d)

Implement async processing for large datasets

Security enhancements:

Add role-based access control (only admins can view analytics)

Rate limiting on analytics endpoints to prevent abuse

Audit logging for analytics access

Additional testing:

Integration tests with real database

Performance tests with large datasets

Contract tests for API response schema

Scalability improvements:

Implement time-series database (InfluxDB/TimescaleDB) for analytics

Add batch processing for historical error rate trends

Support filtering by date range, service name, or threshold


PULL REQUEST SUMMARY
Title:
feat: Add common errors analytics endpoint for service error analysis

Overview:
This PR implements the common errors analytics feature that allows users to retrieve the most frequent error messages for a specific service. The endpoint groups ERROR-level logs by message, counts occurrences, and returns the top N results ordered by frequency.

Changes Made:

Feature additions:

New GET /api/analytics/common-errors endpoint

CommonErrorResponse and CommonErrorsRequest DTOs

Service method to retrieve and rank error messages

Repository queries optimized with indexes

Validation:

Limit parameter: 1-100 (default 10)

Service name required

Optional time range parameters (startTime, endTime)

Returns HTTP 400 for invalid limit values

Testing:

Unit tests for service grouping and ordering logic

Unit tests for controller endpoint validation

Tests cover edge cases (empty results, limit enforcement)

Files Affected:

dto/CommonErrorResponse.java (new)

dto/CommonErrorsRequest.java (new)

service/AnalyticsService.java (modified)

repository/LogEntryRepository.java (modified)

controller/AnalyticsController.java (modified)

service/AnalyticsServiceTest.java (modified)

controller/AnalyticsControllerTest.java (modified)

Testing Instructions:

Start the application

Call the endpoint with required parameters:

GET /api/analytics/common-errors?service=auth-service&limit=5

Copy
bash
Verify response format:

{
"success": true,
"message": "Common errors retrieved successfully",
"data": [
{"message": "Connection timeout", "count": 123},
{"message": "Invalid credentials", "count": 87}
]
}

Copy
json
Test with custom time range:

GET /api/analytics/common-errors?service=auth-service&startTime=1609459200000&endTime=1609545600000

Copy
bash
Test validation:

GET /api/analytics/common-errors?service=auth-service&limit=101  # Should return 400

Copy
bash
Run unit tests: mvn test -Dtest=AnalyticsServiceTest,AnalyticsControllerTest












