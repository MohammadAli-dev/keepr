# 🛡️ SPRINT.md — Sprint 3: Device & Warranty Foundation (Keepr)

## 🎯 Sprint Goal

Enable users to:

1. Add devices
2. Attach warranties
3. View their devices scoped to their household

👉 This creates the **first usable product experience**

---

## ⚠️ Core Principles (Inherited)

* DB is source of truth
* Strict multi-tenancy (household_id everywhere)
* No orphaned data
* Stateless JWT auth enforced
* Services communicate via service layer only

---

## 🧱 Scope

### ✅ Included

1. Device CRUD (create + list)
2. Warranty creation (manual entry only)
3. Household-scoped access control
4. Device ↔ Warranty linkage
5. Basic validation

---

### ❌ Excluded

* Invoice parsing (Sprint 4)
* OCR / AI extraction
* Notifications
* Editing/deleting devices
* File uploads

---

## 📦 Data Model

### Device

| Field         | Type          |
| ------------- | ------------- |
| id            | UUID          |
| household_id  | UUID          |
| name          | String        |
| brand         | String        |
| model         | String        |
| purchase_date | LocalDate     |
| created_at    | LocalDateTime |

---

### Warranty

| Field        | Type          |
| ------------ | ------------- |
| id           | UUID          |
| device_id    | UUID          |
| household_id | UUID          |
| start_date   | LocalDate     |
| end_date     | LocalDate     |
| created_at   | LocalDateTime |

---

## 🔗 Relationships

* 1 Household → Many Devices
* 1 Device → Many Warranties
* Warranty MUST always belong to same household as device

---

## 🔐 Access Control Rules

From JWT:

```java
KeeprPrincipal(userId, householdId, phoneNumber)
```

👉 Every query MUST filter by:

```sql
WHERE household_id = :householdId
```

❗ Never trust client input for household_id

---

## 🧠 Business Rules

### Device Creation

* Must belong to authenticated household
* Name required
* Purchase date cannot be in future

---

### Warranty Creation

* Must reference an existing device
* Device must belong to same household
* end_date ≥ start_date

---

## 📡 API Endpoints

### Device

#### POST /devices

Create new device

Request:

```json
{
  "name": "AC",
  "brand": "LG",
  "model": "DualCool",
  "purchaseDate": "2024-06-01"
}
```

Response:

```json
{
  "deviceId": "...",
  "name": "...",
  "brand": "...",
  "model": "...",
  "purchaseDate": "..."
}
```

---

#### GET /devices

List all devices for household

---

### Warranty

#### POST /warranties

Create warranty for device

Request:

```json
{
  "deviceId": "...",
  "startDate": "2024-06-01",
  "endDate": "2025-06-01"
}
```

---

## 🧪 Testing Requirements

### Integration Tests

1. createDevice_valid_returns200
2. createDevice_futurePurchaseDate_returns400
3. listDevices_returnsOnlyHouseholdDevices
4. createWarranty_valid_returns200
5. createWarranty_invalidDate_returns400
6. createWarranty_deviceNotFound_returns404
7. createWarranty_crossHouseholdDevice_returns403
8. protectedEndpoints_requireAuth
9. devicesScopedToHousehold_correctly

---

## 🚨 Acceptance Criteria

* User can create device
* User can list devices
* User can attach warranty
* Data is strictly household-scoped
* No cross-tenant leakage possible
* All tests pass
* Checkstyle passes

---

## 🚀 What This Unlocks

* First real product value
* Foundation for:

  * Invoice ingestion (Sprint 4)
  * Warranty reminders (Sprint 5)
  * Notifications

---
