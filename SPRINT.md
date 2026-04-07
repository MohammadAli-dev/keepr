# 📋 SPRINT.md — Project Keepr

## Active Sprint: 6
**Title:** Productization Layer — Human Review System
**Status:** 🔄 In Progress

---

## 🎯 Goal

Transform Keepr from a backend extraction engine into a usable, trustworthy
product system. The extraction pipeline works but users have no visibility
or control when extraction is wrong. This sprint introduces a human review
loop for low-confidence extractions and user-facing APIs to see, correct,
and confirm extracted data.

After this sprint: extraction is not perfect — but it is always correctable.
This is the first true product milestone.

---

## 📦 Scope

### What is IN this sprint
- `ReviewTaskStatus` enum: `PENDING`, `COMPLETED`
- `JobStatus` enum expanded: `REVIEW_REQUIRED`, `USER_CONFIRMED`
- `ReviewTask` entity and Flyway migration
- `ReviewService` with household-scoped task management
- `ReviewController` with three endpoints
- Pipeline update: low-confidence jobs route to review instead of failing
- `ReviewIntegrationTest` covering the full review lifecycle
- Structured logging at `[REVIEW_CREATED]` and `[REVIEW_CONFIRMED]` points
- Configurable confidence threshold

### What is NOT in this sprint
- Google Vision or any real OCR provider — `StubOcrProvider` remains the
  only OCR implementation. Real OCR belongs in Sprint 7.
- LLM integration
- Any frontend or mobile work
- WhatsApp or Gmail ingestion channels
- Notification system for review tasks
- Bulk or admin review tooling

---

## 🔐 Confidence Routing Rule

The confidence threshold must be externalized — never hardcoded:

```yaml
keepr:
  extraction:
    review-confidence-threshold: 0.5
```

Injected as:
```java
@Value("${keepr.extraction.review-confidence-threshold:0.5}")
private double reviewConfidenceThreshold;
```

Routing logic (additive — does not touch the existing success path):
```
if confidence < reviewConfidenceThreshold OR validation fails:
    → createReviewTask()
    → set job status = REVIEW_REQUIRED
    → log [REVIEW_CREATED]
    → return (do NOT call finalizeJob)

if confidence >= reviewConfidenceThreshold AND validation passes:
    → existing finalizeJob() call — UNCHANGED
```

The existing COMPLETED flow must not be modified in any way.

---

## 🗃️ New Database Table

### V{N}__create_review_tasks.sql

Before writing this migration, list all files in
`src/main/resources/db/migration/` and identify the highest version number.
Use N = that number + 1. Report the verified N in your plan output.

```sql
CREATE TABLE review_tasks (
    id              UUID PRIMARY KEY,
    job_id          UUID NOT NULL REFERENCES extraction_jobs(id),
    household_id    UUID NOT NULL REFERENCES households(id),
    raw_text        TEXT NOT NULL,
    extraction_json JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'COMPLETED')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_review_tasks_household_status
    ON review_tasks(household_id, status)
    WHERE status = 'PENDING';

CREATE INDEX idx_review_tasks_job_id
    ON review_tasks(job_id);
```

---

## extraction_json contract

The `extraction_json` column stores the direct JSON serialization of
`ExtractionResult` from `ConfidenceService`. It must NOT be a custom
structure invented for this sprint.

Serialize using:
```java
String extractionJson = objectMapper.writeValueAsString(extractionResult);
```

This ensures Sprint 7's LLM layer can consume the same shape without
a migration or schema translation step.

The field names and structure are whatever `ExtractionResult` currently
produces — do not define a separate schema.

---

## 📁 Files to Create / Modify

```
src/main/java/com/keepr/
├── ingestion/
│   ├── model/
│   │   └── JobStatus.java                  ← MODIFY (add 2 values)
│   └── service/
│       └── IngestionProcessingService.java ← MODIFY (routing only)
├── review/                                 ← NEW MODULE
│   ├── controller/
│   │   └── ReviewController.java
│   ├── service/
│   │   ├── ReviewService.java              ← interface
│   │   └── impl/
│   │       └── ReviewServiceImpl.java
│   ├── repository/
│   │   └── ReviewTaskRepository.java
│   ├── model/
│   │   ├── ReviewTask.java
│   │   └── ReviewTaskStatus.java           ← enum
│   └── dto/
│       ├── ReviewTaskSummary.java          ← Record
│       ├── ReviewTaskResponse.java         ← Record
│       └── ConfirmReviewRequest.java       ← Record

src/main/resources/
├── application-local.yml                  ← MODIFY
├── application-test.yml                   ← MODIFY
└── db/migration/
    └── V{N}__create_review_tasks.sql      ← NEW (verify N first)

src/test/java/com/keepr/
└── review/
    └── ReviewIntegrationTest.java         ← NEW
```

No new Maven dependencies in this sprint.

---

## 🧱 Key Implementation Rules

### ReviewTaskStatus enum
```java
public enum ReviewTaskStatus {
    PENDING,
    COMPLETED
}
```

Use `@Enumerated(EnumType.STRING)` on the entity field. This provides
compiler safety and prevents typos that a bare `String status` would not catch.

### confirmTask — Idempotency (critical)

The confirm flow MUST use the existing `DeviceService.createDevice()` path.
Do NOT call `DeviceRepository` directly. Do NOT skip idempotency.

If a user double-confirms, the existing idempotency check
(`findByNameAndBrandAndModelAndHouseholdId`) in `DeviceService` prevents
a duplicate device. The second confirm call still receives a 409 because
the `ReviewTask.status` will already be `COMPLETED` — both protections
work together.

Confirm sequence (all steps in one `@Transactional` method):
```
1. findByIdAndHouseholdId → 404 if absent
2. if status == COMPLETED → throw KeeprException(VALIDATION_ERROR,
   "Review task already completed") → produces 400
3. Validate request.device() is not null and name is not blank
4. Call deviceService.createDevice(request.device(), householdId)
5. If request.warranty() is not null: call warrantyService.createWarranty()
6. task.setStatus(ReviewTaskStatus.COMPLETED), save
7. Load ExtractionJob by task.jobId(), set status = USER_CONFIRMED, save
8. log.info("[REVIEW_CONFIRMED] taskId={} householdId={}", taskId, householdId)
```

### Pipeline change — additive only

The change to `IngestionProcessingService` must be a pure addition.
The existing success path (`finalizeJob`) must not be touched.
Only add the confidence check and early return before the existing call.

---

## ✅ Acceptance Criteria

- [ ] `JobStatus` has `REVIEW_REQUIRED` and `USER_CONFIRMED`
- [ ] `ReviewTaskStatus` enum exists with `PENDING` and `COMPLETED`
- [ ] Migration runs cleanly with verified version number
- [ ] Both indexes on `review_tasks` are created
- [ ] `extraction_json` is direct serialization of `ExtractionResult`
- [ ] Confidence threshold injected via `@Value` — not hardcoded
- [ ] No new Maven dependencies added
- [ ] `GET /api/v1/review/tasks` returns only PENDING tasks for household
- [ ] `GET /api/v1/review/tasks/{id}` returns 404 for wrong household
- [ ] `POST /confirm` creates device using existing `DeviceService` idempotency
- [ ] Double-confirm returns 400
- [ ] `[REVIEW_CREATED]` and `[REVIEW_CONFIRMED]` log lines emitted
- [ ] Existing COMPLETED pipeline flow unchanged — no regressions
- [ ] All 8 `ReviewIntegrationTest` cases pass
- [ ] All existing tests still pass
- [ ] `./mvnw checkstyle:check` zero violations
- [ ] No repository injected into any controller

---

## 🔗 Sprint 7 Dependency Note

Sprint 7 (LLM layer) will read `ReviewTask.extractionJson` to feed
correction prompts. The schema produced in this sprint is load-bearing —
do not change `ExtractionResult` structure between now and Sprint 7.
