# 🔒 Sprint 2 Complete: Minimal Working Authentication

This document serves as an immutable historical record of everything achieved during Sprint 2. It captures the implementation of the Keepr identity layer, specifically focusing on the strict database-first OTP validation and stateless JWT issuance.

## 🎯 High-Level Goal
Implement a minimal, production-safe authentication system utilizing OTP-based login (no passwords) and JWTs for stateless session management. Following Sprint 1's philosophy, the database remains the absolute source of truth, and strict multi-tenancy rules (household-first design) govern all user provisioning.

---

## 🏗️ Architectural Decisions & Constraints Conquered

### 1. Database-First OTP Strategy
Despite Redis being available in the stack, **Redis was explicitly rejected for OTP storage**.
*   **Source of Truth:** All OTP states are maintained in the PostgreSQL `auth_otp` table (created in Sprint 1's `V10` migration).
*   **Reasoning:** Ensures persistence across application restarts, establishes a fully auditable trail, and aligns completely with the DB-first architecture.

### 2. Idempotency & Race Condition Hardening
Authentication endpoints are highly susceptible to race conditions (e.g., users double-tapping "verify"). We implemented robust defenses:
*   **Pessimistic Locking:** OTP fetching utilizes `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`) to prevent concurrent verification attempts from processing the same code.
*   **Immediate Deletion Timing:** The `auth_otp` row is hard-deleted *immediately* upon successful verification, *before* moving to the heavy user/household creation phase. This completely negates retry exploitability.
*   **Provisioning Race Safety:** If two threads attempt to create a user for the same phone number simultaneously, the unique constraint on `users.phone_number` throws a `DataIntegrityViolationException`. The system elegantly catches this and falls back to fetching the newly created existing user.

### 3. Entity & JPA Simplification
*   **No `HouseholdMember` Entity:** To avoid the JPA complexity of mapping composite keys during the high-throughput authentication flow, the `household_members` relationship is inserted cleanly and atomically via an `EntityManager.createNativeQuery(...)` execution.

### 4. Zero "Starter" Bloat
*   No `spring-boot-starter-validation` was added. All strict formatting rules (e.g., OTP must be exactly 6 numeric digits, strict phone formatting) are enforced manually within the boundary layers of `AuthService` rather than relying on heavy reflection annotations.

---

## 📦 Implementation Details

### Dependencies Added
*   `io.jsonwebtoken:jjwt-api:0.12.6`
*   `io.jsonwebtoken:jjwt-impl:0.12.6` (runtime)
*   `io.jsonwebtoken:jjwt-jackson:0.12.6` (runtime)

### Core Components Scaffolded

#### 1. Entity Models (`com.keepr.auth.model`)
*   `User`: Maps to `users`. Features `phone_number` standardizations.
*   `Household`: Maps to `households`.
*   `AuthOtp`: Maps to `auth_otp`.

#### 2. Services (`com.keepr.auth.service`)
*   **`OtpService`:** Strictly dedicated to generating random 6-digit codes and interacting with the `AuthOtpRepository`. Enforces logging hygiene by masking phone numbers (`******3210`) to prevent leaking PII.
*   **`JwtService`:** Handles JWT generation and parsing using JJWT 0.12.6.
    *   Uses HMAC SHA256.
    *   Defensively throws a startup exception if the configured secret key is less than 32 characters.
    *   Enforces a strict 15-minute token expiry.
    *   Configures a 60-second clock skew tolerance.
*   **`AuthService`:** The core orchestrator. Normalizes phone numbers exactly *once* at the boundary (stripping country codes to a standard 10 digits). Transitions from OTP validation to user auto-provisioning smoothly.

#### 3. Security Layer (`com.keepr.common.security`)
*   **`KeeprPrincipal`:** Modeled as a pure Java `record`. Explicitly avoids implementing Spring Security's bloated `UserDetails` interface since the architecture is strictly stateless JWT.
*   **`JwtAuthFilter`:** Extends `OncePerRequestFilter` to intercept `Bearer` tokens, parse them via `JwtService`, and statically populate the generic `SecurityContextHolder`.
*   **`SecurityConfig`:** Updated from Sprint 1's `permitAll()`. Now correctly configures a stateless session policy, provides an explicit `AuthenticationEntryPoint` mapping unauthenticated attempts to raw `401 Unauthorized` responses (rather than Spring's default 403), and permits exactly three paths: `POST /auth/send-otp`, `POST /auth/verify-otp`, and `GET /health`.

#### 4. Controller (`com.keepr.auth.controller`)
*   `AuthController`: Exposes the public REST endpoints accepting standard record DTOs (`SendOtpRequest`, `VerifyOtpRequest`).

---

## 🧪 Testing & Validation

A comprehensive 15-test integration suite (`AuthControllerIntegrationTest`) was constructed utilizing Testcontainers, proving the system end-to-end against a real PostgreSQL instance.

**Verified Behaviors:**
*   Phone normalization happens correctly (`+91-9876543210` becomes `9876543210`).
*   New users are successfully auto-provisioned alongside a new `Household` and `HouseholdMember` owner mapping.
*   Existing users correctly map to their existing household and receive `isNewUser: false`.
*   Expired OTPs (-1 minute) are correctly purged and rejected.
*   Wrong OTPs throw 401s.
*   OTPs are consumed immediately (second attempt with the same OTP fails).
*   Concurrent verification requests successfully yield exactly one user without crashing.
*   Invalid JWTs and expired JWTs correctly throw 401s.

**Quality Metrics:**
*   `./mvnw checkstyle:check` -> **0 Violations**
*   `./mvnw test` -> **18/18 Passed (including Smoke Tests)**

---

### Final Outcome ✅
The Keepr identity layer is fully operational. We have securely automated the user provisioning and household tenancy assignment lifecycle without relying on bloated sessions or cache layers.

**Context is fully captured. We are ready to begin Sprint 3 (Device + Warranty APIs).**
