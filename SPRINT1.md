# 🏁 Sprint 1 Complete: Project Scaffold & Foundation

This document serves as an immutable historical record of everything achieved during Sprint 1. It provides deep context for future agents and engineers regarding the project's foundation, structural decisions, and incremental fixes applied before formally moving to Sprint 2.

## 🎯 High-Level Goal
Establish the strict modular monolith skeleton for the Keepr backend. No business logic, endpoints (except health check), or external API calls were permitted. The focus was entirely on architecture, data modeling, multi-tenancy enforcement, and developer tooling.

---

## 🏗️ Architecture & Project Setup

### Core Tech Stack
*   **Language:** Java 21
*   **Framework:** Spring Boot 3.4.4
*   **Database:** PostgreSQL 16 (managed via Flyway)
*   **Queue/Cache:** Redis 7 
*   **Mapping:** MapStruct (1.6.3)
*   **Testing:** Testcontainers (1.21.4)

### Package Structure 
Created a strict modular structure inside `com.keepr`. Each logical domain is heavily segregated:
*   `com.keepr.auth.*`
*   `com.keepr.device.*`
*   `com.keepr.extraction.*`
*   `com.keepr.ingestion.*`
*   `com.keepr.notification.*`

**Within each module, the following empty package layers were scaffolded:**
`controller`, `service`, `repository`, `model`, `dto`, `mapper`.
*(Rule: No cross-repository access. Services communicate strictly with other services).*

### Common Infrastructure
*   **Exceptions:** Created global error handling via `GlobalExceptionHandler` (`@RestControllerAdvice`), standard `KeeprException`, structured `ErrorResponse`, and an `ErrorCode` enum definition.
*   **Health:** Implemented `KeeprApplicationSmokeTest` testing contexts, and `HealthController` exposing `GET /health` (`{"status":"UP","sprint":1}`).
*   **Security:** Implemented `SecurityConfig` with `permitAll()` to allow unhindered scaffolding checks (to be updated in Sprint 2).
*   **Properties:** Configured base `application.yml` along with `application-local.yml`, `application-test.yml`, and an empty placeholder for `application-prod.yml`.

---

## 🗄️ Database Design & Flyway Migrations
The database schema was rigidly defined across 10 structured Flyway migrations.

### Core Philosophy Enforced
1.  **Multi-Tenancy First:** Every business entity (except base users) explicitly holds a `household_id`.
2.  **Referential Integrity:** Explicit constraints were audited and hardened. Deleting a parent cascade-deletes its children, maintaining a pristine state.

### Migration Manifest
*   **`V1__create_users_and_households.sql`:** `users`, `households`, `household_members`. Added `ON DELETE CASCADE` between households and users.
*   **`V2__create_devices.sql`:** `devices` linked to `household_id` with `ON DELETE CASCADE`.
*   **`V3__create_invoices.sql`:** `invoices` and the joining table `device_invoices` with cascading deletes.
*   **`V4__create_warranties.sql`:** `warranties` linking to `device_id`. *Follow-up Fix: Added direct `household_id` column to enforce top-level multi-tenancy. Added explicit `ON DELETE CASCADE/SET NULL` rules.*
*   **`V5__create_extraction_jobs.sql`:** `extraction_jobs`. *Follow-up Fix: Added direct `household_id` and cascading deletes.*
*   **`V6__create_notifications.sql`:** `notifications`. *Follow-up Fix: Added direct `household_id` and cascading deletes.*
*   **`V7__create_household_invites.sql`:** `household_invites`. Added cascading delete to `household_id`.
*   **`V8__create_user_notification_preferences.sql`:** `user_notification_preferences`. Added cascading delete to `user_id`.
*   **`V9__create_indexes.sql`:** Extensive B-Tree index creation for all foreign keys. *Follow-up Fix: Included indexes for all newly added direct `household_id` columns from the audit.* Verified unique index for `users.phone_number` to prevent duplicate account creation.
*   **`V10__create_auth_otp.sql`:** *(Pre-Sprint 2 Preparatory Work)* Created `auth_otp` table specifically for the upcoming OTP login flow, including the `idx_auth_otp_phone` performance index.

---

## 🛠️ Infrastructure & CI Tooling

*   **Docker Compose:** Set up local developer environment running `postgres:16` and `redis:7` with proper volumes and health-checks.
*   **Code Quality:** Configured the `maven-checkstyle-plugin` pointing to Google's Java Style Guide (`checkstyle.xml`, max 120 chars). Enforced on build.
*   **CI Pipeline:** Set up `.github/workflows/ci.yml`. On push to `main` or `develop`, it validates Java 21 compilation, passes Checkstyle rules, and runs Maven Tests.
*   **Smoke Testing:** Implemented `KeeprApplicationSmokeTest.java`. Instead of mocking, this test uses `Testcontainers` to spin up ephemeral Postgres and Redis instances. It validates that the Spring application context loads, Flyway migrations execute successfully against a real DB, and all dependency injection wiring is correct.

---

## 🚑 Notable Issues Resolved During Scaffold

**1. Docker Desktop & Testcontainers Compatibility Issue**
*   **Symptom:** The Maven test phase repeatedly failed returning a "Status 400 Payload" error from the Testcontainers Docker client.
*   **Root Cause:** Docker Desktop v29 introduced stricter API negotiation, requiring clients to use at least Docker API v1.44. The initial Testcontainers version (`1.20.4`) was too old. 
*   **Resolution:** Investigated underlying UNIX sockets (`docker.sock` vs `docker.raw.sock`) and upgraded the `testcontainers.version` property in `pom.xml` to `1.21.4`, perfectly aligning with modern Docker CLI constraints.

**2. Strict Multi-Tenancy & Integrity Audit**
*   **Symptom:** SPRINT.md constraints mandated all entities have `household_id` and no orphaned rows.
*   **Root Cause/Resolution:** Realized associative and subordinate tables (`warranties`, `extraction_jobs`, `notifications`) relied implicitly on their parents rather than possessing a direct `household_id` to guarantee tenant isolation. Modified `V1`-`V9` scripts to explicitly `ADD COLUMN household_id`, attach indexes, and append `ON DELETE CASCADE` uniformly across all relational keys manually to avert data leaks and orphaned DB rows. 

---

### Final Outcome ✅
The project is perfectly primed. The database schema strictly reflects the Keepr data hierarchy rules. Dependencies, local emulation layers, and automatic syntax verifications are complete.

**Context is fully captured. We are ready to begin Sprint 2.**
