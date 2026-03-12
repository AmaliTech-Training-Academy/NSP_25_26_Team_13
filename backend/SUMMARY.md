# LogStream - Project Summary

## Overview
LogStream is a comprehensive log aggregation and analytics platform built with Spring Boot 3.2 and PostgreSQL. It provides real-time log ingestion, powerful search capabilities, retention policies, analytics, and role-based access control.

## Architecture

### Technology Stack
- **Backend**: Spring Boot 3.2 (Java 21)
- **Database**: PostgreSQL
- **Frontend**: Thymeleaf + Tailwind CSS
- **Authentication**: JWT with HTTP-only cookies
- **API Documentation**: OpenAPI/Swagger

### Core Modules

#### 1. Log Ingestion
- Single log entry ingestion via REST API
- Batch log ingestion for bulk operations
- CSV file import capability
- Support for multiple log levels (ERROR, WARN, INFO, DEBUG)

#### 2. Log Search
- Full-text search across log messages
- Filter by service, log level, time range
- Paginated results with configurable page size
- Native PostgreSQL full-text search

#### 3. Retention Policies
- Configurable retention periods per service
- Archive toggle for each policy
- Automatic log cleanup based on policies

#### 4. Analytics & Health Monitoring
- Real-time error rate calculation per service
- Common error message grouping
- Log volume aggregation (hourly/daily)
- Service health status dashboard (GREEN/YELLOW/RED/UNKNOWN)

#### 5. Authentication & Authorization
- JWT-based authentication
- HTTP-only cookie storage for web
- Role-based access control (ADMIN/USER)
- Secure password hashing with BCrypt

## Features

| Feature | Description |
|---------|-------------|
| Log Ingestion | REST API for single/batch log entry |
| Log Search | Full-text search with filters |
| CSV Import | Bulk import from CSV files |
| Retention | Per-service retention policies |
| Analytics | Error rates, common errors, volume charts |
| Health Dashboard | Service status monitoring |
| User Management | Admin panel for user CRUD |
| Role-Based Access | ADMIN/USER roles |
| API Documentation | Swagger/OpenAPI |

## Project Structure

```
backend/
├── src/main/java/com/logstream/
│   ├── config/          # Configuration classes
│   ├── controller/      # REST controllers
│   ├── service/         # Business logic
│   ├── repository/      # Data access
│   ├── model/           # Entity classes
│   ├── dto/             # Data transfer objects
│   └── exception/       # Exception handlers
├── src/main/resources/
│   ├── templates/       # Thymeleaf HTML templates
│   └── application.yml  # Application config
└── pom.xml              # Maven dependencies
```

## Database Schema

### Core Tables
- **users** - User accounts with roles
- **log_entries** - Log data with service, level, message
- **retention_policies** - Service retention configurations

## Security

- JWT tokens with 24-hour expiration
- HTTP-only secure cookies for web authentication
- Role-based endpoint protection
- BCrypt password hashing
- CORS configuration

## Performance

- Database indexing on frequently queried columns
- Async log processing
- Batch operations for bulk data
- Caching for user data
- Pagination for large result sets
