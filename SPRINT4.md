# 🛡️ Sprint 4 Technical Blueprint: Async Document Ingestion

This document serves as an exhaustive replication guide for the Sprint 4 Async Ingestion Pipeline. A new engineer can rebuild the entire system by following these technical specifications.

---

## 🏗️ 1. Database Schema (DDL)
Run these Flyway-compatible migrations (`V13-V15`, fixed in `V19`) to establish the ingestion foundation.

```sql
-- 1. Ingestion Tables
CREATE TABLE raw_documents (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(512) NOT NULL,
    file_type VARCHAR(50) NOT NULL, -- Canonical MIME type (e.g., application/pdf)
    uploaded_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE extraction_jobs (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    raw_document_id UUID NOT NULL REFERENCES raw_documents(id),
    status VARCHAR(50) NOT NULL, -- PENDING, PROCESSING, COMPLETED, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    failure_reason VARCHAR(100), -- machine-readable code (e.g. LOW_CONFIDENCE)
    extraction_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- 2. Performance & Tenancy Indices
CREATE INDEX idx_extraction_jobs_status_created ON extraction_jobs (status, created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_raw_documents_household ON raw_documents (household_id) WHERE deleted_at IS NULL;
-- Fixed in V19: Added soft-delete predicate
CREATE INDEX idx_raw_documents_household_uploaded_by ON raw_documents(household_id, uploaded_by) WHERE deleted_at IS NULL;
```

---

## 🌐 2. API Endpoints
The following endpoints define the external contract for document ingestion.

### POST `/documents/upload`
- **Purpose**: Upload a document and initialize the async pipeline.
- **Request**: `MultipartFile file`
- **Response**: `202 Accepted` with `ExtractionJobResponse` (job_id, status).
- **Constraints**: 
  - Validates MIME type from prefix.
  - Enforces `keepr.upload.max-file-size` (10MB).

### GET `/documents/status/{jobId}`
- **Purpose**: Poll for the status of an extraction job.
- **Response**: `200 OK` with `ExtractionJobResponse`.
- **Security**: Must verify `household_id` of the authenticated user matches the job.

---

## 🛠️ 3. Dependencies & Configuration
Add the following to `pom.xml` for server-side MIME detection:

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
```

**Required Properties (`application.yml`):**
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

keepr:
  upload:
    dir: ${UPLOADS_PATH:/tmp/keepr/uploads}
    max-file-size: 10MB
```

---

## ⚙️ 4. Core Component Implementation

### A. The Ingestion Flow (Orchestration)
The system uses a **3-Phase Transactional Lifecycle** to prevent DB connection pool exhaustion.

1.  **Marking (`markProcessing`)**: 
    - **Transaction**: `@Transactional(propagation = Propagation.REQUIRES_NEW)`
    - **Logic**: Fetch job, verify not `PROCESSING`, update status, commit.
2.  **Processing (OCR/Parsing)**:
    - **Transaction**: **NONE**.
    - **Logic**: Long-running I/O (OCR/Parsing). Releases DB connections.
3.  **Finalizing (`finalizeJob`)**:
    - **Transaction**: `@Transactional(propagation = Propagation.REQUIRES_NEW)`
    - **Logic**: Persist domain entities and mark `COMPLETED`.

### B. Background Worker (`ExtractionWorker`)
- **Schedule**: `@Scheduled(fixedDelay = 5000)`
- **Query**: `findPendingJobsForUpdate` with `FOR UPDATE SKIP LOCKED`.
- **Retry Logic**: Implements programmable exponential backoff (5s, 25s, 125s).
- **Stale Recovery**: A scheduled task resets jobs stuck in `PROCESSING` longer than **30 minutes**.

### C. Security Layer (`FileStorageService`)
- Detect MIME from first **16KB bytes**.
- **Whitelist**: `application/pdf`, `image/jpeg`, `image/png`.
- **Fail-Fast**: Validate file size on the first line of `store()`.

---

## 🔄 5. Resiliency Details
- **Max Retries**: Exactly `3`.
- **Exponential Backoff**: Thresholds calculated as `5 * Math.pow(5, retryLevel)`.
- **Failure Logic**: `handleFailure` must load a fresh entity in `REQUIRES_NEW`.
- **Normalization**: Use `v.toLowerCase(Locale.ROOT)` for all deduplication comparisons.

---

## 🧪 6. Testing Requirements
1.  **MIME Spoofing**: Attempt to upload a `.sh` script renamed as `.pdf`; must be rejected.
2.  **Early Rejection**: Upload >10MB file; verify immediate `400` failure.
3.  **Zombie Recovery**: Manually set a job to `PROCESSING` with old `updated_at`; verify recovery task resets it.
4.  **Batch Drainage**: Multiple concurrent uploads; verify zero duplicate creation.
