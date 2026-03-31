# 🛠️ Sprint 3 Complete: Device & Warranty Foundation

This document serves as the official record of the implementation of the Device and Warranty module. This sprint focused on building the first usable product experience while enforcing strict multi-tenancy and data integrity.

---

## 🎯 Sprint Goal
Enable users to:
1. Add/Create devices within their household.
2. List devices (ordered newest-first) belonging to their household.
3. Attach warranties (Manufacturer, Extended, AMC) to specific devices.
4. Ensure absolute tenant isolation (no cross-household access).

---

## 🏗️ Architectural Decisions & Implementation

### 1. Multi-Tenancy & Security
*   **Principal-First Strategy:** The `household_id` is never accepted from the request body. It is extracted exclusively from the `KeeprPrincipal` in the `SecurityContext`.
*   **Security Posture (404 vs 403):** To prevent IDOR (Insecure Direct Object Reference) and resource enumeration attacks, a `404 Not Found` is returned if a user attempts to attach a warranty to a device belonging to a different household.
*   **Strict JPA Constraints:** Every required field in the `devices` and `warranties` tables is explicitly mapped with `@Column(nullable = false)` to ensure the entity layer aligns with the database schema.

### 2. Data Integrity & Lifecycle
*   **Explicit API Contracts:** Fields required by the DB (like `category` for devices and `type` for warranties) were included as mandatory inputs in the API DTOs rather than being silently defaulted, ensuring business data is accurately captured from the client.
*   **Timestamp Management:** The `Device` entity implements both `@PrePersist` and `@PreUpdate` lifecycle hooks to manage `createdAt` and `updatedAt` timestamps automatically.

### 3. UX & Performance
*   **Deterministic Ordering:** All list operations (e.g., `findAllByHouseholdIdOrderByCreatedAtDesc`) ensure a consistent, reverse-chronological user experience and prevent flaky test behavior.
*   **Indexing:** Verified that `V9` migrations already provide the necessary indexes on `household_id` for both `devices` and `warranties` to maintain performance at scale.

---

## 📦 Delivered Components

### 1. Entities (`com.keepr.device/warranty.model`)
*   `Device`: Maps to `devices`. Includes `name`, `brand`, `model`, `category`, `purchaseDate`, and timestamps.
*   `Warranty`: Maps to `warranties`. Includes `deviceId`, `type`, `startDate`, `endDate`.

### 2. DTOs (`com.keepr.device/warranty.dto`)
*   `CreateDeviceRequest` / `DeviceResponse`
*   `CreateWarrantyRequest` / `WarrantyResponse`

### 3. Services (`com.keepr.device/warranty.service`)
*   **`DeviceService`**: Validates `name`/`category` presence and ensures `purchaseDate` is not in the future.
*   **`WarrantyService`**: Validates warranty type (`MANUFACTURER`, `EXTENDED`, `AMC`) and ensures `endDate >= startDate`.

### 4. Controllers (`com.keepr.device/warranty.controller`)
*   `DeviceController`: `POST /devices`, `GET /devices`.
*   `WarrantyController`: `POST /warranties`.

---

## 🧪 Testing & Validation

A comprehensive 11-test suite (`DeviceWarrantyIntegrationTest`) was added, running against real Postgres/Redis via Testcontainers.

**Key Verified Scenarios:**
*   **Isolation:** Users cannot see or interact with devices from other households.
*   **Ordering:** Device lists arrive in reverse chronological order.
*   **Validation:** Future dates and invalid warranty types are rejected with `400 Bad Request`.
*   **Empty State:** Correctly handles households with zero devices.
*   **Security:** Unauthorized requests to new endpoints yield `401 Unauthorized`.

**Metrics:**
*   `./mvnw checkstyle:check` -> **0 Violations**
*   `./mvnw test` -> **29/29 Passed (100% Green State)**

---

### Final Outcome ✅
Sprint 3 successfully establishes the foundational product data model. Users now have a secure, authenticated identity (Sprint 2) that they can use to manage their personal device inventory and warranty coverage. 

**Ready for Sprint 4 (Invoice Ingestion & Extraction).**
