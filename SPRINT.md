# 📋 SPRINT.md — Project Keepr

## Active Sprint: 4

**Title:** Invoice Ingestion & Extraction (Async, DB-Queue Based)
**Status:** 🔄 In Progress

---

## 🎯 Goal

Enable users to upload invoices/documents and process them asynchronously into structured data.

This sprint introduces:

* Document ingestion
* Background processing
* Basic extraction pipeline (stubbed OCR + parsing)

---

## 🧠 Core Principle

> Upload fast. Process later.

---

## ⚙️ High-Level Flow

Upload → Save Raw Document → Create Job → Return Response
→ Background Worker → Process → Update Status

---

## 📦 Scope

### INCLUDED

* RawDocument entity
* ExtractionJob entity
* Upload API
* Background worker (DB queue)
* Job state machine
* Basic OCR + parsing (stub implementation)
* Linking parsed data → Device/Warranty (basic)

---

### EXCLUDED

* Real OCR integration (use stub)
* Gmail/WhatsApp sync
* Advanced parsing logic
* Notifications
* Retry queues (basic retry only)

---

## 🧱 Data Model

---

### 1. RawDocument

```id="t3p9fa"
id
household_id
file_name
file_url
file_type
uploaded_by
created_at
```

---

### 2. ExtractionJob

```id="j5o7df"
id
household_id
raw_document_id
status (PENDING, PROCESSING, COMPLETED, FAILED)
retry_count
error_message
created_at
updated_at
```

---

## 🔄 Job State Machine

```id="o0pfjq"
PENDING → PROCESSING → COMPLETED
                  ↓
                FAILED
```

---

## 🔁 Retry Logic

* Max retries: 3
* On failure:

  * Increment retry_count
  * If retry_count < 3 → retry
  * Else → mark FAILED

---

## 🌐 APIs

---

### 1. Upload Document

POST /api/v1/documents/upload

* Accept multipart file
* Store file (local or S3 stub)
* Create RawDocument
* Create ExtractionJob (PENDING)

Response:

```json id="3k6fh2"
{
  "documentId": "UUID",
  "jobId": "UUID",
  "status": "PENDING"
}
```

---

### 2. Get Job Status

GET /api/v1/jobs/{jobId}

Response:

```json id="8cl6jz"
{
  "jobId": "UUID",
  "status": "PROCESSING",
  "errorMessage": null
}
```

---

## 🧠 Worker Design

* Spring @Scheduled job
* Runs every 3–5 seconds
* Picks jobs:

```sql id="7g7m2f"
SELECT * FROM extraction_jobs
WHERE status = 'PENDING'
ORDER BY created_at
LIMIT 5
FOR UPDATE SKIP LOCKED
```

---

### Processing Steps

1. Mark job → PROCESSING
2. Fetch RawDocument
3. Run OCR (stub)
4. Run parsing (stub)
5. Create:

   * Device (if new)
   * Warranty (if found)
6. Mark job → COMPLETED

---

### Failure Handling

* Catch exception
* Increment retry_count
* Store error_message
* Retry or mark FAILED

---

## 🧪 Testing

### Required Tests

* Upload creates job
* Worker picks job
* Job transitions correctly
* Failed job retries
* Completed job creates device/warranty
* Multi-tenancy enforced

---

## ⚠️ Constraints

* No processing inside API
* All jobs must include household_id
* Worker must be idempotent
* No duplicate device creation (basic check)
* Follow AGENTS.md strictly

---

## 🏁 Exit Criteria

* Upload API works
* Jobs created correctly
* Worker processes jobs
* Status updates correctly
* Data saved in DB
* Tests pass
* No cross-tenant leakage

---

## 🚀 Outcome

Keepr can now:

* Accept real-world documents
* Process them asynchronously
* Convert unstructured data → structured system

This is the first step toward an intelligent system.
