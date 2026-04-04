# 🛡️ Sprint 4 Technical Blueprint: Async Document Ingestion

This document serves as an exhaustive replication guide for the Sprint 4 Async Ingestion Pipeline. A new engineer can rebuild the entire system by following these technical specifications.

---

## 🏗️ 1. Database Schema (DDL)
Run these Flyway-compatible migrations (`V13-V15`) to establish the ingestion foundation.

```sql
-- 1. Ingestion Tables
CREATE TABLE raw_documents (
    id UUID PRIMARY KEY,
    household_id UUID NOT NULL REFERENCES households(id),
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(512) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);

-- 2. Performance & Tenancy Indices
CREATE INDEX idx_extraction_jobs_status_created ON extraction_jobs (status, created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_raw_documents_household ON raw_documents (household_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_raw_documents_household_uploaded_by ON raw_documents(household_id, uploaded_by);
```

---

## 🛠️ 2. Dependencies & Configuration
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
keepr:
  upload:
    dir: ${UPLOADS_PATH:/tmp/keepr/uploads}
```

---

## ⚙️ 3. Core Component Implementation

### A. The Ingestion Flow (Orchestration)
The system uses a **3-Phase Transactional Lifecycle** to prevent DB connection pool exhaustion.

1.  **Marking (`markProcessing`)**: 
    - **Transaction**: `@Transactional(propagation = Propagation.REQUIRES_NEW)`
    - **Logic**: Fetch the job by ID, verify it's not already `PROCESSING`, update status and commit immediately.
2.  **Processing (OCR/Parsing)**:
    - **Transaction**: **NONE** (Run in non-transactional method).
    - **Logic**: Perform long-running I/O (Tesseract/Parsing/FileSystem). Releasing DB connections here prevents pool starvation.
3.  **Finalizing (`finalizeJob`)**:
    - **Transaction**: `@Transactional(propagation = Propagation.REQUIRES_NEW)`
    - **Logic**: Persist domain entities (Devices/Warranties) and mark job as `COMPLETED`.

### B. Background Worker (`ExtractionWorker`)
- **Schedule**: `@Scheduled(fixedDelay = 5000)`
- **Query**: `findPendingJobsForUpdate` using `FOR UPDATE SKIP LOCKED` for high-concurrency safety.
- **handoff**: Passes `UUID jobId` to the processing service to avoid passing stale JPA entities across transaction boundaries.

### C. Security Layer (`FileStorageService`)
- Detect MIME from first **16KB bytes** using `tika.detect(BufferedInputStream)`.
- Use `mark()` and `reset()` to ensure the stream is reusable.
- **Whitelist**: Rejects everything except `application/pdf`, `image/jpeg`, and `image/png`.

### D. Domain Modeling: Physical vs Generic
- **`DeviceService.createDevice`**: Manual creation always returns a **new instance**.
- **`DeviceService.createDeviceIngestion`**: Ingestion uses normalized `name+brand+model+household` checks for idempotency.

---

## 🔄 4. Resiliency Details
- **Max Retries**: Exactly `3`.
- **Failure Logic**: `handleFailure(UUID jobId, Exception e)` must load a fresh entity in a `REQUIRES_NEW` transaction to avoid locking/deadlock issues.
- **Normalization**: Use `v.toLowerCase(Locale.ROOT)` in all deduplication logic.

---

## 🧪 5. Testing Requirements
A replica is not complete without these exact test scenarios:
1.  **MIME Spoofing**: Attempt to upload a `.sh` script renamed as `.pdf`; must be rejected with `400 Bad Request`.
2.  **Transaction Rollback**: If `ExtractionJob` creation fails, verify the `RawDocument` file is not orphaned.
3.  **Batch Drainage**: Run the `ExtractionWorker` against multiple concurrent uploads; verify zero duplicate devices are created.
4.  **Recursive Cleanup**: Integration tests must clear `users`, `households`, `devices`, and `warranties` in correct FK order during `@BeforeEach`.
