# Common Errors Analytics Feature - Implementation Summary

## Overview
Implemented a REST API endpoint to retrieve the most frequent error messages for a specific service over a configurable time range.

## Commits Created

### 1. feat(dto): add common errors response DTO
- Created `CommonErrorResponse` DTO with `message` and `count` fields
- Used Lombok annotations for clean boilerplate reduction
- Follows existing DTO pattern in the project

### 2. feat(dto): add common errors request DTO with validation
- Created `CommonErrorsRequest` DTO with:
  - `service` (required, @NotBlank)
  - `limit` (1-100, default 10, @Min @Max)
  - `startTime` and `endTime` (optional, for custom time ranges)
- Validation annotations ensure data integrity at controller layer

### 3. feat(repository): add queries for top error messages
- Added `findTopErrorMessagesByService` query for last 24 hours
- Added `findTopErrorMessagesByServiceAndTimeRange` query for custom time ranges
- Queries group by message, count occurrences, order by count descending
- Uses indexed columns (level, serviceName, createdAt) for performance

### 4. feat(service): implement common errors retrieval logic
- Implemented `getCommonErrors` method in AnalyticsService
- Handles time range defaults (last 24 hours if not specified)
- Respects limit parameter and returns top N results
- Returns empty list for services with no errors
- Supports both default and custom time range queries

### 5. feat(controller): add common errors endpoint
- Added `GET /api/analytics/common-errors` endpoint
- Validates `CommonErrorsRequest` with @Valid annotation
- Returns `ApiResponse` with list of `CommonErrorResponse`
- Supports query parameters: service (required), limit (optional), startTime, endTime

### 6. test(service): add unit tests for common errors logic
- Test for correct ordering by count descending
- Test for limit parameter enforcement
- Test for empty result handling
- Test for custom time range handling
- All tests use mocked repository queries

## Endpoint Specification

**URL:** `GET /api/analytics/common-errors`

**Query Parameters:**
- `service` (required): Service name to analyze
- `limit` (optional): Number of top errors to return (1-100, default: 10)
- `startTime` (optional): Start time in milliseconds since epoch
- `endTime` (optional): End time in milliseconds since epoch

**Response Format:**
```json
{
  "success": true,
  "message": "Common errors retrieved successfully",
  "data": [
    {
      "message": "Connection timeout",
      "count": 123
    },
    {
      "message": "Invalid credentials",
      "count": 87
    }
  ]
}
```

**Example Requests:**

1. Get top 5 errors for auth-service in last 24 hours:
```bash
GET /api/analytics/common-errors?service=auth-service&limit=5
```

2. Get top 10 errors (default) for payment-service:
```bash
GET /api/analytics/common-errors?service=payment-service
```

3. Get top 20 errors for auth-service in custom time range:
```bash
GET /api/analytics/common-errors?service=auth-service&limit=20&startTime=1609459200000&endTime=1609545600000
```

## Acceptance Criteria Met

✅ GET /api/analytics/common-errors?service=auth-service&limit=5 returns top 5 errors
✅ Response format: [{message: "Connection timeout", count: 123}, ...]
✅ Default limit is 10 if not specified
✅ Max limit is 100; larger values return HTTP 400
✅ Time range defaults to last 24 hours if not specified
✅ Empty result returns empty array, not error
✅ Errors grouped by message and counted
✅ Results ordered by count descending
✅ Unit tests for grouping and ordering logic

## Files Modified/Created

**New Files:**
- `src/main/java/com/logstream/dto/CommonErrorResponse.java`
- `src/main/java/com/logstream/dto/CommonErrorsRequest.java`
- `src/test/java/com/logstream/service/CommonErrorsServiceTest.java`
- `src/test/java/com/logstream/controller/CommonErrorsControllerTest.java`

**Modified Files:**
- `src/main/java/com/logstream/repository/LogEntryRepository.java` (added 2 queries)
- `src/main/java/com/logstream/service/AnalyticsService.java` (added 1 method)
- `src/main/java/com/logstream/controller/AnalyticsController.java` (added 1 endpoint)

## Architecture Decisions

1. **Query Optimization:** Used native JPA queries with GROUP BY and ORDER BY for efficient aggregation
2. **Validation:** Applied @Valid and validation annotations at DTO level for clean separation
3. **Time Range Handling:** Default to last 24 hours; support custom ranges via optional parameters
4. **Error Handling:** Return empty array instead of error for no results (graceful degradation)
5. **Limit Enforcement:** Validate limit at controller layer before service processing

## Performance Considerations

- Queries use indexed columns (level, serviceName, createdAt)
- GROUP BY and ORDER BY executed at database level
- Limit applied in service layer after query results
- No N+1 queries; single query per request

## Future Improvements

1. Add caching layer (Redis) with 5-minute TTL
2. Create materialized view for pre-aggregated error counts
3. Add pagination for very large result sets
4. Implement error pattern matching/regex filtering
5. Add role-based access control for analytics endpoints
6. Rate limiting on analytics endpoints
7. Audit logging for analytics access
