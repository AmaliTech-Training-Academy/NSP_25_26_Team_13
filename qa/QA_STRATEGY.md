# LogStream Project: Comprehensive QA Strategy & Technical Architecture

This document defines the quality assurance roadmap for the LogStream Aggregator, detailing technical analysis, test planning, and execution protocols for the two core ecosystems: **Python (Data Engineering)** and **Java (Backend API)**.

---

## 1. Technical Analysis & Component Breakdown

### A. Data Engineering (Python Ecosystem)
The Python module facilitates high-throughput log ingestion and data transformation.
*   **Key Components Analyzed**:
    *   `etl_pipeline.py`: Handles dynamic PostgreSQL partition creation, incremental log extraction (15/60/1440 min windows), and health metric derivation.
    *   `retention_policy.py`: Manages the lifecycle of log partitions (detach and drop logic).
    *   `config/`: Environment-specific DB routing.
*   **Testing Priority**: High risk in data integrity, date-math logic for partitions, and idempotency (pre-deletion of existing trends before reload).

### B. Backend API (Java/Spring Boot Ecosystem)
The Java module serves as the secure gateway for log consumption and analytics visualization.
*   **Key Components Analyzed**:
    *   `LogController`: Ingestion (single/batch), Search (paginated), and Retention endpoints.
    *   `Security Filter Chain`: JWT parsing, RBAC (ADMIN/USER roles), and `/error` path security.
    *   `SearchService`: Query predicate generation for complex log filtering (Date range and Pagination).
*   **Testing Priority**: High risk in authorization bypass (Security), REST contract compliance, and search performance.

---

## 2. Test Planning & Methodology

*Note: All unit testing (testing of granular logic using mocks) is the responsibility of the development engineering team and is outside the scope of this QA strategy.*

### I. Integration Testing (Component Flow) - Primary Focus
*   **Python**: Test against a containerized PostgreSQL instance to verify that `manage_partitions()` correctly creates `log_entries_yYYYY_MM_DD` tables, end-to-end extraction, and aggregations.
*   **Java**: Use Rest-Assured or similar integration testing tools. Focus on the end-to-end HTTP request lifecycle from the Controller to the real DB, ensuring the `@AuthenticationPrincipal` is correctly populated and endpoints return expected responses.

### II. Infrastructure and Connectivity (Dockerized)
The integration suite relies on a live Docker environment with the following configuration:
*   **Database**: PostgreSQL mapped to host port `5433` (internal `5432`). Ensure `pgdata` and `de_data` volumes are correctly defined.
*   **Backend**: Spring Boot 3.x with Java 21 support (required for Text Blocks).
*   **Environment**: A `.env` file must be present at the root, containing `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `BACKEND_PORT`, and `JWT_SECRET`.

### III. Advanced QA Ideas (Beyond Basics) - [VERIFIED]
1.  **Contract Testing**: [COMPLETED] Validated that the search response strictly matches the expected JSON contract (verified in `LogIngestionApiTest`).
2.  **Boundary & Negative Testing**:
    - **Partition Rollover**: [PLANNED for Phase 2 ETL]
    - **Security Scanning**: [COMPLETED] Implemented automated checks for SQL injection in search predicates and JWT manipulation/tampering in `SecurityApiTest`.
    - **Malformed Payloads**: [COMPLETED] Resolved 403 authorization issue; now returns clean 400 Bad Request.
    - **RBAC Enforcement**: [COMPLETED] Strictly restricted `/api/retention` to ADMIN role; verified rejection for USER role.
3.  **Volume/Load Testing**: [COMPLETED] Simulated 50+ concurrent batch ingestions to verify ingestion throughput.

---

## 3. Test Cases & Suite Execution

### Architecture: Cases vs. Suites
*   **Test Case**: An atomic verification (e.g., `verify_batch_ingest_rejects_empty_payload`).
*   **Test Suite**: Logical groupings.
    - `SmokeSuite`: Essential ingestion and login (Runs on every PR).
    - `RegressionSuite`: Complete coverage of all endpoints (Runs before release).
    - `ETLSuite`: Specific to the Python aggregation logic.

### Execution Protocol

#### Unified Test Execution (Python + Java)
A PowerShell script is provided to run both suites sequentially:
- **Command**: `.\run_all_tests.ps1` (Executes Phase 1 Python and Phase 2 Java Maven tests).

#### Running Python Tests (Data Engineering)
- **Individual Case**: `pytest tests/test_etl.py::test_metric_aggregation`
- **Full Suite**: `pytest tests/` (Requires `.venv`)
- **Coverage Check**: `coverage run -m pytest && coverage report`

#### Running Java Tests (Backend)
- **Individual Case**: `mvn test -Dtest=LogIngestionApiTest#singleLogIngestSuccess`
- **Full Suite**: `mvn test` (Requires Java 17+ and Docker environment).
- **Profile-based Execution**: `mvn test -Pintegration-tests`

---

## 4. Quality Gates (CI/CD)
*   **Pre-Commit**: Java linting (Checkstyle) and Python flake8.
*   **Post-Merge**: 100% pass rate required for `SmokeSuite`.
*   **Final Release**: Python coverage must exceed 80%, and all Java Integration tests must pass with a zero-auth-fail record.
