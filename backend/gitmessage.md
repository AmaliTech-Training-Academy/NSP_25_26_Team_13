# Git Commit Messages

---

## User Story 1 — Error Rate per Service

```
feat(analytics): add error rate per service endpoint with bulk queries

- Wrote AnalyticsController.getErrorRate() returning ApiResponse<List<ErrorRateResponse>>
- Implemented getErrorRatePerService() using two bulk DB queries to avoid N+1
- Added buildErrorRate() and toMap() helpers to keep method size under 30 lines
- Error rate rounded to 2 decimal places via BigDecimal.HALF_UP
- Services with no logs in last 24h return errorRate 0.0 safely
- Fixed legacy getErrorRates() to delegate to getErrorRatePerService() (DRY)
```

---

## User Story 2 — Common Errors per Service

```
feat(analytics): add common errors endpoint with limit validation and typed responses

- Wrote getCommonErrors() with resolveLimit() guard (default 10, max 100)
- Time range defaults to last 24h when startTime/endTime are omitted
- Fixed getTopErrors() return type from List<Map<String,Object>> to List<CommonErrorResponse>
- Fixed AnalyticsResponse.topErrors field type to List<CommonErrorResponse>
- Fixed AnalyticsService.getAnalytics() local variable type to match
- Fixed AnalyticsControllerTest JSON paths from $[0].* to $.data[0].* after ApiResponse wrapping
```

---

## User Story 3 — Log Volume Time Series

```
feat(analytics): add log volume time series with hourly and daily aggregation

- Wrote getLogVolumeTimeSeries() using native SQL date_trunc via findHourlyVolume/findDailyVolume
- Implemented fillGaps() to fill missing time buckets with count 0
- Implemented truncate() to align start time to hour or day boundary
- Rejects invalid granularity values early with IllegalArgumentException
- Defaults time range to last 7 days when no range is provided
- Fixed 3 legacy AnalyticsServiceTest tests referencing deleted repository methods
```

---

## User Story 4 — Health Dashboard

```
feat(health): add health dashboard with per-service status and correct thresholds

- Wrote HealthDashboardService.getHealthDashboard() with UNKNOWN for no-log services
- Fixed getServiceHealth() thresholds: >5% RED, >=1% YELLOW, <1% GREEN (was >10%/>5%)
- Moved HealthDashboardResponse construction from controller into buildLegacyDashboard()
- Made resolveStatus() public to allow direct unit testing
- Fixed corrupted HealthDashboardService.java (orphaned if blocks, duplicate public modifier)
- Fixed HealthControllerTest JSON paths from $[0].* to $.data[0].* after ApiResponse wrapping
```

---

## User Story 5 — Swagger UI + HTML Dashboard Accessibility

```
feat(dashboard): fix ApiResponse unwrapping in dashboard.html and correct Swagger docs

- Fixed fetchDashboard() to unwrap ApiResponse: const services = json.data before rendering
- Updated HealthController @ApiResponse Swagger annotation to document ApiResponse wrapper shape
- Replaced bare @ArraySchema with @Schema(implementation = ApiResponse.class)
- Updated @ExampleObject to show full {"success":true,"data":[...],"message":"","timestamp":""} envelope
- Removed unused ArraySchema import from HealthController
- Verified SecurityConfig permits /dashboard.html, /swagger-ui/**, /v3/api-docs/** without auth
```

---

## User Story 6 — Unit Tests

```
test(analytics): add unit tests for error rate, common errors, volume, and health dashboard

- Added 42 tests in AnalyticsServiceTest covering error rate, rounding, boundary values,
  common error ordering, limit validation, gap-filling, hourly/daily bucketing, and edge cases
- Added 24 tests in HealthDashboardServiceTest covering resolveStatus() at every boundary,
  full dashboard assembly, UNKNOWN detection, all-GREEN/RED, and all four statuses simultaneously
- Fixed @MockBean import in AnalyticsControllerTest and HealthControllerTest:
  mock.bean -> mock.mockito (Spring Boot 3.2 correct package)
- Added <testExcludes> in pom.xml to exclude orphaned com/smart_ecomernce_api/** test sources
- Upgraded Lombok from 1.18.34 to 1.18.42 for JDK 25 compatibility
```

---

## Cross-cutting — ApiResponse Standardisation

```
refactor(response): standardise all controller responses to use ApiResponse<T> wrapper

- All 5 controllers (Analytics, Auth, Health, Log, Retention) return ResponseEntity<ApiResponse<T>>
- Fixed PaginatedResponse package from com.smart_ecomernce_api to com.logstream.common.response
- Fixed AuthController typo authSertvice -> authService
- Delete endpoints return ApiResponse<String> with "deleted" as data payload
- All controller tests updated to assert $.data.* instead of root-level JSON paths
```
