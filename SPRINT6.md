# 🏁 Sprint 6 Completion — Human Review System

## 🎯 Goal
Transform Keepr from a backend extraction engine into a usable, trustworthy product system by introducing a human-in-the-loop review process for low-confidence or validation-failed extractions.

## 📦 Accomplishments

### 1. New Data Model & Migrations
- Created `review_tasks` table with `JSONB` support for `extraction_json`.
- Added indexes on `(household_id, status)` and `job_id` for efficient polling and retrieval.
- Expanded `JobStatus` to include `REVIEW_REQUIRED` and `USER_CONFIRMED`.

### 2. Ingestion Pipeline Routing (The "Brain")
- Enhanced `IngestionProcessingService` to calculate confidence and validate results before finalizing.
- Implemented **Automatic Routing**: If confidence < 0.5 or device validation fails, the job is moved to `REVIEW_REQUIRED` and a `ReviewTask` is generated.
- Used `Propagation.REQUIRES_NEW` for status transitions to ensure atomic metadata persistence (metrics, raw text) even when the extraction fails validation.

### 3. Human Review APIs
- **`GET /api/v1/review/tasks`**: List pending review tasks for the current household.
- **`GET /api/v1/review/tasks/{id}`**: Fetch detailed task context (including OCR raw text and the original extraction snapshot).
- **`POST /api/v1/review/tasks/{id}/confirm`**: Submit corrections. This triggers the same idempotent device/warranty creation logic used in the automated path.

### 4. Technical Hardening
- **Map-based JSONB**: Migrated `extraction_json` from String to `Map<String, Object>` to leverage Hibernate's `@JdbcTypeCode(SqlTypes.JSON)` for native PostgreSQL JSONB support.
- **Null Safety**: Resolved multiple null type safety warnings across the service and test layers using `Objects.requireNonNull`.
- **Failure Reason Propagation**: Specific validation failure codes (e.g., `MISSING_PRODUCT_NAME`) are now correctly persisted to the `ExtractionJob` record.

## ✅ Verification Results

### Automated Tests
- **`ReviewIntegrationTest`**: Verified the full lifecycle of a review task (listing, detail retrieval, confirmation, and error handling for double-confirmation).
- **`ExtractionIntegrationTest`**: Updated to verify that failing jobs correctly transition to `REVIEW_REQUIRED` and persist timing metrics/failure reasons.
- **Total Tests Run**: 9
- **Failures**: 0
- **Build Status**: 🟢 SUCCESS

### Checkstyle
- Verified zero violations via `./mvnw checkstyle:check`.

## 📈 Impact
The system is no longer "black box." When the AI is unsure, it asks the user for help instead of silently failing or creating junk data. This provides a high-trust foundation for the upcoming LLM-based intelligence features in Sprint 7.
