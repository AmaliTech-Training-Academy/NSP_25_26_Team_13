#!/usr/bin/env bash

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "Using BASE_URL=${BASE_URL}"

JWT_TOKEN="${JWT_TOKEN:-eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzdHJpbmdAZ21haWwuY29tIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3NzMyMjM4NTksImV4cCI6MTc3MzMxMDI1OX0.oqqhM_8MoLSXfIwLFSJ8ReTNbOLkiXoKKW7SDCk9YSM}"
AUTH_ARGS=()

echo "=== Auth: Register (optional, may fail if user exists) ==="
curl -i -X POST "${BASE_URL}/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123",
    "fullName": "Test User"
  }'
echo -e "\n"

echo "=== Auth: Login (obtain JWT) ==="
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123"
  }')
echo "${LOGIN_RESPONSE}"

if [ -z "${JWT_TOKEN}" ]; then
  JWT_TOKEN="$(printf '%s' "${LOGIN_RESPONSE}" | sed -n 's/.*"token":"\\([^"]*\\)".*/\\1/p')"
fi

if [ -n "${JWT_TOKEN}" ]; then
  echo "Extracted JWT token."
  AUTH_ARGS=(-H "Authorization: Bearer ${JWT_TOKEN}")
else
  echo "WARNING: Could not extract JWT token from login response."
  AUTH_ARGS=()
fi

echo
echo "=== Logs: Ingest single log (POST /api/logs) ==="
curl -i -X POST "${BASE_URL}/api/logs" \
  -H "Content-Type: application/json" \
  "${AUTH_ARGS[@]}" \
  -d '{
    "timestamp": "2026-03-11T10:00:00Z",
    "serviceName": "auth-service",
    "level": "INFO",
    "message": "User logged in successfully",
    "traceId": "trace-123",
    "userId": "user-1",
    "metadata": {
      "ip": "127.0.0.1"
    }
  }'
echo -e "\n"

echo "=== Logs: Ingest batch logs (POST /api/logs/batch) ==="
curl -i -X POST "${BASE_URL}/api/logs/batch" \
  -H "Content-Type: application/json" \
  "${AUTH_ARGS[@]}" \
  -d '{
    "logs": [
      {
        "timestamp": "2026-03-11T10:05:00Z",
        "serviceName": "auth-service",
        "level": "ERROR",
        "message": "Login failed",
        "traceId": "trace-124",
        "userId": "user-2",
        "metadata": {
          "reason": "invalid_credentials"
        }
      },
      {
        "timestamp": "2026-03-11T10:10:00Z",
        "serviceName": "payment-service",
        "level": "WARN",
        "message": "Slow response from gateway",
        "traceId": "trace-125",
        "userId": "user-3",
        "metadata": {
          "latency_ms": 1200
        }
      }
    ]
  }'
echo -e "\n"

echo "=== Logs: Search logs (POST /api/logs/search) ==="
curl -i -X POST "${BASE_URL}/api/logs/search" \
  -H "Content-Type: application/json" \
  "${AUTH_ARGS[@]}" \
  -d '{
    "serviceName": "auth-service",
    "keyword": "login",
    "level": "INFO",
    "page": 0,
    "size": 10
  }'
echo -e "\n"

echo "=== Analytics: Error rate (GET /api/analytics/error-rate) ==="
curl -i "${BASE_URL}/api/analytics/error-rate"
echo -e "\n"

echo "=== Analytics: Common errors (GET /api/analytics/common-errors) ==="
curl -i "${BASE_URL}/api/analytics/common-errors?service=auth-service&limit=5"
echo -e "\n"

echo "=== Analytics: Log volume (GET /api/analytics/volume) ==="
curl -i "${BASE_URL}/api/analytics/volume?service=auth-service&granularity=hour"
echo -e "\n"

echo "=== Retention: List policies (GET /api/retention) ==="
curl -i "${BASE_URL}/api/retention"
echo -e "\n"

echo "=== Retention: Create policy (POST /api/retention) ==="
curl -i -X POST "${BASE_URL}/api/retention" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "auth-service",
    "retentionDays": 30,
    "archiveEnabled": false
  }'
echo -e "\n"

echo "=== Retention: Update policy (PUT /api/retention/{serviceName}) ==="
curl -i -X PUT "${BASE_URL}/api/retention/auth-service" \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "auth-service",
    "retentionDays": 60,
    "archiveEnabled": true
  }'
echo -e "\n"

echo "=== Retention: Delete policy (DELETE /api/retention/{serviceName}) ==="
curl -i -X DELETE "${BASE_URL}/api/retention/auth-service"
echo -e "\n"

echo "=== Retention: Trigger cleanup (POST /api/retention/cleanup) ==="
curl -i -X POST "${BASE_URL}/api/retention/cleanup"
echo -e "\n"

echo "=== Admin: Trigger cleanup (POST /api/admin/cleanup) ==="
curl -i -X POST "${BASE_URL}/api/admin/cleanup"
echo -e "\n"

echo "=== Health: Dashboard (GET /api/health/dashboard) ==="
curl -i "${BASE_URL}/api/health/dashboard"
echo -e "\n"

echo "=== OpenAPI: JSON (GET /v3/api-docs) ==="
curl -i "${BASE_URL}/v3/api-docs"
echo -e "\n"

echo "=== Swagger UI (GET /swagger-ui/index.html) ==="
curl -i "${BASE_URL}/swagger-ui/index.html"
echo -e "\n"

echo "All endpoint tests completed."

