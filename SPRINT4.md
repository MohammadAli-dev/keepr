# Sprint 4: Async Ingestion System (Document Extraction)

## 1. Infrastructure & Data Model (V13 - V15)
*   **RawDocument Entity:** Stores file metadata with `household_id`, `file_url`, `file_type`, and `uploaded_by` tracking.
*   **ExtractionJob Entity:** Implements a robust state machine using the `JobStatus` enum: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`.
*   **Database Optimization (V15 Migration):**
    *   `idx_jobs_status_created`: Optimized for high-frequency worker polling.
    *   **New:** `idx_raw_docs_household_uploader`: Compound index on `(household_id, uploaded_by)` to accelerate multi-tenant document lookups.
    *   `idx_raw_documents_household`: Enforces fast, household-scoped document isolation.

## 2. Secure Ingestion API & Hardened Storage
*   **Upload Endpoint:** `POST /api/v1/documents/upload`
*   **[Hardened] Server-Side MIME Detection:** 
    *   Integrated **Apache Tika** for magic-byte sniffing.
    *   Uses a 16KB-bounded `BufferedInputStream` to detect real media types without trusting client headers.
    *   Strict Whitelist: `application/pdf`, `image/jpeg`, `image/png`. Rejects Unknown/Spoofed types (e.g., `.exe` renamed to `.pdf`).
*   **Storage Abstraction:** Introduced `StoredFile` DTO to encapsulate safe storage paths and detected MIME types, preventing OS-specific path leakage to the controller layer.
*   **Status Tracking API:** `GET /api/v1/documents/jobs/{jobId}`
    *   **Security:** Enforces strict household boundaries using `findByIdAndHouseholdId`.
    *   **Boundary Enforcement:** Removed all repository logic from the Controller; delegated orchestration to `IngestionService`.

## 3. Worker & Async Logic
*   **Worker Strategy:** `ExtractionWorker` polls the DB every 5 seconds using `FOR UPDATE SKIP LOCKED` for safe concurrency in multi-instance environments.
*   **Metadata Isolation:** Introduced `IngestionMetadataService` to handle `RawDocument` and `ExtractionJob` persistence in an isolated transaction, resolving Spring proxy self-invocation issues.
*   **Zombie Job Recovery:** Hardened `resetStaleJobs` query to be **soft-delete aware** (`deleted_at IS NULL`) and to handle terminal state transitions correctly after max retries.

## 4. Failure Handling & Retry Engine
*   **Deadlock Prevention:** Refactored `IngestionFailureService` to accept `UUID jobId` instead of entity objects. It executes lookups and updates in a dedicated `REQUIRES_NEW` transaction to avoid row-lock contention.
*   **Retry Limit:** `MAX_RETRIES = 3`.
*   **Idempotency Guards:** Added logic to `handleFailure` to prevent double-processing if a job has already reached a terminal state (`COMPLETED` or `FAILED`).
*   **Exponential Backoff Logic:** Polling query respects specific intervals:
    *   Retry 1: After 30 seconds.
    *   Retry 2: After 2 minutes.
    *   Final Failure: Automatic transition to `FAILED` status after the 3rd unsuccessful attempt.

## 5. Domain Service Normalization
*   **Deterministic Folding:** Upgraded `DeviceService.normalize()` to use `Locale.ROOT`. This ensures that "MacBook" and "MACBOOK" resolve to the same record regardless of the server's OS locale.
*   **Service Decoupling:** Refactored `DeviceService` and `WarrantyService` to expose `*Internal` methods for background workers.

## 6. Hardened Verification Suite
*   **IngestionIntegrationTest.java:** Significantly expanded to cover:
    *   **MIME Spoofing:** Rejection of malicious scripts disguised as PDFs.
    *   **Failure Idempotency:** Ensuring late-arriving errors don't corrupt completed jobs.
    *   **Retry State Machine:** Verifying correct transitions to `FAILED` after max attempts.
    *   **Auth Flow:** Restored reliable OTP retrieval from database for test continuity.
*   **Build & Style:** 
    *   Verified 100% Checkstyle compliance (fixed 3 violations during hardening).
    *   Successful `./mvnw clean compile` verification.
