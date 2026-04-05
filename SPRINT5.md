# Sprint 5: Intelligence Layer - High-Precision OCR & Parsing Logic

This document provides a comprehensive, reproducible specification of the Project Keepr Intelligence Layer developed during Sprint 5. It encompasses the architecture of pluggable OCR providers, the implementation of a heuristic-based parsing engine, decoupled confidence scoring, and high-precision operational hardening.

---

## 🎯 Sprint Objectives
- **Pluggable OCR**: Implement an abstraction for text extraction from documents.
- **Structured Parsing**: Build an engine to extract `Device` and `Warranty` fields from raw OCR text.
- **Intelligence Resilience**: Decouple parsing from scoring for independent tuning.
- **Operational Hardening**: Persist deep metrics (JSONB confidence breakdowns, stage-level timings, extraction versioning) and enforce security (upload limits).
- **Transactional Integrity**: Ensure heavy OCR/Parsing I/O exists outside database transactions while maintaining consistency.

---

## 🏗️ Architecture Overview

The Intelligence Layer follows a strict 3-phase orchestration flow within `IngestionProcessingService`:

1.  **Phase 1 (Mark Processing)**: `REQUIRES_NEW` transaction to transition `ExtractionJob` to `PROCESSING`.
2.  **Phase 2 (Intelligence Extraction)**: **NO TRANSACTION**.
    *   `OcrService` -> `OcrProvider` (OCR Extraction)
    *   `ParsingService` (Regex/Heuristic Parsing)
    *   `ConfidenceService` (Decoupled Scoring Engine)
    *   `ValidationService` (Business Rules & Thresholds)
3.  **Phase 3 (Atomic Finalization)**: `REQUIRES_NEW` transaction.
    *   Persist structured metrics and operational metadata.
    *   Atomic creation of `Device` and (optional) `Warranty` records.
    *   Transition `ExtractionJob` to `COMPLETED`.

---

## 🛠️ Implementation Details

### 1. Database Migrations (Flyway)
Four migrations were added or refined to support the expanded intelligence metadata and performance:

- **`V16-V18`**: Added `raw_text`, `confidence_score`, `extraction_json` (JSONB), `failure_reason`, and stage-level latency metrics (`ocr_ms`, `parse_ms`, `validate_ms`).
- **`V19__fix_raw_documents_soft_delete_index.sql`**: Optimized multi-tenant document queries by adding the missing `WHERE deleted_at IS NULL` predicate to the compound index.

### 2. Core Service Layer & Hardening

#### `FileStorageService.java` (Security Hardening)
- **Early Size Check**: Implemented a fail-fast `validateSize()` check on the first line of `store()`.
- **Limits**: Enforces a strict **10MB** limit injected from `keepr.upload.max-file-size`.
- **Observability**: Logs a `warn` on rejected oversized uploads with specific file details.

#### `ExtractionWorker.java` (Resiliency Hardening)
- **Programmable Backoff**: Implemented a dynamic exponential backoff strategy for job retries (`5s`, `25s`, `125s`) based on `retry_count`.
- **Zombie Recovery**: Extended the stale job recovery threshold to **30 minutes** to accommodate long-running extraction cycles.
- **Improved Logging**: Enhanced polling and processing logs to include the current `retryCount` for better production monitoring.

#### `ExtractionException.java` (Clean Code compliance)
- **Lombok Removal**: Removed prohibited `@Getter` annotation.
- **Observability**: Added standard chained constructors to preserve full stack traces and a manual `getFailureReason()` accessor.

#### `ValidationService.java` (Stability)
- **Null Safety**: Implemented proactive null guards for `ExtractionResult` parameters to prevent `NullPointerException` during the validation phase.

#### `IngestionProcessingService.java` (Orchestration Optimization)
- **State Reuse**: Refactored `finalizeJob` to accept a pre-computed `ValidationResult` for the warranty stage, eliminating redundant logic execution.
- **Tenancy Boundary**: Strictly maintained internal job processing via `findById()` to minimize parameter bloating in background pipelines.

---

## 🚦 Error Handling & Observability

- **`failure_reason` vs `error_message`**: Explicitly separated machine-readable classification codes (e.g., `LOW_CONFIDENCE`) from human-readable debug info/stack traces stored in `error_message`.
- **Metrics Logging**: Structured logs in format: 
  `[METRICS] jobId={} version=1 confidence={} status={} totalMs={} ocrMs={} parseMs={} validateMs={}`

---

## 🧪 Verification & QA

### ExtractionIntegrationTest.java
- **`extraction_successful_withFullData`**: Tests OCR -> Parse -> Device/Warranty creation + Metrics integrity.
- **Fail-Fast Testing**: Verified that oversized files are rejected with a `400 Bad Request` prior to stream processing.
- **Retry Logic**: Confirmed the worker correctly uses exponential thresholds for picking up failed jobs.

### Coding Standards Compliance
- **AGENTS.md**: No Lombok on Exceptions; records used for DTOs; no wild imports.
- **Multi-tenancy**: Boundaries strictly enforced at the ingress level (`findByIdAndHouseholdId` for user-facing lookups).

---

## 🧠 Reproducibility Notes for Engineers
1.  Apply migrations `V16` through `V19`.
2.  Configure `application.yml` with `spring.servlet.multipart.max-file-size: 10MB`.
3.  Ensure `FileStorageService` performs size validation **before** calling `tika.detect()`.
4.  Implement `ExtractionWorker` polling with thresholds calculated via `5 * Math.pow(5, retryLevel)`.
5.  Maintain a **3-Phase flow** in orchestration to ensure the database connection is released during high-latency OCR/Parsing tasks.
