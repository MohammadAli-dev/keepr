# Sprint 4: Async Ingestion System (Document Extraction)

## 1. Infrastructure & Data Model (V13)
*   **RawDocument Entity:** Stores file metadata with `household_id`, `file_url`, `file_type`, and `uploaded_by` tracking.
*   **ExtractionJob Entity:** Implements a state machine using the `JobStatus` enum: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`.
*   **Database Optimization:**
    *   `idx_jobs_status_created`: Optimized for the high-frequency worker polling query.
    *   `idx_raw_documents_household`: Enforces fast, household-scoped document lookups.

## 2. Secure Ingestion API
*   **Upload Endpoint:** `POST /api/v1/documents/upload`
    *   **File Validation:** Enforces a 10MB size limit and restricts types to PDF and Images (JPEG/PNG).
    *   **Naming Safety:** Uses UUID-prefixed filenames (`UUID-originalName`) to prevent collisions in local storage.
*   **Status Tracking API:** `GET /api/v1/documents/jobs/{jobId}`
    *   **Security:** Uses `findByIdAndHouseholdId` to ensure users can only track jobs within their own household.
    *   **DTOs:** Uses `UploadDocumentResponse` and `JobStatusResponse` for clean API contracts.

## 3. Background Worker & Queue Logic
*   **Worker Strategy:** `ExtractionWorker` polls the DB every 5 seconds using `FOR UPDATE SKIP LOCKED` for safe concurrency.
*   **Decoupled Logic:** Introduced `IngestionProcessingService` to handle the core extraction lifecycle independently of the web layer.
*   **Zombie Job Recovery:** Implemented `resetStaleJobs` query to reset any job stuck in `PROCESSING` for more than 5 minutes back to `PENDING`.

## 4. Failure Handling & Retries
*   **Retry Engine:** Implemented `handleFailure()` within a `REQUIRES_NEW` transaction boundary.
*   **Retry Limit:** `MAX_RETRIES = 3`.
*   **Exponential Backoff:** Exact retry delays enforced via the repository query:
    *   Retry 1: After 30 seconds.
    *   Retry 2: After 2 minutes.
    *   Final Failure: Transition to `FAILED` status after 3 unsuccessful attempts.

## 5. Domain Service Refactoring & Idempotency
*   **Service Decoupling:** Refactored `DeviceService` and `WarrantyService` to expose `*Internal(..., UUID householdId)` methods, allowing background processing without a `KeeprPrincipal`.
*   **Fuzzy Idempotency:**
    *   **Devices:** Uses `findByNameAndBrandAndModelAndHouseholdId` to reuse existing active devices instead of creating duplicates.
    *   **Warranties:** Implemented an exact-match check (Type + StartDate + EndDate) to prevent redundant warranty entries for the same device.

## 6. Verification & Completion
*   **Full Integration Test:** `IngestionIntegrationTest.java` verifies the end-to-end lifecycle, including multitenancy isolation, worker polling, and idempotency logic.
*   **Build Status:** Verified successful clean build and 100% test pass rate (38/38 tests).
*   **Style Compliance:** All code is Checkstyle-compliant with no star imports or indentation violations.
