# 🛡️ SPRINT.md — Sprint 2: Authentication (Keepr)

## 🎯 Sprint Goal

Implement a **minimal, production-safe authentication system** using:

* OTP-based login (no passwords)
* JWT-based stateless authentication
* Automatic user + household provisioning

This sprint establishes the **identity layer** for Keepr.

---

## ⚠️ Core Principles (Inherited from Sprint 1)

From Sprint 1 foundation:

* **Database is the source of truth** (not Redis)
* **Strict multi-tenancy enforced at DB level**
* **No orphaned data allowed**
* **Idempotent and race-safe operations**

---

## 🧱 Scope

### ✅ Included

1. OTP generation and verification (DB-backed)
2. JWT access token generation & validation
3. User creation (if new)
4. Household creation (if new)
5. household_members entry (OWNER role)
6. AuthController (`/auth/send-otp`, `/auth/verify-otp`)
7. JwtAuthFilter
8. SecurityConfig (stateless JWT)

---

### ❌ Explicitly Excluded

* Refresh tokens
* Logout
* Rate limiting
* Third-party auth (Firebase, OAuth)
* Email/password login

---

## 🔐 OTP Architecture

### Source of Truth: **Database (`auth_otp` table)**

Redis may be used optionally for caching but:

> ❗ OTP correctness MUST NOT depend on Redis

---

## 📲 OTP Flow

### 1. Send OTP

* Input: phone number
* Normalize to 10-digit format
* Generate 6-digit OTP
* Store in `auth_otp` table:

  * phone_number
  * otp
  * expiry_time (10 min)
  * created_at
* Return success response

---

### 2. Verify OTP

* Normalize phone number
* Validate OTP format (6-digit numeric)
* Fetch latest OTP from DB
* Validate:

  * Exists
  * Not expired
  * Matches input
* Mark OTP as used OR delete it
* Proceed to authentication

---

## 👤 User Provisioning Logic

### New User

If phone not found:

1. Create `User`
2. Create `Household`
3. Insert into `household_members`:

   * role = OWNER

---

### Existing User

* Fetch user by phone number
* Fetch household via `owner_user_id`

---

## 🔁 Idempotency & Race Safety

### Must Handle:

* Duplicate OTP verification requests
* Concurrent user creation

### Rules:

* Use unique constraint on `users.phone_number`
* On insert failure:
  → re-fetch existing user
* OTP must be **single-use only**

---

## 🔑 JWT Requirements

* Stateless authentication
* Signed with HMAC SHA256
* Claims:

  * userId
  * householdId
  * phoneNumber
* Expiry: 15 minutes
* Allow small clock skew tolerance

---

## 🔒 Security Layer

### JwtAuthFilter

* Extract Bearer token
* Validate JWT
* Populate SecurityContext with `KeeprPrincipal`

### SecurityConfig

* Stateless (no sessions)
* Permit:

  * POST /auth/send-otp
  * POST /auth/verify-otp
  * GET /health
* All other endpoints require authentication

---

## 📦 Data Integrity Rules

* Phone number must always be normalized before use
* No raw phone stored anywhere
* OTP must always be 6 digits
* OTP must expire after 10 minutes
* OTP must be deleted or invalidated after use

---

## 🧪 Testing Requirements

### Integration Tests

1. sendOtp_validPhone_returns200
2. sendOtp_invalidPhone_returns400
3. sendOtp_phoneNormalisationWorks
4. verifyOtp_correctOtp_returnsAccessToken
5. verifyOtp_newUser_createsUserHouseholdAndMember
6. verifyOtp_existingUser_returnsIsNewUserFalse
7. verifyOtp_wrongOtp_returns401
8. verifyOtp_expiredOtp_returns401
9. verifyOtp_otpConsumedAfterSuccess
10. verifyOtp_otpStoredInDatabase
11. verifyOtp_concurrentRequests_onlyOneUserCreated
12. healthEndpoint_noAuth_returns200
13. protectedEndpoint_noJwt_returns401
14. protectedEndpoint_validJwt_returnsNotUnauthorized
15. protectedEndpoint_expiredJwt_returns401

---

### Unit Tests

* JwtService:

  * Token generation
  * Expiry validation
  * Invalid/tampered token handling

---

## 🧠 Design Decisions

### Why DB-based OTP?

* Survives restarts
* Auditable
* Consistent with DB-first architecture
* Avoids cache inconsistency bugs

---

### Why no refresh tokens?

* Keep system simple in early stage
* Reduce complexity in auth flows
* Can be added later in Sprint 4+

---

## 🚨 Acceptance Criteria

* User can log in via OTP
* New users are automatically provisioned
* JWT is issued and works for protected endpoints
* No duplicate users created
* OTP cannot be reused
* System is safe under concurrent requests
* All tests pass

---

## 📌 Output Expectations

At completion:

* All endpoints working
* All tests passing
* No violations of Sprint 1 constraints
* Code passes checkstyle
* System is ready for Sprint 3

---

## 🚀 What This Enables (Next Sprint)

Sprint 2 unlocks:

* Device registration
* Invoice ingestion
* Warranty tracking
* Notification system

👉 This is the foundation for all user-scoped data

---
