# 🛡️ AGENTS.md — Project Keepr (2026)

---

## 🎯 Project Vision

Keepr is a high-trust home warranty and invoice tracking ecosystem for Indian consumers.
It transforms messy document data (Gmail, WhatsApp, SMS, camera scans, uploads) into a
structured household device and warranty inventory through an async multi-step extraction pipeline.

* **Architecture:** Modular Monolith (single Spring Boot JAR, strict module boundaries)
* **Methodology:** 15-sprint delivery
* **Current Sprint:** Refer to SPRINT.md (STRICT scope enforcement)
* **Security Pillar:** Every query MUST include `household_id` from authenticated user

---

## 🏗️ Data Model Hierarchy (STRICT)

```
users → households → devices → warranties
                   → invoices → extraction_jobs
users → household_members
users → notifications
```

### Rules:

* Device = user-owned instance (NOT a generic product)
* Warranty ALWAYS links to a device
* Invoice can map to multiple devices
* Household is the core tenancy boundary

---

## 🛠️ Tech Stack & Environment

### Backend

* Java 21, Spring Boot 3.x
* PostgreSQL 16 (Flyway)
* Redis 7 (**Redis Streams for queues only**)
* AWS S3 (file storage)
* Google Vision API
* WhatsApp Business API
* Claude API (fallback)

### Frontend

* React Native (Expo)
* TypeScript (strict)
* Zustand (state only)
* NativeWind (UI)

---

## 🚀 Sacred Commands

| Layer   | Task       | Command                                          |
| ------- | ---------- | ------------------------------------------------ |
| Backend | Compile    | `./mvnw compile`                                 |
| Backend | Test       | `./mvnw test`                                    |
| Backend | Checkstyle | `./mvnw checkstyle:check`                        |
| Backend | Run        | `docker-compose up -d && ./mvnw spring-boot:run` |

---

## 🎨 Coding Standards

### 1. Java Style

* Use **Records** for DTOs
* Lombok ONLY:

  * `@Slf4j`
  * `@RequiredArgsConstructor`
* Follow Google Java Style
* Javadoc required for all public methods
* No wildcard imports
* No magic strings

---

### 2. Modular Architecture

Each module:

```
controller → service → repository → model → dto → mapper
```

Rules:

* No business logic in controllers
* No repository access across modules
* Communication ONLY via service layer

---

### 3. Async Processing (CRITICAL)

* NEVER use:

  * `@Async`
  * `CompletableFuture`
* ALL heavy processing must go through:

  * Redis Streams → worker

Exception:

* Allowed ONLY for non-critical notifications

---

### 4. Multi-Tenancy (MANDATORY)

* Every entity (except users) MUST include `household_id`
* Every query MUST filter by `household_id`
* No cross-household data leakage allowed

---

### 5. Idempotency

* Prevent duplicate creation
* Always check existing records before insert
* Especially for:

  * invoices
  * devices
  * warranties

---

### 6. API Contracts

* Do NOT change request/response formats without instruction
* Maintain backward compatibility

---

### 7. Redis Rules

* Use ONLY for:

  * Redis Streams (queue)
  * caching
  * rate limiting
* NEVER store primary business data

---

### 8. Mapping Rules

* Use MapStruct ONLY for DTO ↔ Entity mapping
* No complex logic inside mappers
* Keep mappings deterministic

---

### 9. Error Handling

* Never swallow exceptions
* Always log with context
* Use global exception handler

---

### 10. Code Philosophy

* Prefer explicit over clever
* Break complex logic into small methods
* Avoid hidden behavior

---

## 🛑 Agent Boundaries (STRICT)

| Category        | Rule                                  |
| --------------- | ------------------------------------- |
| Secrets         | Never modify `.env`, API keys         |
| Security        | Never modify security package         |
| Migrations      | Never edit existing Flyway files      |
| Sensitive Logic | Never modify parsing/extraction logic |
| API Contracts   | Never change without instruction      |
| Dependencies    | No new libraries without approval     |

### ⚠️ Exception (Sprint 1 ONLY)

* Creation of:

  * docker-compose.yml
  * CI workflow
    is allowed ONLY in Sprint 1

After Sprint 1 → immutable

---

## 🤖 Execution Rules

1. Always output a `<plan>` before coding
2. Follow SPRINT.md strictly
3. Ensure code compiles (`mvn compile`)
4. Add tests with features
5. Respect all boundaries
6. Validate household_id filtering
7. One migration per schema change

---

## 📋 Sprint Roadmap

| Sprint | Focus                               |
| ------ | ----------------------------------- |
| 1      | Scaffold + data model + Docker + CI |
| 2      | Auth                                |
| 3      | Device + warranty APIs              |
| ...    | ...                                 |

---

## 🧠 Final Principle

> Clarity > Cleverness
> Safety > Speed
> Structure > Shortcuts
