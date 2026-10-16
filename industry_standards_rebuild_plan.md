# 🏥 Pathology LIMS — Industry Standards Audit & Rebuild Plan

## Executive Summary

After a thorough code audit of the backend (67 Java files across 7 Maven modules) and frontend (8 React components + services), the LIMS MVP demonstrates a **solid architectural foundation** with proper modular monolith structure, DDD patterns, and clinical workflows. However, several critical gaps must be addressed to meet **ISO 15189:2022**, **HL7/FHIR**, and enterprise production standards.

---

## 🔴 Critical Issues (Must Fix)

### 1. **Security: Zero Authentication/Authorization**
- **Finding**: No Spring Security, no JWT, no RBAC, no login flow
- **Risk**: HIPAA/GDPR violation — any user can access any endpoint
- **Fix**: Add Spring Security + JWT with role-based access (ADMIN, PATHOLOGIST, TECHNICIAN, RECEPTIONIST)

### 2. **Sequence Generation is In-Memory (AtomicLong)**
- **File**: [`PatientRegistrationService.java`](file:///d:/demoProject/Lab%20Pathology%20%20Software/backend/lims-patient/src/main/java/com/lims/patient/service/PatientRegistrationService.java#L39-L40)
- **Finding**: `AtomicLong UHID_COUNTER` and `ACC_COUNTER` reset on every restart
- **Risk**: Duplicate UHIDs and accession numbers after server restart
- **Fix**: Use database sequences or a dedicated `sequences` table

### 3. **No Input Validation on API Layer**
- **Finding**: Controllers accept `any` typed request bodies without `@Valid`
- **Risk**: Injection attacks, corrupted data, bypassed business rules
- **Fix**: Add `@Valid` annotations, Jakarta Bean Validation on all DTOs

### 4. **Cross-Module Direct Entity Coupling**
- **Finding**: `lims-diagnostics` directly imports `lims-patient` entities (`DiagnosticOrder`, `Patient`, `DiagnosticOrderRepository`)
- **Risk**: Defeats modular monolith isolation, creates circular dependencies
- **Fix**: Use shared DTOs/events or anti-corruption layer interfaces

### 5. **No Transaction Boundaries on Event Listeners**
- **File**: [`ZeroClickSpoolerService.java`](file:///d:/demoProject/Lab%20Pathology%20%20Software/backend/lims-printing/src/main/java/com/lims/printing/service/ZeroClickSpoolerService.java#L48-L83)
- **Finding**: `@EventListener` runs synchronously in the same transaction
- **Risk**: Print failure rolls back order creation
- **Fix**: Use `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`

---

## 🟡 Major Issues (Should Fix)

### 6. **No API Versioning Strategy**
- Endpoints use `/api/v1/` prefix but no versioning infrastructure
- **Fix**: Add `ApiVersion` header support or content negotiation

### 7. **No Rate Limiting or Request Throttling**
- **Fix**: Add Spring Cloud Gateway or `bucket4j` rate limiter

### 8. **No Health Checks or Observability**
- No Actuator endpoints, no Prometheus metrics, no structured logging
- **Fix**: Enable Spring Boot Actuator + Micrometer + structured JSON logging

### 9. **CORS Configuration is Wide Open**
- **File**: [`CorsConfig.java`](file:///d:/demoProject/Lab%20Pathology%20%20Software/backend/lims-server/src/main/java/com/lims/server/config/CorsConfig.java#L18-L21)
- **Finding**: `allowedOriginPatterns("*")` with credentials
- **Fix**: Whitelist specific origins per environment profile

### 10. **No Pagination on List Endpoints**
- `getAllOrders()` and `searchPatients()` return unbounded lists
- **Fix**: Add `Pageable` + `Page<T>` response wrappers

### 11. **Frontend: No React Router / Code Splitting**
- All 5 major views are loaded simultaneously via tab state
- **Fix**: Add React Router for URL-based navigation + lazy loading

### 12. **Frontend: No Form Validation Library**
- Manual validation in every component
- **Fix**: Add `react-hook-form` + `zod` for schema validation

### 13. **No Error Boundary or Global Error Handling (Frontend)**
- API errors are caught per-component with inconsistent UX
- **Fix**: Add React Error Boundary + centralized toast notification system

### 14. **No Environment Configuration (Frontend)**
- API base URL is hardcoded as `/api/v1`
- WebSocket URL is hardcoded to `localhost:8080`
- **Fix**: Use Vite `.env` files for environment-specific config

---

## 🟢 Good Practices Already Present ✓

| Area | Implementation | Grade |
|------|---------------|-------|
| **Modular Monolith** | 7 Maven modules with clear DDD boundaries | ✅ A |
| **RFC 7807 ProblemDetail** | Global exception handler with typed error URIs | ✅ A |
| **Flyway Migrations** | Versioned SQL schema + seed data | ✅ A |
| **Optimistic Locking** | `@Version` on BaseEntity | ✅ A |
| **JPA Auditing** | `@CreatedDate` / `@LastModifiedDate` | ✅ A |
| **Domain Events** | Spring `ApplicationEventPublisher` for decoupling | ✅ B+ |
| **UUID Primary Keys** | `UUID.randomUUID()` for all entities | ✅ A |
| **Audit Trail Table** | Immutable `audit_logs` with JSON old/new data | ✅ A |
| **Clinical Logic** | Critical range evaluation, delta checks, digital sign-off | ✅ A |
| **Database Indexes** | Proper indexes on FK columns and search fields | ✅ A |

---

## 🔧 Rebuild Phases

> **Status (2026-10-16): all six phases implemented.** Items below were verified in code; see
> "Verification" at the end for what was run.

### Phase 1: Security & Auth (Critical)
- [x] Add Spring Security dependency
- [x] Implement JWT authentication filter
- [x] Create User/Role entities with BCrypt password hashing
- [x] Add `@PreAuthorize` annotations on controllers
- [x] Build sign-in page on frontend (`LoginPage`, session expiry handling, role-based navigation and route guards). Staff accounts are provisioned by admins (`/admin/users`), not self-registered
- [x] Add JWT interceptor to the fetch API service (`src/lib/http.ts`; WebSocket uses `access_token`)

### Phase 2: Data Integrity Hardening
- [x] Replace `AtomicLong` counters with database sequences table
- [x] Add `@Valid` on all controller `@RequestBody` parameters
- [x] Add Jakarta validation annotations on all request DTOs
- [x] Fix cross-module entity coupling with API interfaces (`ClinicalOrderGateway`)
- [x] Add `@TransactionalEventListener` for event handlers

### Phase 3: API & Performance
- [x] Add `Pageable` pagination to all list endpoints (`PageResponse<T>`)
- [ ] ~~Add response envelope (`ApiResponse<T>`) wrapper~~: **not adopted by design**. Success bodies are plain resources, lists use `PageResponse<T>`, errors use RFC 7807 `ProblemDetail`; a second envelope would duplicate HTTP status semantics
- [x] Add ETag/conditional request support (shallow ETag filter on reference data)
- [x] Add `@Cacheable` for catalog/reference-range lookups (Caffeine)
- [x] Configure HikariCP pool sizing in application.yml
- [x] Restrict CORS to environment-specific origins

### Phase 4: Observability & Operations
- [x] Enable Spring Boot Actuator (health, info, metrics, liveness/readiness probes)
- [x] Add Micrometer Prometheus metrics
- [x] Add structured JSON logging with correlation IDs
- [x] Add request/response logging filter
- [x] Add API documentation with SpringDoc examples

### Phase 5: Frontend Modernization
- [x] Add React Router with URL-based navigation (`/register`, `/accessioning`, `/results/:orderId`, `/reports/:orderId`, `/spooler`, `/admin/*`, public `/verify/:token`)
- [x] Add `react-hook-form` + `zod` validation (schemas mirror backend regexes and billing rules)
- [x] Add React Error Boundary (app-level + per-route, reset on navigation)
- [x] Add environment configuration via `.env` (`.env.example`, `.env.development`, `.env.production`)
- [x] Add code splitting with `React.lazy()` (one chunk per screen, vendor chunks)
- [x] Add centralized toast/notification system (RFC 7807-aware `toast.apiError`)
- [x] Add loading skeletons for better UX

### Phase 6: Testing & CI/CD
- [x] Add unit tests for all service classes (JUnit 5 + Mockito)
- [x] Add integration tests with Testcontainers (MySQL); they skip when Docker is unavailable and run in CI
- [x] Add Spring Modulith architecture tests
- [x] Add frontend tests (Vitest + React Testing Library)
- [x] Add GitHub Actions CI pipeline (`.github/workflows/ci.yml`)
- [x] Add Dockerfiles for containerized deployment (`backend/Dockerfile`, `frontend/Dockerfile` + nginx, `docker-compose.yml`)

---

## Verification

- Backend: `mvn verify` BUILD SUCCESS, 71 unit/module tests pass; the 2 `LimsWorkflowIT` Testcontainers tests skip locally (no Docker daemon) and run in CI.
- Frontend: strict TypeScript typecheck, 67 Vitest tests, production build.
- End to end against the running backend (H2 `dev` profile) through the Vite proxy: an API contract script
  (37 checks, all passing: auth, paging, validation problems, RBAC 403s, PDF, WebSocket handshake) and a headless-Chromium
  walkthrough of register → receive → result entry → sign-off → report → public verification → audit trail,
  with no browser console errors.
- Not yet verified locally: building/running the Docker images (Docker daemon was not running). CI builds both images.
