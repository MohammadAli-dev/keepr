# Sprint 5 Blueprint: Intelligence Layer & Operational Hardening

## 🎯 Sprint Overview
Sprint 5 focused on transforming messy document data into a structured inventory through the implementation of a high-trust document ingestion and intelligence pipeline. This was followed by a comprehensive "Operational Hardening" phase (Sprint 5.3) to resolve security findings and ensure production readiness.

---

## 🏗️ Core Architecture: The Ingest-Process-Finalize Pipeline

Keepr uses a 3-step transactional orchestration to process uploads without exhausting database connection pools.

### 1. 📂 Document Ingestion (`FileStorageService`)
- **Strict Limits**: Enforced 10MB file size limit to prevent memory pressure.
- **Fail-Fast Validation**: Rejected oversized uploads before I/O processing.
- **Persistence**: Storage of raw PDF/Image bytes with deduplication checks.

### 2. ⚡ Processing Orchestration (`IngestionProcessingService`)
Orchestrates the lifecycle of an extraction job using high-precision timing.

- **Phase A: Preparation**: Marks the job as `PROCESSING`.
- **Phase B: Intelligence (No Tx)**:
    - **OCR**: Conversion of document to text via `OcrProvider`.
    - **Parsing**: Rule-based extraction of fields (Product, Brand, Date, etc.).
    - **Confidence Scoring**: Dynamic calculation of extraction accuracy.
    - **Validation**: Business logic checks (e.g., Warranty start before end).
- **Phase C: Persistence**: Transactional creation of `Device` and `Warranty` entities based on the intelligence result.

---

## 🧠 Intelligence Layer Components

### 🔍 Parsing Engine (`ParsingService`)
- **Defensive Design**: Added null/blank guards for raw text.
- **Regex Hardening**: Migrated magic strings to precompiled `Pattern` constants for performance and maintainability.
- **Standardized Errors**: Throws `ExtractionException` with structured reasons like `EMPTY_OCR_TEXT`.

### 📊 Scoring Engine (`ConfidenceService`)
- **Schema Stability**: Defined public key constants (e.g., `product_name`, `brand`) for the `confidence_breakdown` JSONB, ensuring downstream analytics don't break.
- **Dynamic Metrics**: Derived `totalFields` dynamically from the extraction result.
- **Accurate Counter**: Success metrics now only count fields that contribute a positive score (weight > 0).

---

## 🛡️ Operational Hardening (Remediation)

### 1. 🔁 Resiliency & Recovery (`ExtractionWorker`)
- **Exponential Backoff**: Implemented a programmable retry strategy (5s, 25s, 125s) to handle transient failures.
- **Zombie Recovery**: Added a scheduled task that resets jobs stuck in `PROCESSING` longer than 30 minutes.
- **Idempotency**: All entity creation checks for existing records (Device/Warranty) before insertion.

### 2. 🔒 Security & Data Integrity
- **Database Schema**: Created `V20` migration to safely enforce `NOT NULL` on the `extraction_version` column.
- **Mock Guards**: Restricted `StubOcrProvider` to `local` and `test` profiles using `@Profile` to avoid stub data in production.

### 3. 🧪 Modernized Integration Tests (`ExtractionIntegrationTest`)
- **Spring 3.4+ Compliance**: Replaced deprecated `@MockBean` with `@MockitoBean`.
- **Resilient Assertions**: Updated timing assertions to use `isGreaterThanOrEqualTo(0)` to prevent flakiness in fast-running environments.

---

## 🚀 Key Commands

| Task | Command |
| --- | --- |
| Full Build | `./mvnw clean compile` |
| Integration Tests | `./mvnw test -Dtest=ExtractionIntegrationTest` |
| Flyway Info | `./mvnw flyway:info` |

---

## 📜 Sprint Artifacts Summary
- [x] **Pipeline**: Async Redis Streams handling worker polling.
- [x] **Intelligence**: Confidence-based decision making for auto-inventory.
- [x] **Resiliency**: Programmable backoff and recovery task.
- [x] **Hardening**: Resolved 10+ CodeRabbit findings for production safety.

> [!NOTE]
> All code complies with `AGENTS.md` standards: Records used for DTOs, Lombok restricted, and `household_id` enforced for all tenancy boundaries.
