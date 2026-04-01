# Sprint 4: Async Ingestion System (Document Extraction)

## Phase 1: Infrastructure & Queue Strategy
*   **Database Schema (V13):** Created `raw_documents` (file storage metadata) and `extraction_jobs` (async queue) with performance-critical indexing (`idx_jobs_status_created`) for high-concurrency worker polling.
*   **Config:** Enabled `@EnableScheduling` and implemented directory initialization logic for local storage stubs (/tmp/keepr-uploads).

## Phase 2: Secure Ingestion API
*   **Location:** `/api/v1/documents/upload` (POST)
*   **Multi-Tenancy:** Each document is strictly scoped to the `household_id` of the uploading user.
*   **Naming Safety:** Unique UUID-based filenames to prevent overwrites.
*   **Tracking:** Returns a `jobId` for immediate frontend polling.

## Phase 3: Background Worker & Retry Logic
*   **DB as Queue:** Implemented `FOR UPDATE SKIP LOCKED` in `ExtractionJobRepository` to allow safe, concurrent worker polling.
*   **Retry Backoff:** Exponential delays to prevent hammering failing extraction jobs (0s -> 30s -> 2m).
*   **Zombie Recovery:** Automatic reaper to reset jobs stuck in `PROCESSING` if a worker node crashes mid-task.
*   **Transaction Isolation:** Each job is processed in `REQUIRES_NEW` for atomic Success/Failure boundaries.

## Phase 4: Core Service Refactoring
*   **Decoupling:** Refactored `DeviceService` and `WarrantyService` to support internal creation calls (`createDeviceInternal` and `createWarrantyInternal`) using `household_id`, removing the need for a fake "KeeprPrincipal" in background tasks.
*   **Idempotency:** Implemented existence checks to reuse existing active devices/warranties during extraction processing.

## Phase 5: Extraction Flow (Stubs)
*   **OcrService:** Stub simulating text extraction.
*   **ParsingService:** Stub converting raw text into structured device and warranty entities.

## Verification
*   **Integration Tests:** Finalized `IngestionIntegrationTest` verifying end-to-end async flow, multitenancy, and failure recovery.
*   **Sacred Commands:** All pass successfully:
  * `./mvnw compile` (Success)
  * `./mvnw test` (Success — 38 tests verified)
  * `./mvnw checkstyle:check` (Success — 0 violations)
