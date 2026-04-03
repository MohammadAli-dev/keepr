# Sprint 4: Async Document Ingestion Pipeline

## Overview
In this sprint, we implemented a robust, async document ingestion pipeline that transforms user uploads (PDFs, images) into tracked devices and warranties. The architecture enforces strict multi-tenancy constraints, idempotency, and high resiliency against database/filesystem failures.

All processing triggers an async `ExtractionJob` handled reliably by `ExtractionWorker`.

---

## 🛠️ API Endpoints Implemented

### 1. Document Upload
**`POST /api/v1/documents/upload`**
- **Purpose**: Upload a document for automatic metadata extraction (device/warranty detection).
- **Validation**: 
  - Max file size: 10MB
  - Content Types allowed: PDF, JPEG, PNG
  - Filename renaming strictly implemented (UUID-based) using MIME types.
- **Output**: Returns `documentId`, `jobId`, and initial `PENDING` status.

### 2. Job Status Check
**`GET /api/v1/documents/jobs/{jobId}`**
- **Purpose**: Polling endpoint to check the long-running OCR processing flow.
- **Security Check**: Enforces household-scoped access control.
- **Output**: Returns status (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`).

---

## 🏗️ Architecture & Resiliency

### File I/O Decoupling & Security
- `FileStorageService` separated physical filesystem operations from database transactions.
- Added strict Path Traversal protections when reading/saving/deleting files.
- Original client filenames are completely ignored. Instead, files map precisely to a clean generic extension assigned out of safe MIME type detection (e.g. `image/jpeg` -> `.jpg`).

### Idempotency & Concurrency Safety
1. **Device Level**: `DeviceService` applies fuzzy deduplication across (`name`, `brand`, `model`, `householdId`).
2. **Warranty Level**: Applied overlap idempotency handling by checking specific date ranges.
3. **Extraction Locks**: The `ExtractionWorker` utilizes PostgreSQL's `FOR UPDATE SKIP LOCKED` for reliable multi-node parallel fetching.

### Retry Backoff & Failure Handling
- **`MAX_RETRIES`**: Max retry attempts are strictly capped at `3` before entering `FAILED` status indefinitely.
- **Backoff Mechanics**:
  - `retryCount = 0` → Retry immediately
  - `retryCount = 1` → Retry after 30 seconds
  - `retryCount = 2` → Retry after 2 minutes
- **Failure Service**: Failures correctly utilize `@Transactional(propagation = Propagation.REQUIRES_NEW)` inside `IngestionFailureService` to gracefully commit failure thresholds (increment counts) even when primary entity transactions roll back.
- **Cleanup on Terminal States**: Active files are kept through retry boundaries. File debris cleanly deletes when max-retries hit `FAILED` or the system processes successfully (`COMPLETED`).

### Future Roadmap
- **Auditing `job_logs`**: In future sprints, consider adding a comprehensive `job_logs` database table capable of storing granular, text-first traces of parsing/OCR behavior or exception streams for support team debugging.

---

## 🚨 Edge-Cases Captured & Squashed
- **Zombie Job Recovery**: Background process reliably unearths stuck jobs >5m from `PROCESSING` state back into action bounded by the retry limit dynamically evaluated via Java time logic.
- **AuthOtp Test Refactor**: Cleaned out direct entity cross-dependencies by utilizing robust inline JDBC test verifications to retrieve DB-backed OTPs directly inside `IngestionIntegrationTest` and `DeviceWarrantyIntegrationTest`.
