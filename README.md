# Project Keepr

> A high-trust home warranty and invoice tracking platform for Indian consumers. Keepr transforms messy document data (invoices, warranty cards, receipts) into a structured household device and warranty inventory through an async multi-step extraction pipeline.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Problem Statement](#2-problem-statement)
3. [System Capabilities](#3-system-capabilities)
4. [System Limitations](#4-system-limitations)
5. [High-Level Architecture](#5-high-level-architecture)
6. [Detailed Architecture Breakdown](#6-detailed-architecture-breakdown)
7. [Tech Stack](#7-tech-stack)
8. [Folder Structure](#8-folder-structure)
9. [Setup Instructions](#9-setup-instructions)
10. [Running the Project](#10-running-the-project)
11. [Running with Testcontainers](#11-running-with-testcontainers)
12. [API Documentation](#12-api-documentation)
13. [End-to-End Flow](#13-end-to-end-flow)
14. [Retry & Failure Handling](#14-retry--failure-handling)
15. [Data Model](#15-data-model)
16. [Intelligence Layer](#16-intelligence-layer)
17. [Metrics & Observability](#17-metrics--observability)
2. [Mental Model](#2-mental-model)
3. [Core Design Principles](#3-core-design-principles)
4. [Problem Statement](#4-problem-statement)
5. [Current Product State](#5-current-product-state)
6. [High-Level Architecture](#6-high-level-architecture)
7. [Detailed Architecture Breakdown](#7-detailed-architecture-breakdown)
8. [Tech Stack](#8-tech-stack)
9. [Folder Structure](#9-folder-structure)
10. [Setup Instructions](#10-setup-instructions)
11. [Running the Project](#11-running-the-project)
12. [Running with Testcontainers](#12-running-with-testcontainers)
13. [API Documentation](#13-api-documentation)
14. [End-to-End Flow](#14-end-to-end-flow)
15. [Retry & Failure Handling](#15-retry--failure-handling)
16. [Data Model](#16-data-model)
17. [Intelligence Layer](#17-intelligence-layer)
18. [Metrics & Observability](#18-metrics-observability)
19. [How to Test the System](#19-how-to-test-the-system)
20. [Common Failure Cases & Debugging Guide](#20-common-failure-cases--debugging-guide)
21. [Configuration Reference](#21-configuration-reference)
22. [Security Considerations](#22-security-considerations)
23. [Scaling Considerations](#23-scaling-considerations)
24. [Future Roadmap](#24-future-roadmap)
25. [Contribution Guide](#25-contribution-guide)

---

## 1. Project Overview

**Keepr** is a modular monolith backend (single Spring Boot JAR) that lets households upload documents (invoices, warranty cards, receipts) and automatically extracts structured data from them to build a device and warranty inventory.

**Core value proposition:** Upload a photo of your invoice → Keepr reads it, identifies the product, brand, model, dates → creates a `Device` and `Warranty` record in your household inventory — all automatically.

**Architecture style:** Modular Monolith with strict module boundaries.  
**Tenancy model:** Multi-tenant via `household_id`. Every query is scoped to the authenticated user's household.  
**Authentication:** Stateless JWT with OTP-based login (phone number).

---

## 2. Mental Model

**Keepr is not a file upload system.**

It is: **A deterministic data extraction engine with auditability.**

Every document that enters the system is treated as a source of truth for downstream structured data. The system's job is to prove (via confidence scores and audit logs) that it has accurately translated paper/pixels into business entities.

---

## 3. Core Design Principles

These principles guide every architectural decision and code change:

- **Commit Fast, Process Outside**: Upload metadata and the job itself are committed to the DB instantly. The heavy lifting (OCR, Parsing) happens outside of active database transactions to keep the pool healthy.
- **Idempotency First**: The system must be safe to retry. Creating a device from an invoice twice should result in exactly one device record.
- **Fail Fast**: Oversized files, invalid types, or low-confidence results are rejected as early as possible in the pipeline.
- **Observability First**: "If it isn't logged, it didn't happen." Every stage of the pipeline (OCR, Parse, Validate) emits timing metrics and success/failure metadata.

---

## 4. Problem Statement

Indian consumers lose track of warranties and invoices because:

- Paper invoices get lost or damaged
- Digital invoices are scattered across Gmail, WhatsApp, and SMS
- There is no centralized system to track warranty expiry dates
- When a device breaks, finding the warranty card is a manual, frustrating process

**Keepr solves this** by providing a single upload endpoint. You upload any document (PDF, JPEG, PNG), and the system automatically:
1. Stores the document securely
2. Runs OCR to extract text
3. Parses the text into structured fields (product name, brand, dates, etc.)
4. Scores the extraction confidence
5. Validates the data against business rules
6. Creates `Device` and `Warranty` records in your household

---

## 5. Current Product State

### What Works Today (Sprint 6)

| Capability | Status |
|---|---|
| OTP-based authentication (send + verify) | ✅ Working |
| JWT token issuance and validation | ✅ Working |
| Document upload (PDF, JPEG, PNG) | ✅ Working |
| Server-side MIME type detection (Apache Tika) | ✅ Working |
| File size enforcement (10MB limit) | ✅ Working |
| Async extraction job queue (DB-backed polling) | ✅ Working |
| Stub OCR provider (local/test profiles) | ✅ Working |
| Rule-based text parsing (regex) | ✅ Working |
| Confidence scoring (weighted field scoring) | ✅ Working |
| Device validation (name required, confidence ≥ 0.5) | ✅ Working |
| Warranty validation (end ≥ start date) | ✅ Working |
| Idempotent device creation | ✅ Working |
| Exponential backoff retry (5s → 25s → 125s) | ✅ Working |
| Zombie job recovery (30-minute threshold) | ✅ Working |
| Per-job timing metrics (ocrMs, parseMs, validateMs) | ✅ Working |
| Confidence breakdown (per-field JSONB) | ✅ Working |
| Human review system for low-confidence extractions | ✅ Working |
| Manual review task management (GET/POST endpoints) | ✅ Working |
| JSONB extraction snapshots for manual correction | ✅ Working |
| Manual device CRUD | ✅ Working |
| Manual warranty creation | ✅ Working |
| Integration tests with Testcontainers | ✅ Working |
| Flyway database migrations (V1–V21) | ✅ Working |

### What Does NOT Exist Yet (Limitations)

| Missing Capability | Planned Sprint |
|---|---|
| Real OCR provider (Google Vision / Tesseract) | Sprint 7 |
| AI/LLM fallback parsing (Claude API) | Sprint 7 |
| Gmail / WhatsApp / SMS ingestion sources | Sprint 7+ |
| Push notifications for warranty expiry | Sprint 8+ |
| React Native mobile app | Sprint 9+ |
| AWS S3 file storage (currently local filesystem) | Sprint 7 |
| Multi-device invoice linking | Sprint 7+ |
| Rate limiting | Sprint 6+ |
| Redis Streams (currently using DB-backed polling) | Sprint 7+ |

---

## 6. High-Level Architecture

```
┌──────────────────────────────────────────────────────────┐
│                      CLIENT (Future)                      │
│              React Native (Expo) + TypeScript              │
└──────────────────────┬───────────────────────────────────┘
                       │ HTTPS + JWT
                       ▼
┌──────────────────────────────────────────────────────────┐
│                   SPRING BOOT 3.4.4                       │
│                  (Modular Monolith)                        │
│                                                           │
│  ┌─────────┐  ┌──────────┐  ┌───────────┐  ┌──────────┐ │
│  │  Auth   │  │  Device  │  │  Warranty  │  │Ingestion │ │
│  │ Module  │  │  Module  │  │  Module    │  │  Module  │ │
│  └────┬────┘  └────┬─────┘  └─────┬─────┘  └────┬─────┘ │
│       │            │              │              │        │
│       └────────────┴──────────────┴──────────────┘        │
│                           │                               │
│                    ┌──────┴──────┐                         │
│                    │   Common    │                         │
│                    │  (Security, │                         │
│                    │  Exception) │                         │
│                    └─────────────┘                         │
└────────────┬────────────────────────────┬────────────────┘
             │                            │
             ▼                            ▼
     ┌───────────────┐           ┌────────────────┐
     │ PostgreSQL 16 │           │    Redis 7      │
     │   (Primary)   │           │   (Cache/Queue) │
     └───────────────┘           └────────────────┘
```

---

## 7. Detailed Architecture Breakdown

### 6.1 Ingestion Pipeline

The ingestion pipeline is the core of Keepr. It processes uploaded documents in 3 phases:

```
UPLOAD PHASE (Synchronous)              PROCESSING PHASE (Async)
─────────────────────────              ──────────────────────────
                                       ExtractionWorker polls every 5s
Client ──POST /upload──►               ┌─────────────────────────────┐
  │                                    │ Phase A: Mark PROCESSING    │
  ▼                                    │ (REQUIRES_NEW transaction)  │
FileStorageService.store()             ├─────────────────────────────┤
  │ 1. Validate file size (≤10MB)      │ Phase B: Intelligence       │
  │ 2. MIME sniff (16KB Tika prefix)   │ (NO transaction)            │
  │ 3. Write to disk                   │  ├─ OCR → rawText           │
  ▼                                    │  ├─ Parse → ExtractionResult│
IngestionMetadataService               │  ├─ Confidence scoring      │
  │ (@Transactional)                   │  └─ Validation checks       │
  │ 1. Save RawDocument                ├─────────────────────────────┤
  │ 2. Create ExtractionJob (PENDING)  │ Phase C: Routing Decision   │
  ▼                                    │ (REQUIRES_NEW transaction)  │
Return {documentId, jobId, PENDING}    │  ├─ IF Low Confidence:      │
                                       │  │  └─ Create ReviewTask    │
                                       │  └─ ELSE:                   │
                                       │     └─ Create Device/Warr.  │
                                       └─────────────────────────────┘
```

**Why 3 phases?** To avoid holding database connections during long-running OCR/parsing I/O. Phase B runs entirely outside any database transaction.

### 6.2 Intelligence Layer (OCR, Parsing, Validation)

**OCR Layer:**
- `OcrProvider` interface decouples OCR implementations
- `StubOcrProvider` (active in `local`/`test` profiles) returns mock invoice text
- `OcrService` delegates to whichever `OcrProvider` bean is available
- Real providers (Google Vision, Tesseract) can be plugged in by implementing `OcrProvider`

**Parsing Engine (`ParsingService`):**
- Uses precompiled `Pattern` constants (no magic strings)
- Extracts 8 fields: productName, brand, model, category, purchaseDate, warrantyStart, warrantyEnd, warrantyType
- Returns a pure domain `ExtractionResult` record
- Throws `ExtractionException("EMPTY_OCR_TEXT")` if input is null/blank

**Confidence Scoring (`ConfidenceService`):**
- Weighted field scoring (0.0 → 1.0):

| Field | Weight |
|---|---|
| `product_name` | 0.3 |
| `brand` | 0.2 |
| `model` | 0.1 |
| `category` | 0.0 (informational) |
| `purchase_date` | 0.2 |
| `warranty_end` | 0.2 |

- Returns `ConfidenceResult` with: `totalScore`, `breakdown` (Map), `successfulFields`, `totalFields`

**Validation (`ValidationService`):**
- **Device validation (mandatory):** productName must be present AND confidence ≥ 0.5
- **Warranty validation (optional):** If both start and end dates exist, end must not be before start

### 6.3 Job Queue System

The system uses **database-backed polling** (not Redis Streams yet):

- `ExtractionWorker.pollAndProcess()` runs every **5 seconds** via `@Scheduled`
- Fetches up to **5 PENDING jobs** using `SELECT ... FOR UPDATE SKIP LOCKED` (prevents double-processing in multi-instance deployments)
- Filters eligible jobs by per-job backoff delay in Java before processing

### 6.4 Retry & Backoff System

```
Retry 0 (first attempt):  5-second delay
Retry 1:                   5-second delay
Retry 2:                  25-second delay
Retry 3:                 125-second delay (max retries reached → FAILED)
```

- **Transient failures** (system errors, timeouts): job goes back to `PENDING` with incremented `retryCount`
- **Validation failures** (`ExtractionException`): job immediately goes to `FAILED` (no retry)
- **Max retries exceeded** (≥ 3): job goes to `FAILED`, physical file is cleaned up from disk

### 6.5 Data Model

See [Section 16](#16-data-model) for complete table definitions.

---

## 8. Tech Stack

| Component | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.4 |
| Database | PostgreSQL | 16 |
| Cache/Queue | Redis | 7 |
| Migrations | Flyway | (managed by Spring Boot) |
| ORM | Hibernate / Spring Data JPA | (managed by Spring Boot) |
| Auth | JJWT | 0.12.6 |
| DTO Mapping | MapStruct | 1.6.3 |
| MIME Detection | Apache Tika | 2.9.2 |
| Code Generation | Lombok | (managed by Spring Boot) |
| Build Tool | Maven | 3.x (via `mvnw` wrapper) |
| Testing | JUnit 5 + Testcontainers | 1.21.4 |
| Code Style | Checkstyle | 10.21.2 |
| Containerization | Docker Compose | 3.8 |

---

## 9. Folder Structure

```
keepr/
├── AGENTS.md                    # AI agent rules and boundaries
├── SPRINT.md                    # Current sprint specification
├── SPRINT1.md ... SPRINT5.md    # Historical sprint docs
├── README.md                    # This file
└── keepr-backend/
    ├── docker-compose.yml       # PostgreSQL 16 + Redis 7 containers
    ├── pom.xml                  # Maven dependencies and plugins
    ├── mvnw / mvnw.cmd          # Maven wrapper (no global Maven needed)
    ├── checkstyle.xml           # Google Java Style enforcement
    └── src/
        ├── main/
        │   ├── java/com/keepr/
        │   │   ├── KeeprApplication.java      # Spring Boot entry point
        │   │   ├── auth/                       # Authentication module
        │   │   │   ├── controller/             # POST /auth/send-otp, /auth/verify-otp
        │   │   │   ├── dto/                    # SendOtpRequest, VerifyOtpRequest, AuthResponse
        │   │   │   ├── mapper/                 # MapStruct mappers
        │   │   │   ├── model/                  # User, Household, OTP entities
        │   │   │   ├── repository/             # JPA repositories
        │   │   │   └── service/                # AuthService, JwtService, OtpService
        │   │   ├── common/                     # Shared infrastructure
        │   │   │   ├── config/                 # SecurityConfig (JWT filter chain)
        │   │   │   ├── exception/              # GlobalExceptionHandler, KeeprException, ErrorCode
        │   │   │   ├── health/                 # GET /health endpoint
        │   │   │   └── security/               # JwtAuthFilter, KeeprPrincipal
        │   │   ├── device/                     # Device management module
        │   │   │   ├── controller/             # POST/GET /devices
        │   │   │   ├── dto/                    # CreateDeviceRequest, DeviceResponse
        │   │   │   ├── mapper/
        │   │   │   ├── model/                  # Device entity, DeviceCategory enum
        │   │   │   ├── repository/
        │   │   │   └── service/                # DeviceService, DeviceOwnershipService
        │   │   ├── ingestion/                  # Document ingestion & intelligence pipeline
        │   │   │   ├── controller/             # POST /api/v1/documents/upload, GET /jobs/{id}
        │   │   │   ├── dto/                    # UploadDocumentResponse, JobStatusResponse
        │   │   │   ├── exception/              # ExtractionException
        │   │   │   ├── mapper/
        │   │   │   ├── model/                  # ExtractionJob, RawDocument, JobStatus
        │   │   │   ├── repository/             # ExtractionJobRepository (FOR UPDATE SKIP LOCKED)
        │   │   │   └── service/                # 13 service classes (see below)
        │   │   ├── warranty/                   # Warranty management module
        │   │   │   ├── controller/             # POST /warranties
        │   │   │   ├── dto/                    # CreateWarrantyRequest, WarrantyResponse
        │   │   │   ├── model/                  # Warranty entity, WarrantyType enum
        │   │   │   ├── repository/
        │   │   │   └── service/                # WarrantyService
        │   │   └── notification/               # Notification module (placeholder)
        │   └── resources/
        │       ├── application.yml             # Base config (all profiles)
        │       ├── application-local.yml       # Local dev DB/Redis connection
        │       ├── application-test.yml        # Test profile (Testcontainers override)
        │       ├── application-prod.yml        # Production profile
        │       └── db/migration/               # 20 Flyway migration files (V1–V20)
        └── test/
            └── java/com/keepr/
                ├── AbstractIntegrationTest.java    # Singleton Testcontainers base class
                ├── KeeprApplicationSmokeTest.java  # Context load + health check tests
                ├── auth/                           # Auth integration tests
                ├── device/                         # Device integration tests
                └── ingestion/                      # Extraction pipeline integration tests
```

**Key ingestion services (13 files):**

| Service | Responsibility |
|---|---|
| `IngestionService` | Orchestrates upload: store file → save metadata → cleanup on failure |
| `IngestionMetadataService` | Atomic DB save of RawDocument + ExtractionJob (`@Transactional`) |
| `FileStorageService` | File I/O: MIME detection, size validation, disk storage, deletion |
| `ExtractionWorker` | `@Scheduled` polling loop + zombie recovery |
| `IngestionProcessingService` | 3-phase job processing orchestrator |
| `OcrService` | Delegates to the active `OcrProvider` implementation |
| `OcrProvider` | Interface for pluggable OCR backends |
| `StubOcrProvider` | Mock OCR returning sample invoice text (local/test only) |
| `ParsingService` | Regex-based text → `ExtractionResult` parsing |
| `ConfidenceService` | Weighted field confidence scoring |
| `ValidationService` | Business rule validation (device + warranty) |
| `ValidationResult` | Record: `{valid, reason}` with factory methods |
| `IngestionFailureService` | Handles failure: retry increment or FAILED + file cleanup |

---

## 10. Setup Instructions

### 9.1 Prerequisites

| Tool | Required Version | Check Command |
|---|---|---|
| Java JDK | 21+ | `java -version` |
| Docker | 20+ | `docker --version` |
| Docker Compose | v2+ | `docker compose version` |
| Git | Any | `git --version` |

> **Note:** Maven is NOT required globally. The project includes `mvnw` (Maven Wrapper) which downloads the correct Maven version automatically.

### 9.2 Clone the Repository

```bash
git clone <repository-url> keepr
cd keepr
```

### 9.3 Start Infrastructure (PostgreSQL + Redis)

```bash
cd keepr-backend
docker compose up -d
```

This starts:
- **PostgreSQL 16** on `localhost:5432` (database: `keepr`, user: `keepr`, password: `keepr_local`)
- **Redis 7** on `localhost:6379`

Verify containers are healthy:

```bash
docker compose ps
```

Both services should show `healthy` status.

### 9.4 Environment Variables

The project uses Spring profiles and `application.yml` files. **No `.env` file is needed for local development.** All defaults are built into the YAML configs.

### 9.5 application.yml Explanation

**`application.yml` (base config — applies to all profiles):**

```yaml
spring:
  application:
    name: keepr-backend        # Application name
  profiles:
    active: local              # Default active profile
  jpa:
    hibernate:
      ddl-auto: validate       # NEVER auto-creates tables; Flyway manages schema
    open-in-view: false        # Prevents lazy-loading outside transactions (best practice)
  flyway:
    enabled: true              # Auto-runs migrations on startup
    locations: classpath:db/migration
  servlet:
    multipart:
      max-file-size: 10MB      # Spring-level upload size limit
      max-request-size: 10MB

server:
  port: 8080                   # HTTP port

keepr:
  upload:
    dir: /tmp/keepr-uploads    # Physical file storage directory
    max-file-size: 10MB        # Application-level file size enforcement
  jwt:
    secret: keepr-local-dev-secret-key-must-be-at-least-32-chars-long
```

**`application-local.yml` (local profile — database connection):**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/keepr
    username: keepr
    password: keepr_local
  data:
    redis:
      host: localhost
      port: 6379
```

**`application-test.yml` (test profile — used by Testcontainers; values overridden dynamically):**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/keepr_test  # Overridden by Testcontainers
    username: keepr
    password: keepr_test
  jpa:
    hibernate:
      ddl-auto: validate
keepr:
  jwt:
    secret: test-secret-key-must-be-at-least-32-characters-long-for-hmac
```

### 9.6 Database Setup

**Automatic (recommended):** Flyway runs all 20 migrations automatically on application startup. No manual SQL execution needed.

**Manual verification:**

```bash
# Connect to PostgreSQL
docker exec -it keepr-postgres psql -U keepr -d keepr

# List all tables
\dt

# Check Flyway history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

### 9.7 Redis Setup

Redis starts automatically via Docker Compose. No additional configuration needed. Redis is used for caching and will be used for Redis Streams in future sprints.

---

## 11. Running the Project

### Step 1: Start Infrastructure

```bash
cd keepr-backend
docker compose up -d
```

### Step 2: Compile

```bash
./mvnw compile
```

### Step 3: Run the Application

```bash
./mvnw spring-boot:run
```

The application will:
1. Start on `http://localhost:8080`
2. Run all Flyway migrations automatically
3. Create the upload directory (`/tmp/keepr-uploads`)
4. Start the `ExtractionWorker` polling loop (every 5 seconds)
5. Start the zombie job recovery task (every 60 seconds)

### Step 4: Verify

```bash
curl http://localhost:8080/health
```

Expected response: `200 OK`

### Common Commands

| Task | Command |
|---|---|
| Compile | `./mvnw compile` |
| Run tests | `./mvnw test` |
| Run checkstyle | `./mvnw checkstyle:check` |
| Full build | `./mvnw clean compile` |
| Run specific test | `./mvnw test -Dtest=ExtractionIntegrationTest` |
| Check Flyway status | `./mvnw flyway:info` |

---

## 12. Running with Testcontainers

Integration tests use **Testcontainers** to spin up real PostgreSQL and Redis instances in Docker containers.

**How it works:**

1. `AbstractIntegrationTest` is a base class for all integration tests
2. It uses the **Singleton Container pattern**: PostgreSQL 16 and Redis 7 containers start once per JVM and are shared across all test classes
3. `@DynamicPropertySource` overrides `spring.datasource.*` and `spring.data.redis.*` with the container's dynamically assigned ports
4. `@DirtiesContext(classMode = AFTER_CLASS)` ensures each test class gets a clean Spring context

**Running:**

```bash
# Run all tests (Docker must be running)
./mvnw test

# Run only ingestion integration tests
./mvnw test -Dtest=ExtractionIntegrationTest
```

**Requirements:**
- Docker Desktop must be running
- First run will pull `postgres:16` and `redis:7` images (~500MB total)
- Subsequent runs reuse containers via `withReuse(true)`

---

## 13. API Documentation

### 12.1 Authentication

#### POST /auth/send-otp

Sends a 6-digit OTP to the given phone number.

```bash
curl -X POST http://localhost:8080/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+919876543210"}'
```

**Response (200 OK):**
```json
{"message": "OTP sent successfully"}
```

#### POST /auth/verify-otp

Verifies the OTP and returns a JWT token.

```bash
curl -X POST http://localhost:8080/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+919876543210", "otp": "123456"}'
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "a1b2c3d4-...",
  "householdId": "e5f6a7b8-..."
}
```

> **Note:** In local/test profiles, the OTP is stored in Redis and logged. Check application logs for the generated OTP.

### 12.2 Document Upload

#### POST /api/v1/documents/upload

Uploads a document for async extraction processing. Requires JWT authentication.

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "file=@/path/to/invoice.pdf"
```

**Accepted file types:** `application/pdf`, `image/jpeg`, `image/png`  
**Max file size:** 10MB

**Response (200 OK):**
```json
{
  "documentId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "jobId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "PENDING"
}
```

**Error Responses:**
- `400 Bad Request` — Unsupported file type or file too large
- `401 Unauthorized` — Missing or invalid JWT token

### 12.3 Job Status

#### GET /api/v1/documents/jobs/{jobId}

Checks the status of an extraction job. Scoped to the authenticated user's household.

```bash
curl http://localhost:8080/api/v1/documents/jobs/<JOB_ID> \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response (200 OK):**
```json
{
  "jobId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "COMPLETED",
  "errorMessage": null
}
```

**Possible `status` values:**

| Status | Meaning |
|---|---|
| `PENDING` | Waiting to be picked up by the worker |
| `PROCESSING` | Currently being processed (OCR + parsing) |
| `REVIEW_REQUIRED` | Low confidence or validation failure; needs human correction |
| `USER_CONFIRMED` | Job completed after human review and correction |
| `COMPLETED` | Successfully extracted and created Device/Warranty automatically |
| `FAILED` | Permanently failed after max retries or unrecoverable error |

### 12.4 Human Review System

#### GET /api/v1/review/tasks

Lists all pending review tasks for the authenticated household.

```bash
curl http://localhost:8080/api/v1/review/tasks \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response (200 OK):**
```json
[
  {
    "id": "e98e4f5a-...",
    "jobId": "7c9e6679-...",
    "status": "PENDING",
    "createdAt": "2024-01-15T10:00:00Z"
  }
]
```

#### GET /api/v1/review/tasks/{taskId}

Retrieves the full context for a specific review task, including the original OCR text and the extraction snapshot.

```bash
curl http://localhost:8080/api/v1/review/tasks/<TASK_ID> \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Response (200 OK):**
```json
{
  "id": "e98e4f5a-...",
  "jobId": "7c9e6679-...",
  "rawText": "Mock OCR Invoice Text...",
  "extractionJson": {
    "productName": "MacBook Pro",
    "brand": null,
    "model": "M3 Max"
  },
  "status": "PENDING",
  "createdAt": "2024-01-15T10:00:00Z"
}
```

#### POST /api/v1/review/tasks/{taskId}/confirm

Submits corrected data for a review task. Completes the task and creates the corresponding Device/Warranty.

```bash
curl -X POST http://localhost:8080/api/v1/review/tasks/<TASK_ID>/confirm \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "device": {
      "name": "Corrected Device Name",
      "brand": "Brand",
      "model": "Model",
      "category": "LAPTOP",
      "purchaseDate": "2024-01-15"
    },
    "warranty": {
      "type": "MANUFACTURER",
      "startDate": "2024-01-15",
      "endDate": "2025-01-15"
    }
  }'
```

**Response (200 OK):**
*Empty body (200 OK)*

### 12.5 Devices

#### POST /devices

```bash
curl -X POST http://localhost:8080/devices \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MacBook Pro",
    "brand": "Apple",
    "model": "M3 Max",
    "category": "LAPTOP",
    "purchaseDate": "2024-01-15"
  }'
```

#### GET /devices

```bash
curl http://localhost:8080/devices \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### 12.5 Warranties

#### POST /warranties

```bash
curl -X POST http://localhost:8080/warranties \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "<DEVICE_UUID>",
    "type": "MANUFACTURER",
    "startDate": "2024-01-15",
    "endDate": "2025-01-15"
  }'
```

---

## 14. End-to-End Flow

Here is every step from upload to entity creation:

```
Step 1:  Client sends POST /api/v1/documents/upload with a PDF file
         │
Step 2:  JwtAuthFilter extracts Bearer token, validates JWT,
         populates SecurityContext with KeeprPrincipal(userId, householdId, phone)
         │
Step 3:  IngestionController receives the multipart file
         │
Step 4:  IngestionService.uploadDocument() is called
         │
Step 5:  FileStorageService.store():
         ├─ Validates file size ≤ 10MB
         ├─ Reads 16KB prefix from the InputStream
         ├─ Detects MIME type using Apache Tika
         ├─ Rejects if not PDF/JPEG/PNG or octet-stream
         ├─ Generates UUID filename (e.g., "a1b2c3d4.pdf")
         └─ Writes file to /tmp/keepr-uploads/a1b2c3d4.pdf
         │
Step 6:  IngestionMetadataService.saveMetadata() [@Transactional]:
         ├─ Creates RawDocument (householdId, fileName, fileUrl, fileType, uploadedBy)
         ├─ Creates ExtractionJob (householdId, rawDocumentId, status=PENDING)
         └─ Returns UploadDocumentResponse {documentId, jobId, PENDING}
         │
Step 7:  Response returned to client: 200 OK with {documentId, jobId, PENDING}
         │
         ═══════════════ ASYNC BOUNDARY (5-second polling) ═══════════════
         │
Step 8:  ExtractionWorker.pollAndProcess() runs every 5s:
         ├─ SELECT * FROM extraction_jobs WHERE status='PENDING' FOR UPDATE SKIP LOCKED
         ├─ Filters eligible jobs by backoff delay
         └─ For each eligible job → calls IngestionProcessingService.processJob(jobId)
         │
Step 9:  IngestionProcessingService.processJob(jobId):
         │
         ├─ Phase A: markProcessing(jobId) [REQUIRES_NEW]
         │   └─ Sets job.status = PROCESSING, commits immediately
         │
         ├─ Phase B: Intelligence (NO transaction)
         │   ├─ Fetches RawDocument from DB
         │   ├─ OcrService.extractText(fileUrl) → rawText
         │   │   └─ StubOcrProvider returns mock invoice text
         │   ├─ ParsingService.parse(rawText) → ExtractionResult
         │   │   └─ Regex extraction of 8 fields
         │   ├─ ConfidenceService.calculateConfidence(result) → ConfidenceResult
         │   │   └─ Weighted scoring: totalScore, breakdown map
         │   ├─ ValidationService.validateDevice(result, confidence)
         │   │   └─ Must have productName AND confidence ≥ 0.5
         │   └─ ValidationService.validateWarranty(result)
         │       └─ If both dates present: end ≥ start
         │
         └─ Phase C: finalizeJob() [REQUIRES_NEW]
             ├─ Stores rawText, confidenceScore, extractionJson, breakdown, timing metrics
             ├─ DeviceService.createDeviceIngestion() → creates/finds Device
             │   └─ Idempotent: checks for existing device by name+brand+model+household
             ├─ WarrantyService.createWarrantyInternal() (if warranty is valid)
             ├─ Sets job.status = COMPLETED
             └─ Commits transaction
         │
Step 10: Client polls GET /api/v1/documents/jobs/{jobId}
         └─ Returns {jobId, COMPLETED, null}
```

---

## 15. Retry & Failure Handling

### Failure Classification

| Exception Type | Action | Retries? |
|---|---|---|
| `ExtractionException` (validation) | Immediate `FAILED` | No |
| Any other `Exception` (transient) | `PENDING` + retry | Yes (up to 3) |

### Retry Flow (IngestionFailureService)

```
Attempt 1 fails (transient error):
  → retryCount = 1, status = PENDING, backoff = 5s

Attempt 2 fails (transient error):
  → retryCount = 2, status = PENDING, backoff = 25s

Attempt 3 fails (transient error):
  → retryCount = 3, status = FAILED (max retries exceeded)
  → Physical file deleted from disk
```

### Validation Failure (No Retry)

```
Attempt 1 fails (ExtractionException: INVALID_DEVICE):
  → retryCount = 1, status = FAILED, failureReason = "INVALID_DEVICE"
  → Physical file deleted from disk
```

### Zombie Job Recovery

```
ExtractionWorker.recoverStaleJobs() runs every 60 seconds:
  → Finds jobs in PROCESSING state with updatedAt > 30 minutes ago
  → If retryCount + 1 >= 3: marks as FAILED
  → Otherwise: resets to PENDING with incremented retryCount
```

---

## 16. Data Model

### Core Tables

#### `users`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PRIMARY KEY |
| `phone_number` | VARCHAR(20) | UNIQUE, NOT NULL |
| `email` | VARCHAR(255) | |
| `name` | VARCHAR(255) | |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `is_active` | BOOLEAN | DEFAULT true |

#### `households`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PRIMARY KEY |
| `name` | VARCHAR(255) | NOT NULL |
| `owner_user_id` | UUID | NOT NULL, FK → users |
| `created_at` | TIMESTAMPTZ | NOT NULL |

#### `household_members`
| Column | Type | Constraints |
|---|---|---|
| `household_id` | UUID | PK, FK → households |
| `user_id` | UUID | PK, FK → users |
| `role` | VARCHAR(20) | CHECK IN ('OWNER', 'MEMBER') |
| `joined_at` | TIMESTAMPTZ | NOT NULL |

#### `devices`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PRIMARY KEY |
| `household_id` | UUID | NOT NULL, FK → households |
| `name` | VARCHAR(255) | NOT NULL |
| `brand` | VARCHAR(255) | |
| `model` | VARCHAR(255) | |
| `serial_number` | VARCHAR(255) | |
| `category` | VARCHAR(100) | NOT NULL |
| `purchase_date` | DATE | |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `updated_at` | TIMESTAMPTZ | NOT NULL |
| `deleted_at` | TIMESTAMPTZ | Soft delete |

#### `warranties`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PRIMARY KEY |
| `household_id` | UUID | NOT NULL, FK → households |
| `device_id` | UUID | NOT NULL, FK → devices |
| `invoice_id` | UUID | FK → invoices |
| `type` | VARCHAR(30) | CHECK IN ('MANUFACTURER', 'EXTENDED', 'AMC') |
| `provider` | VARCHAR(255) | |
| `start_date` | DATE | NOT NULL |
| `end_date` | DATE | NOT NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `deleted_at` | TIMESTAMPTZ | Soft delete |

### Ingestion Tables

#### `raw_documents`
| Column | Type | Constraints |
|---|---|---|
| `id` | UUID | PRIMARY KEY |
| `household_id` | UUID | NOT NULL |
| `file_name` | VARCHAR(255) | NOT NULL |
| `file_url` | VARCHAR(512) | NOT NULL |
| `file_type` | VARCHAR(50) | NOT NULL |
| `uploaded_by` | UUID | NOT NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `deleted_at` | TIMESTAMPTZ | Soft delete |

#### `extraction_jobs`
| Column | Type | Constraints | Added In |
|---|---|---|---|
| `id` | UUID | PRIMARY KEY | V13 |
| `household_id` | UUID | NOT NULL | V13 |
| `raw_document_id` | UUID | NOT NULL, FK → raw_documents | V13 |
| `status` | VARCHAR(50) | NOT NULL (PENDING/PROCESSING/COMPLETED/FAILED) | V13 |
| `retry_count` | INT | NOT NULL, DEFAULT 0 | V13 |
| `error_message` | TEXT | | V13 |
| `created_at` | TIMESTAMPTZ | NOT NULL | V13 |
| `updated_at` | TIMESTAMPTZ | NOT NULL | V13 |
| `deleted_at` | TIMESTAMPTZ | Soft delete | V13 |
| `raw_text` | TEXT | OCR output for audit | V16 |
| `confidence_score` | DOUBLE PRECISION | 0.0–1.0 | V16 |
| `extraction_json` | JSONB | Structured extraction snapshot | V17 |
| `failure_reason` | VARCHAR(255) | Machine-readable code | V17 |
| `extraction_version` | INT | NOT NULL, DEFAULT 1 | V18/V20 |
| `confidence_breakdown` | JSONB | Per-field scores | V18 |
| `ocr_ms` | INT | OCR stage duration in ms | V18 |
| `parse_ms` | INT | Parse stage duration in ms | V18 |
| `validate_ms` | INT | Validation stage duration in ms | V18 |
| `total_fields_extracted` | INT | Total fields in breakdown | V18 |
| `successful_fields` | INT | Fields with score > 0 | V18 |

#### `review_tasks`
| Column | Type | Constraints | Added In |
|---|---|---|---|
| `id` | UUID | PRIMARY KEY | V21 |
| `job_id` | UUID | NOT NULL, FK → extraction_jobs | V21 |
| `household_id` | UUID | NOT NULL, FK → households | V21 |
| `raw_text` | TEXT | OCR snapshot for review | V21 |
| `extraction_json` | JSONB | Data snapshot for correction | V21 |
| `status` | VARCHAR(20) | PENDING/COMPLETED | V21 |
| `created_at` | TIMESTAMPTZ | NOT NULL | V21 |
| `updated_at` | TIMESTAMPTZ | NOT NULL | V21 |

---

## 17. Intelligence Layer

### Parsing Engine

The `ParsingService` extracts fields from OCR text using precompiled regex patterns:

```
Pattern: "Device:\s*(.*)"       → productName
Pattern: "Brand:\s*(.*)"        → brand
Pattern: "Model:\s*(.*)"        → model
Pattern: "Category:\s*(.*)"     → category
Pattern: "Warranty Type:\s*(.*)"→ warrantyType
Pattern: "Purchase Date:\s*(\d{4}-\d{2}-\d{2})"  → purchaseDate
Pattern: "Warranty Start:\s*(\d{4}-\d{2}-\d{2})" → warrantyStart
Pattern: "Warranty End:\s*(\d{4}-\d{2}-\d{2})"   → warrantyEnd
```

All patterns are case-insensitive. Dates must be in `YYYY-MM-DD` format.

### Confidence Scoring

The scoring engine assigns weights to each field. A non-null, non-blank value gets the full weight; otherwise 0:

```
Total = product_name(0.3) + brand(0.2) + model(0.1) + purchase_date(0.2) + warranty_end(0.2)
Max possible score: 1.0
```

**Decision thresholds:**

| Score Range | System Behavior |
|---|---|
| ≥ 0.5 | Auto-create Device (and Warranty if valid) |
| < 0.5 | Job marked as FAILED (reason: LOW_CONFIDENCE) |
| Missing productName | Job marked as FAILED (reason: INVALID_DEVICE) |

### Confidence Breakdown Example

Stored as JSONB in `confidence_breakdown`:

```json
{
  "product_name": 0.3,
  "brand": 0.2,
  "model": 0.1,
  "category": 0.0,
  "purchase_date": 0.2,
  "warranty_end": 0.2
}
```

---

## 18. Metrics & Observability

### Structured Log Format

Every completed job emits a structured metrics log line:

```
[METRICS] jobId=<UUID> version=1 confidence=<0.0-1.0> status=<SUCCESS|FAILURE_REASON>
          totalMs=<N> ocrMs=<N> parseMs=<N> validateMs=<N>
```

### Key Log Events

| Event | Log Level | Message Pattern |
|---|---|---|
| Document uploaded | INFO | `Document uploaded & job created: jobId=..., householdId=...` |
| Worker picks up job | INFO | `Processing job=... retry=...` |
| OCR stub invoked | INFO | `Stub OCR provider extracting from: ...` |
| Parsing complete | INFO | `Parsing complete. Extracted: ...` |
| Validation failure | WARN | `Device validation failed for job ...: ...` |
| Job finalized | INFO | `Job ... finalized successfully (v1). Confidence: ...` |
| Retry scheduled | WARN | `Job ... failed (attempt N). Marking as PENDING for retry.` |
| Max retries hit | ERROR | `Job ... reached max retries (3). Marking as FAILED.` |
| Zombie recovery | WARN | `Recovered N stale processing jobs (older than 30 mins)` |
| Metrics summary | INFO | `[METRICS] jobId=... version=1 confidence=... totalMs=...` |

### Database Metrics

Per-job timing and quality metrics are persisted in `extraction_jobs`:
- `ocr_ms`, `parse_ms`, `validate_ms` — stage timing
- `confidence_score`, `confidence_breakdown` — extraction quality
- `total_fields_extracted`, `successful_fields` — field counts
- `extraction_version` — pipeline version for A/B testing

---

## 19. How to Test the System

### Quick Smoke Test (curl)

```bash
# 1. Start the system
cd keepr-backend
docker compose up -d
./mvnw spring-boot:run

# 2. Health check
curl http://localhost:8080/health

# 3. Send OTP
curl -X POST http://localhost:8080/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+919876543210"}'

# 4. Check application logs for the OTP code, then verify
curl -X POST http://localhost:8080/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+919876543210", "otp": "<OTP_FROM_LOGS>"}'
# Save the returned "token" value

# 5. Upload a document (any PDF/JPEG/PNG)
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@/path/to/any-invoice.pdf"
# Save the returned "jobId"

# 6. Wait 5-10 seconds for the worker to process, then check status
curl http://localhost:8080/api/v1/documents/jobs/<JOB_ID> \
  -H "Authorization: Bearer <TOKEN>"
# Should show status: "COMPLETED"

# 7. Verify device was created
curl http://localhost:8080/devices \
  -H "Authorization: Bearer <TOKEN>"
# Should show a device named "macbook pro" (from stub OCR)
```

### Running Integration Tests

```bash
# Ensure Docker is running
./mvnw test
```

---

## 20. Common Failure Cases & Debugging Guide

| Symptom | Cause | Fix |
|---|---|---|
| `401 Unauthorized` on all endpoints | Missing or expired JWT token | Re-authenticate via `/auth/verify-otp` |
| `400 Bad Request: Unsupported file type` | File is not PDF/JPEG/PNG (MIME detected by Tika, not extension) | Upload a real PDF, JPEG, or PNG file |
| `400 Bad Request: File too large` | File exceeds 10MB | Reduce file size |
| Job stuck in `PENDING` | Worker not running or backoff delay in effect | Check that `@Scheduled` is enabled; check logs for backoff |
| Job stuck in `PROCESSING` | Worker crashed mid-processing | Wait for zombie recovery (runs every 60s, threshold 30 min) |
| Job `FAILED` with `INVALID_DEVICE` | Product name not found in OCR text or confidence < 0.5 | Check `raw_text` column in `extraction_jobs` for OCR output quality |
| Job `FAILED` with `EMPTY_OCR_TEXT` | OCR returned null/blank | Check the file is not corrupt; check OcrProvider implementation |
| `Table not found` errors on startup | Flyway migration failed | Check `flyway_schema_history` table; run `./mvnw flyway:info` |
| `Connection refused` to PostgreSQL | Docker containers not running | Run `docker compose up -d` |
| `Port 5432 already in use` | Another PostgreSQL instance running | Stop the conflicting process or change the port in `docker-compose.yml` |
| Tests fail with `Could not connect to Docker` | Docker Desktop not running | Start Docker Desktop |

### Debugging Extraction Jobs

```sql
-- Connect to database
docker exec -it keepr-postgres psql -U keepr -d keepr

-- View all jobs with their status
SELECT id, status, retry_count, confidence_score, failure_reason, 
       ocr_ms, parse_ms, validate_ms, created_at, updated_at
FROM extraction_jobs 
ORDER BY created_at DESC 
LIMIT 20;

-- View raw OCR text for a specific job
SELECT raw_text FROM extraction_jobs WHERE id = '<JOB_UUID>';

-- View extraction result JSON
SELECT extraction_json FROM extraction_jobs WHERE id = '<JOB_UUID>';

-- View confidence breakdown
SELECT confidence_breakdown FROM extraction_jobs WHERE id = '<JOB_UUID>';

-- Check for stuck jobs
SELECT id, status, retry_count, updated_at 
FROM extraction_jobs 
WHERE status = 'PROCESSING' 
AND updated_at < NOW() - INTERVAL '30 minutes';
```

---

## 21. Configuration Reference

| Property | Default | Description |
|---|---|---|
| `spring.profiles.active` | `local` | Active Spring profile |
| `server.port` | `8080` | HTTP server port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/keepr` | PostgreSQL JDBC URL |
| `spring.datasource.username` | `keepr` | Database username |
| `spring.datasource.password` | `keepr_local` | Database password |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate schema strategy (NEVER set to `create`) |
| `spring.jpa.open-in-view` | `false` | Disables OSIV anti-pattern |
| `spring.flyway.enabled` | `true` | Enable automatic migrations |
| `spring.servlet.multipart.max-file-size` | `10MB` | Spring multipart limit |
| `keepr.upload.dir` | `/tmp/keepr-uploads` | File storage directory |
| `keepr.upload.max-file-size` | `10MB` | Application-level file limit |
| `keepr.jwt.secret` | (profile-specific) | HMAC signing key (≥32 chars) |

### Hardcoded System Constants

| Constant | Value | Location |
|---|---|---|
| Worker poll interval | 5,000 ms | `ExtractionWorker` |
| Worker batch size | 5 jobs | `ExtractionWorker` |
| Zombie recovery interval | 60,000 ms | `ExtractionWorker` |
| Stale job threshold | 30 minutes | `ExtractionWorker` |
| Max retries | 3 | `IngestionFailureService` |
| Min confidence threshold | 0.5 | `ValidationService` |
| MIME sniff buffer | 16,384 bytes | `FileStorageService` |
| Extraction version | 1 | `IngestionProcessingService` |

---

## 22. Security Considerations

| Concern | Implementation |
|---|---|
| **Authentication** | Stateless JWT via `JwtAuthFilter`. Token carries `userId`, `householdId`, `phoneNumber`. |
| **Multi-tenancy** | Every query includes `household_id` filter. No cross-household data access is possible. |
| **CSRF** | Disabled (stateless API, no cookies) |
| **Sessions** | `STATELESS` session policy — no server-side session storage |
| **File upload safety** | Server-side MIME detection via Apache Tika (ignores client-provided Content-Type) |
| **Path traversal** | `FileStorageService.delete()` validates file path is within upload directory |
| **Stub data in production** | `StubOcrProvider` restricted to `local` and `test` profiles via `@Profile` |
| **SQL injection** | Parameterized queries via Spring Data JPA |
| **Public endpoints** | Only `/auth/send-otp`, `/auth/verify-otp`, `/health` are unauthenticated |
| **Secret management** | JWT secret is profile-specific; must be overridden in production |

---

## 23. Scaling Considerations

| Dimension | Current State | Future Path |
|---|---|---|
| **Worker concurrency** | Single-threaded `@Scheduled` loop | Multiple worker instances safe via `FOR UPDATE SKIP LOCKED` |
| **File storage** | Local filesystem (`/tmp/keepr-uploads`) | AWS S3 (Sprint 7) |
| **Job queue** | PostgreSQL-backed polling | Redis Streams (planned) |
| **OCR** | In-process stub | External API (Google Vision) — remove from transaction scope |
| **Database connections** | Phase B runs outside transactions | Prevents connection pool exhaustion during OCR |
| **Horizontal scaling** | Safe — row-level locking prevents double-processing | Add more instances behind load balancer |
| **MIME detection** | Bounded 16KB read (not full file scan) | Already optimized |

---

## 24. Future Roadmap

| Sprint | Focus | Key Deliverables |
|---|---|---|
| 6 | Human Review | Routing logic, review tasks, correction APIs (Completed) |
| 7 | Real OCR + AI Fallback | Google Vision integration, LLM fallback parsing, S3 storage |
| 7 | Multi-source Ingestion | Gmail API, WhatsApp Business API, S3 storage migration |
| 8 | Notifications | Push notifications for warranty expiry, extraction completion |
| 9 | Mobile App (Phase 1) | React Native (Expo) + TypeScript, basic upload and inventory views |
| 10-12 | Advanced Intelligence | LLM fallback parsing (Claude API), multi-device invoice linking |
| 13-15 | Production Hardening | Rate limiting, Redis Streams migration, observability dashboards |

---

## 25. Contribution Guide

### Code Standards

- **Java Style:** Google Java Style (enforced via `checkstyle.xml`)
- **DTOs:** Use Java Records
- **Lombok:** Only `@Slf4j`, `@RequiredArgsConstructor`, `@Getter`/`@Setter` (entities only)
- **No wildcard imports**
- **No magic strings** — use constants
- **Javadoc required** for all public methods

### Module Architecture

```
controller → service → repository → model → dto → mapper
```

- No business logic in controllers
- No cross-module repository access
- Communication between modules only via service layer

### Git Workflow

1. Create a feature branch from `main`
2. Make changes following the coding standards
3. Run full verification:
   ```bash
   ./mvnw clean compile
   ./mvnw checkstyle:check
   ./mvnw test
   ```
4. Ensure all tests pass
5. Submit a pull request

### Adding a New Flyway Migration

- **NEVER** edit existing migration files
- Create a new file: `V{N+1}__description.sql`
- One migration per schema change
- Check current version: `./mvnw flyway:info`

### Adding a New OCR Provider

1. Implement the `OcrProvider` interface:
   ```java
   @Component
   @Profile("prod")
   public class GoogleVisionOcrProvider implements OcrProvider {
       @Override
       public String extractText(String fileUrl) {
           // Implementation
       }
   }
   ```
2. Use `@Profile` to control activation
3. The system will auto-select the available provider via Spring DI

---

## License

This project is proprietary software. All rights reserved.
