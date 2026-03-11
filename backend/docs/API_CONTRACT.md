# API Contract Documentation

## Base Information
- Base path: `/api`
- Content type for JSON endpoints: `application/json`
- Response wrapper used by most endpoints:
  - `success` (boolean)
  - `message` (string)
  - `data` (object | array | null)
  - `path` (string, Optional)
  - `statusCode` (number, Optional)
  - `timestamp` (string, ISO-like datetime)

## Authentication and Headers
- Current backend security configuration permits all `/api/**` endpoints.
- `Authorization: Bearer <token>` is not required by current config, but may still be used by clients for future compatibility.
- Use `Content-Type: application/json` for JSON request bodies.
- Use `Content-Type: multipart/form-data` for file upload endpoint.

---

## 1) Register User

**HTTP Method:** `POST`  
**Endpoint Path:** `/api/auth/register`  
**Description:** Registers a new user account and returns a JWT token with user details.

**Request Headers**
- `Content-Type: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| fullName | string | Yes | Full name of the user. | `"Jane Doe"` |
| email | string | Yes | Valid email address. | `"jane@example.com"` |
| password | string | Yes | Minimum 8 chars, must include letters and numbers. | `"Passw0rd1"` |

**Response Body**

Status Codes
- `200 OK`: User registered successfully.
- `400 Bad Request`: Validation error.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| token | string | JWT access token. |
| email | string | Registered user email. |
| role | string | Assigned role (for example `ROLE_USER`). |

Example JSON response
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqYW5lQGV4YW1wbGUuY29tIn0.signature",
  "email": "jane@example.com",
  "role": "ROLE_USER"
}
```

---

## 2) Login User

**HTTP Method:** `POST`  
**Endpoint Path:** `/api/auth/login`  
**Description:** Authenticates a user and returns a JWT token with user details.

**Request Headers**
- `Content-Type: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| email | string | Yes | User email. | `"jane@example.com"` |
| password | string | Yes | User password. | `"Passw0rd1"` |

**Response Body**

Status Codes
- `200 OK`: Login successful.
- `400 Bad Request`: Validation error.
- `401 Unauthorized`: Invalid credentials.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| token | string | JWT access token. |
| email | string | Authenticated user email. |
| role | string | User role. |

Example JSON response
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqYW5lQGV4YW1wbGUuY29tIn0.signature",
  "email": "jane@example.com",
  "role": "ROLE_ADMIN"
}
```

---

## 3) Ingest Single Log

**HTTP Method:** `POST`  
**Endpoint Path:** `/api/logs`  
**Description:** Ingests one log entry.

**Request Headers**
- `Content-Type: application/json`
- `Authorization: Bearer <token>` (Optional)

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| serviceName | string | Yes | Service identifier (`[a-zA-Z0-9-_]+`). | `"auth-service"` |
| level | string | Yes | Log level (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`). | `"ERROR"` |
| message | string | Yes | Log message (1 to 2000 chars). | `"JWT validation failed"` |
| metadata | object (string map) | No (**Optional**) | Additional key-value context. | `{"ip":"10.0.0.1","env":"prod"}` |
| source | string | No (**Optional**) | Source component name. | `"gateway"` |
| traceId | string | No (**Optional**) | Request trace identifier. | `"trace-12345"` |
| createdAt | string | No (**Optional**) | Client-provided creation timestamp. | `"2026-03-11T09:30:00Z"` |

**Response Body**

Status Codes
- `201 Created`: Log ingested.
- `400 Bad Request`: Validation error.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data.id | string (UUID) | Log identifier. |
| data.serviceName | string | Service name. |
| data.timestamp | string (date-time) | Event timestamp. |
| data.level | string | Log level. |
| data.message | string | Log message. |
| data.metadata | string | Serialized metadata. |
| data.source | string | Source component. |
| data.traceId | string | Trace ID. |
| data.createdAt | string (date-time) | Persistence timestamp. |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Log ingested successfully",
  "data": {
    "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
    "serviceName": "auth-service",
    "timestamp": "2026-03-11T09:30:00Z",
    "level": "ERROR",
    "message": "JWT validation failed",
    "metadata": "{\"ip\":\"10.0.0.1\",\"env\":\"prod\"}",
    "source": "gateway",
    "traceId": "trace-12345",
    "createdAt": "2026-03-11T09:30:01Z"
  },
  "timestamp": "2026-03-11T09:30:01.123"
}
```

---

## 4) Get Logs (Paginated)

**HTTP Method:** `GET`  
**Endpoint Path:** `/api/logs`  
**Description:** Retrieves paginated log entries.

**Request Headers**
- `Accept: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| page | integer (query) | No (**Optional**) | Page index, default `0`. | `0` |
| size | integer (query) | No (**Optional**) | Page size, default `20`. | `20` |

**Response Body**

Status Codes
- `200 OK`: Logs retrieved.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data.content | array | Page content of `LogEntryResponse`. |
| data.number | integer | Current page index. |
| data.size | integer | Page size. |
| data.totalElements | integer | Total number of elements. |
| data.totalPages | integer | Total pages available. |
| data.first | boolean | Whether current page is first. |
| data.last | boolean | Whether current page is last. |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Logs retrieved successfully",
  "data": {
    "content": [
      {
        "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
        "serviceName": "auth-service",
        "timestamp": "2026-03-11T09:30:00Z",
        "level": "INFO",
        "message": "Login successful",
        "metadata": "{\"ip\":\"10.0.0.1\"}",
        "source": "auth-api",
        "traceId": "trace-001",
        "createdAt": "2026-03-11T09:30:01Z"
      }
    ],
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "timestamp": "2026-03-11T09:40:12.005"
}
```

---

## 5) Ingest Batch Logs

**HTTP Method:** `POST`  
**Endpoint Path:** `/api/logs/batch`  
**Description:** Ingests multiple log entries in one request.

**Request Headers**
- `Content-Type: application/json`
- `Authorization: Bearer <token>` (Optional)

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| logs | array of `LogEntryRequest` | Yes | Non-empty list of log entries. | `[{"serviceName":"auth-service","level":"ERROR","message":"Failure"}]` |

**Response Body**

Status Codes
- `201 Created`: Batch processed.
- `400 Bad Request`: Validation error.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data.count | integer | Number of ingested log records. |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Batch log ingestion completed",
  "data": {
    "count": 25
  },
  "timestamp": "2026-03-11T09:45:30.800"
}
```

---

## 6) Import Logs From File

**HTTP Method:** `POST`  
**Endpoint Path:** `/api/logs/import`  
**Description:** Uploads a file and initiates log import.

**Request Headers**
- `Content-Type: multipart/form-data`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| file | file (multipart) | Yes | Upload file containing logs (for example CSV). | `"logs.csv"` |

**Response Body**

Status Codes
- `200 OK`: Import accepted and completed by service call.
- `400 Bad Request`: Invalid file input.
- `500 Internal Server Error`: File processing failure.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| message | string | Operation result message. |

Example JSON response
```json
{
  "message": "Log import successful"
}
```

---

## 7) Search Logs

**HTTP Method:** `POST`  
**Endpoint Path:** `/api/logs/search`  
**Description:** Searches logs by filters and returns paginated results.

**Request Headers**
- `Content-Type: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| serviceName | string | No (**Optional**) | Filter by service name. | `"auth-service"` |
| level | string | No (**Optional**) | Filter by level (`TRACE`,`DEBUG`,`INFO`,`WARN`,`ERROR`). | `"ERROR"` |
| startTime | string | No (**Optional**) | Start timestamp filter. | `"2026-03-10T00:00:00Z"` |
| endTime | string | No (**Optional**) | End timestamp filter. | `"2026-03-11T00:00:00Z"` |
| keyword | string | No (**Optional**) | Keyword to match in message/metadata. | `"timeout"` |
| page | integer | No (**Optional**) | Page index, default `0`. | `0` |
| size | integer | No (**Optional**) | Page size, default `20`. | `20` |

**Response Body**

Status Codes
- `200 OK`: Search completed.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| content | array | List of matching `LogEntryResponse`. |
| number | integer | Current page index. |
| size | integer | Page size. |
| totalElements | integer | Total matching records. |
| totalPages | integer | Number of pages. |
| first | boolean | First page marker. |
| last | boolean | Last page marker. |

Example JSON response
```json
{
  "content": [
    {
      "id": "6c3f9f7a-ec67-4565-9d1e-f8aebf7a4fd8",
      "serviceName": "auth-service",
      "timestamp": "2026-03-10T12:11:00Z",
      "level": "ERROR",
      "message": "Connection timeout",
      "metadata": "{\"region\":\"eu-west-1\"}",
      "source": "auth-api",
      "traceId": "trace-222",
      "createdAt": "2026-03-10T12:11:01Z"
    }
  ],
  "number": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

## 8) Logs Analytics (Placeholder)

**HTTP Method:** `GET`  
**Endpoint Path:** `/api/logs/analytics`  
**Description:** Placeholder endpoint; current implementation returns `null` body.

**Request Headers**
- `Accept: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| startDate | string (query) | No (**Optional**) | Start date filter. | `"2026-03-01"` |
| endDate | string (query) | No (**Optional**) | End date filter. | `"2026-03-11"` |

**Response Body**

Status Codes
- `200 OK`: Current code path returns `null` response payload.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| (none) | null | Endpoint currently returns `null`. |

Example JSON response
```json
null
```

---

## 9) Get Error Rate Per Service

**HTTP Method:** `GET`  
**Endpoint Path:** `/api/analytics/error-rate`  
**Description:** Returns error-rate metrics per service for the last 24 hours.

**Request Headers**
- `Accept: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| None | N/A | No (**Optional**) | No request body or query fields required. | `null` |

**Response Body**

Status Codes
- `200 OK`: Metrics returned.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data[].service | string | Service name. |
| data[].errorRate | number | Error-rate percentage. |
| data[].errorCount | integer | Count of error logs. |
| data[].totalCount | integer | Total logs counted. |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Error rates retrieved successfully",
  "data": [
    {
      "service": "auth-service",
      "errorRate": 5.2,
      "errorCount": 52,
      "totalCount": 1000
    }
  ],
  "timestamp": "2026-03-11T10:00:00.000"
}
```

---

## 10) Get Common Errors

**HTTP Method:** `GET`  
**Endpoint Path:** `/api/analytics/common-errors`  
**Description:** Returns top recurring error messages for a service.

**Request Headers**
- `Accept: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| service | string (query) | Yes | Target service name. | `"auth-service"` |
| limit | integer (query) | No (**Optional**) | Max results (1-100), default service behavior applies when omitted. | `10` |
| startTime | string (query) | No (**Optional**) | ISO-8601 or `YYYY-MM-DD`. | `"2026-03-01T00:00:00Z"` |
| endTime | string (query) | No (**Optional**) | ISO-8601 or `YYYY-MM-DD`. | `"2026-03-11"` |

**Response Body**

Status Codes
- `200 OK`: Data returned.
- `400 Bad Request`: Invalid datetime format or constraints.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data[].message | string | Error message text. |
| data[].count | integer | Occurrence count. |
| timestamp | string | API response timestamp. |

Example JSON response
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
      "message": "JWT validation failed",
      "count": 74
    }
  ],
  "timestamp": "2026-03-11T10:05:01.100"
}
```

---

## 11) Get Log Volume Time Series

**HTTP Method:** `GET`  
**Endpoint Path:** `/api/analytics/volume`  
**Description:** Returns log volume grouped by hour or day.

**Request Headers**
- `Accept: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| service | string (query) | Yes | Target service name. | `"auth-service"` |
| granularity | string (query) | Yes | Aggregation unit (`hour` or `day`). | `"hour"` |
| startTime | string (query) | No (**Optional**) | ISO-8601 or `YYYY-MM-DD`. | `"2026-03-01"` |
| endTime | string (query) | No (**Optional**) | ISO-8601 or `YYYY-MM-DD`. | `"2026-03-11T00:00:00Z"` |

**Response Body**

Status Codes
- `200 OK`: Data returned.
- `400 Bad Request`: Invalid datetime format or invalid granularity.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data[].timestamp | string (date-time) | Time bucket start. |
| data[].service | string | Service name. |
| data[].count | integer | Logs in bucket. |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Log volume retrieved successfully",
  "data": [
    {
      "timestamp": "2026-03-11T09:00:00Z",
      "service": "auth-service",
      "count": 42
    }
  ],
  "timestamp": "2026-03-11T10:10:55.009"
}
```

---

## 12) Get Health Dashboard

**HTTP Method:** `GET`  
**Endpoint Path:** `/api/health/dashboard`  
**Description:** Returns health status summary for monitored services.

**Request Headers**
- `Accept: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| None | N/A | No (**Optional**) | No request body or query fields required. | `null` |

**Response Body**

Status Codes
- `200 OK`: Dashboard returned.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data[].service | string | Service name. |
| data[].lastLogTime | string (date-time) | Last seen log timestamp. |
| data[].errorRate | number | Error rate percentage. |
| data[].status | string | Health status (`GREEN`, `YELLOW`, `RED`, `UNKNOWN`). |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Health dashboard retrieved successfully",
  "data": [
    {
      "service": "auth-service",
      "lastLogTime": "2026-03-11T09:59:12Z",
      "errorRate": 2.3,
      "status": "YELLOW"
    }
  ],
  "timestamp": "2026-03-11T10:15:00.001"
}
```

---

## 13) Get Retention Policies

**HTTP Method:** `GET`  
**Endpoint Path:** `/api/retention`  
**Description:** Returns all retention policies.

**Request Headers**
- `Accept: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| None | N/A | No (**Optional**) | No request body required. | `null` |

**Response Body**

Status Codes
- `200 OK`: Policies returned.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data[].id | integer | Policy ID. |
| data[].serviceName | string | Service name. |
| data[].retentionDays | integer | Retention duration in days. |
| data[].archiveEnabled | boolean | Archive toggle. |
| data[].createdAt | string (date-time) | Creation timestamp. |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Retention policies retrieved",
  "data": [
    {
      "id": 1,
      "serviceName": "auth-service",
      "retentionDays": 30,
      "archiveEnabled": false,
      "createdAt": "2026-03-10T12:00:00Z"
    }
  ],
  "timestamp": "2026-03-11T10:20:00.000"
}
```

---

## 14) Create Retention Policy

**HTTP Method:** `POST`  
**Endpoint Path:** `/api/retention`  
**Description:** Creates a new retention policy.

**Request Headers**
- `Content-Type: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| serviceName | string | Yes | Service name for policy. | `"auth-service"` |
| retentionDays | integer | No (**Optional**) | Retention period, defaults to `30`. | `45` |
| archiveEnabled | boolean | No (**Optional**) | Archive toggle, defaults to `false`. | `true` |

**Response Body**

Status Codes
- `201 Created`: Policy created.
- `400 Bad Request`: Invalid payload.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data.id | integer | Policy ID. |
| data.serviceName | string | Service name. |
| data.retentionDays | integer | Retention duration. |
| data.archiveEnabled | boolean | Archive toggle. |
| data.createdAt | string (date-time) | Creation timestamp. |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Policy created",
  "data": {
    "id": 2,
    "serviceName": "auth-service",
    "retentionDays": 45,
    "archiveEnabled": true,
    "createdAt": "2026-03-11T10:22:00Z"
  },
  "timestamp": "2026-03-11T10:22:00.456"
}
```

---

## 15) Update Retention Policy

**HTTP Method:** `PUT`  
**Endpoint Path:** `/api/retention/{serviceName}`  
**Description:** Updates retention settings for the given service.

**Request Headers**
- `Content-Type: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| serviceName | string (path) | Yes | Service name identifier in URL. | `"auth-service"` |
| retentionDays | integer | No (**Optional**) | Retention period, defaults to `30` in DTO. | `60` |
| archiveEnabled | boolean | No (**Optional**) | Archive toggle, defaults to `false` in DTO. | `false` |

**Response Body**

Status Codes
- `200 OK`: Policy updated.
- `404 Not Found`: Policy/service not found.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data.id | integer | Policy ID. |
| data.serviceName | string | Service name. |
| data.retentionDays | integer | Updated retention duration. |
| data.archiveEnabled | boolean | Updated archive setting. |
| data.createdAt | string (date-time) | Policy creation timestamp. |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Policy updated",
  "data": {
    "id": 2,
    "serviceName": "auth-service",
    "retentionDays": 60,
    "archiveEnabled": false,
    "createdAt": "2026-03-11T10:22:00Z"
  },
  "timestamp": "2026-03-11T10:24:10.111"
}
```

---

## 16) Delete Retention Policy

**HTTP Method:** `DELETE`  
**Endpoint Path:** `/api/retention/{serviceName}`  
**Description:** Deletes retention policy for the specified service.

**Request Headers**
- `Accept: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| serviceName | string (path) | Yes | Service name identifier in URL. | `"auth-service"` |

**Response Body**

Status Codes
- `200 OK`: Policy deleted.
- `404 Not Found`: Policy/service not found.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data | null | Always `null` for this operation. |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Policy deleted",
  "data": null,
  "timestamp": "2026-03-11T10:25:01.007"
}
```

---

## 17) Trigger Retention Cleanup

**HTTP Method:** `POST`  
**Endpoint Path:** `/api/retention/cleanup`  
**Description:** Triggers retention cleanup process immediately.

**Request Headers**
- `Accept: application/json`

**Request Body**

| Field Name | Data Type | Required | Description | Example Value |
|---|---|---|---|---|
| None | N/A | No (**Optional**) | No request body required. | `null` |

**Response Body**

Status Codes
- `200 OK`: Cleanup task triggered and completed by service call.

Response Fields

| Field Name | Data Type | Description |
|---|---|---|
| success | boolean | Indicates operation status. |
| message | string | Human-readable message. |
| data | null | Currently returned as `null`. |
| timestamp | string | API response timestamp. |

Example JSON response
```json
{
  "success": true,
  "message": "Retention cleanup completed",
  "data": null,
  "timestamp": "2026-03-11T10:26:30.200"
}
```

---

## Error Response Shape (Validation and Exceptions)

Typical error payload (from global exception handling):

| Field Name | Data Type | Description |
|---|---|---|
| message | string | Error message text. |
| status | integer | HTTP status code (present for some handlers). |
| errors | object | Field-level validation errors map (`field -> message`). |

Example JSON error response
```json
{
  "message": "Validation failed",
  "status": 400,
  "errors": {
    "email": "Please provide a valid email address",
    "password": "Password is required"
  }
}
```