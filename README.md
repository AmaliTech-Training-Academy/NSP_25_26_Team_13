# LogStream - Log Aggregator

Spring Boot + PostgreSQL.

## Team Members (Parts)

| Member | Part | Components |
|--------|------|------------|
| Part A | Ingestion + BulkImport | LogController, IngestionService, FileProcessingService |
| Part B | Search + Retention | LogSearchController, SearchService, RetentionService |
| Part C | Auth + Analytics | AuthController, AnalyticsController, HealthController, UserController |

## Part C: Authentication, Analytics & User Management

### Features
- JWT authentication with HTTP-only cookies
- Real-time error rate calculation
- Service health monitoring (GREEN/YELLOW/RED/UNKNOWN)
- Log volume aggregation (hourly/daily)
- User management with role-based access control

### API Endpoints

**Authentication:**
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login

**Analytics:**
- `GET /api/analytics/error-rate` - Error rates per service
- `GET /api/analytics/common-errors` - Top error messages
- `GET /api/analytics/volume` - Log volume time series
- `GET /api/health/dashboard` - Service health status

**User Management (Admin only):**
- `GET /api/users` - List all users
- `POST /api/users` - Create user
- `PUT /api/users/{id}/role` - Update role
- `PUT /api/users/{id}/toggle-status` - Toggle status
- `DELETE /api/users/{id}` - Delete user

## Running

```bash
docker-compose up -d
cd backend
mvn spring-boot:run
```

## Documentation

- [API Documentation](API_DOCUMENTATION.md) - Detailed API reference
- [Usage Guide](USAGE.md) - Usage examples and curl commands

## Tests
- AnalyticsServiceTest: 9 tests ✅
- HealthServiceTest: 21 tests ✅
- AuthServiceTests: 172 tests ✅
