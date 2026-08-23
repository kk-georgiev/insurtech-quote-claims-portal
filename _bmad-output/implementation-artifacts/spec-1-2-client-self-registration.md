---
title: 'Story 1.2: Client Self-Registration'
type: 'feature'
created: '2026-08-23'
status: 'done'
review_loop_iteration: 1
baseline_commit: 'ac5bbd767404c09f0a0a50fc3af48e7e2027127a'
context: ['{project-root}/_bmad-output/planning-artifacts/architecture/architecture-motor-insurance-quote-claims-portal-2026-08-23/ARCHITECTURE-SPINE.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There is no way for a prospective client to create an account — the `auth` module doesn't exist yet, so nothing beyond the Story 1.1 health check is reachable.

**Approach:** Create the `auth` module (first real capability, AD-6) with one registration endpoint that creates a User with Role=CLIENT, hashed password, and a matching frontend form — plus a reusable `ApiException` pattern in `shared` so every future module-specific error (this story's `AUTH_EMAIL_TAKEN`, and later `AUTH_INVALID_CREDENTIALS`, `QUOTE_VALIDATION_ERROR`, etc.) plugs into the existing centralized handler without new handler methods each time.

## Boundaries & Constraints

**Always:**
- `RegisterRequest` has no `role` field — the service hardcodes `Role.CLIENT`. Self-registration can never produce a staff account (privilege-escalation invariant).
- Password is hashed with `BCryptPasswordEncoder` (`spring-security-crypto`, not the full Spring Security starter — no filter chain/auto-secured-endpoints this story) before persistence; raw password is never logged, stored, or returned in any response.
- Duplicate email → a new `EmailAlreadyRegisteredException extends ApiException` (`shared`), HTTP 409, code `AUTH_EMAIL_TAKEN`, routed through one new generic `@ExceptionHandler(ApiException.class)` in the existing `GlobalExceptionHandler` — not a one-off catch block.
- Bean Validation (`@Email`, `@NotBlank`, `@Size(min=8, max=100)` on password) enforces field-level errors at the API layer; `users.email` is `UNIQUE NOT NULL` and `password_hash`/`role` are `NOT NULL` at the DB layer.
- New `auth` module follows the existing `api/application/domain/persistence` layering exactly as documented in the Architecture Spine.
- Frontend calls the endpoint through the existing typed client (`frontend/src/api/client.ts`) — no new HTTP layer.

**Ask First:** Any deviation from BCrypt for hashing, or from the `api/application/domain/persistence` layering.

**Never:**
- No login or JWT issuance (Story 1.3) — registration only.
- No password-strength UI or complexity rules, no email verification (explicit PRD non-goals this epic).
- No redirect to a login screen after success — it doesn't exist yet; show a success state in place.
- No `spring-boot-starter-security` dependency this story — it would auto-secure every endpoint (including Story 1.1's health check) before Story 1.3/1.4 need real auth.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path | Unique email, password ≥ 8 chars | 201; User row created, Role=CLIENT, password hashed | N/A |
| Duplicate email | Email already registered | 409, `ApiError.code = AUTH_EMAIL_TAKEN` | Frontend shows a mapped message, form stays editable |
| Invalid input | Blank/malformed email, or password < 8 chars | 400 with `fieldErrors` (reuses Story 1.1's validation handler) | Frontend shows field-level messages |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/V2__create_users_table.sql` -- `users` table: `id UUID PK`, `email VARCHAR UNIQUE NOT NULL`, `password_hash VARCHAR NOT NULL`, `role VARCHAR NOT NULL CHECK (...)`, `created_at TIMESTAMPTZ NOT NULL`
- `backend/src/main/java/com/motorinsurance/auth/domain/User.java` -- JPA entity, mirrors the migration
- `backend/src/main/java/com/motorinsurance/auth/domain/Role.java` -- enum `CLIENT, AGENT, LIQUIDATOR, ADMINISTRATOR` (all 4 now — Epic 2 seeds the other 3 into this same table)
- `backend/src/main/java/com/motorinsurance/auth/persistence/UserRepository.java` -- Spring Data JPA, `findByEmail`
- `backend/src/main/java/com/motorinsurance/auth/application/RegistrationService.java` -- use-case: check duplicate, hash, save
- `backend/src/main/java/com/motorinsurance/auth/application/EmailAlreadyRegisteredException.java` -- extends new `shared` `ApiException`
- `backend/src/main/java/com/motorinsurance/auth/api/RegisterRequest.java` / `RegisterResponse.java` -- request/response DTOs (no `role` field on the request)
- `backend/src/main/java/com/motorinsurance/auth/api/AuthController.java` -- `POST /api/v1/auth/register`
- `backend/src/main/java/com/motorinsurance/auth/config/PasswordEncoderConfig.java` -- `BCryptPasswordEncoder` bean (Story 1.3's login reuses it)
- `backend/src/main/java/com/motorinsurance/shared/api/ApiException.java` -- NEW abstract base (status, code, message, fieldErrors) so future module errors need zero new handler methods
- `backend/src/main/java/com/motorinsurance/shared/api/GlobalExceptionHandler.java` -- MODIFY: add one `@ExceptionHandler(ApiException.class)`
- `backend/pom.xml` -- MODIFY: add `spring-security-crypto`
- `frontend/src/features/auth/RegisterForm.tsx` -- registration screen, calls the endpoint via `apiFetch`
- `frontend/src/app/router.tsx` -- MODIFY: add `/register` route

## Tasks & Acceptance

**Execution:**
- [x] `V2__create_users_table.sql` -- add `users` table -- backs the entity + DB-layer constraints
- [x] `auth/domain/{User,Role}.java` -- add entity + enum -- matches migration
- [x] `auth/persistence/UserRepository.java` -- add `findByEmail` -- duplicate-check + future login lookup
- [x] `shared/api/ApiException.java` -- add base exception type -- reusable envelope-mapping for every future module error
- [x] `shared/api/GlobalExceptionHandler.java` -- add `ApiException` handler -- keeps AD-7's "one centralized handler" invariant
- [x] `auth/application/EmailAlreadyRegisteredException.java` -- add, extends `ApiException` -- 409 `AUTH_EMAIL_TAKEN`
- [x] `auth/config/PasswordEncoderConfig.java` -- add `BCryptPasswordEncoder` bean -- reused by Story 1.3
- [x] `auth/application/RegistrationService.java` -- add: duplicate check, hash, save, hardcode `Role.CLIENT` -- core use case
- [x] `auth/api/{RegisterRequest,RegisterResponse,AuthController}.java` -- add DTOs + controller -- `POST /api/v1/auth/register`
- [x] `backend/pom.xml` -- add `spring-security-crypto` -- BCrypt only, no auto-secured endpoints
- [x] `frontend/src/features/auth/RegisterForm.tsx` -- add registration form + success/error states
- [x] `frontend/src/app/router.tsx` -- add `/register` route

**Acceptance Criteria:**
- Given a new email, when I submit registration with a valid email+password, then a User is created with Role=CLIENT and the password is hashed, never plain text.
- Given an already-registered email, when I try again, then I get `AUTH_EMAIL_TAKEN` via the AD-7 envelope, not a generic error.
- Given invalid input, when submitted, then field-level validation errors are enforced identically at the API layer and as DB constraints.

## Spec Change Log

- `frontend/src/api/client.ts` was extended (not just consumed as-is): `ApiRequestError` now carries `code` and `fieldErrors` parsed from the AD-7 envelope on a non-2xx response, and a new `ApiFieldError` type is exported. The Code Map didn't list this file, but the AC ("Frontend shows a mapped message" for `AUTH_EMAIL_TAKEN`, "Frontend shows field-level messages" for 400s) and AD-7's "`code` is the only thing the frontend uses to select translated text" aren't satisfiable from `response.status` alone — the client needs the parsed error body. This is an extension of the existing typed client, not a new HTTP layer, so it stays inside the "Always" boundary. `ApiRequestError.message` is deliberately left as a generic `status`-based string (never the backend's dev/log-facing `ApiError.message`), so nothing downstream is tempted to render it to an end user.
- **Review-loop iteration 1** (3-reviewer adversarial pass, 4 patch-level fixes applied, no spec renegotiation needed):
  1. `RegistrationService.register()` — the `findByEmail` pre-check and `save()` were a check-then-act race: two concurrent requests for the same email could both pass the check, and the losing insert threw an uncaught `DataIntegrityViolationException` → opaque 500 instead of `409 AUTH_EMAIL_TAKEN`. Now wrapped in a `try/catch` that re-throws as `EmailAlreadyRegisteredException`. **Discovered while verifying this fix**: `save()` alone wasn't enough — Spring Data normally defers the actual `INSERT` to transaction-commit time, which happens *after* `register()` returns, so the constraint violation was still escaping the `try/catch` untouched (proved with a live 2-concurrent-request test: `201`/`500`, one row in the DB). Switched to `saveAndFlush()` so the insert - and any constraint violation - happens synchronously inside the `try`. Re-tested with 3 concurrent requests for one new email: `201`/`409`/`409`, exactly one row in the DB.
  2. `RegisterRequest.email` had `@Email @NotBlank` but no length bound, even though `users.email` is `VARCHAR(255)` and the class's own javadoc claimed Bean Validation mirrors the DB constraints. A >255-char email would have passed validation and failed at the DB layer as an uncaught 500. Added `@Size(max = 255)`.
  3. `RegisterForm.handleSubmit`'s async work had no unmount guard (a regression from `HealthStatus.tsx`'s established `cancelled`-flag pattern in this same codebase) — state setters could fire after the user navigated away mid-request. Added a `cancelledRef` checked before each post-await `setState`. **Discovered while verifying this fix**: the naive version (register a cleanup that sets the ref `true`, nothing else) got permanently stuck after the very first mount — React 19 `StrictMode` (see `main.tsx`) double-invokes effects in dev (mount → cleanup → mount), and the cleanup's `true` was never reset back to `false` on the second mount, so every real submit afterward silently no-opped at the `if (cancelledRef.current) return;` guard even though the request succeeded (proved live: POST returned `201` but the UI stayed on "Creating account…" forever). Fixed by also resetting `cancelledRef.current = false` at the top of the mount effect, so only a genuine unmount leaves it `true`.
  4. `/register` was wired into the router but had no entry point anywhere in the UI (URL-only reachable). Added a plain `Link` to `/register` in `RootLayout.tsx`'s header nav.
  - Findings rejected as out-of-scope/non-issues (logged to `deferred-work.md`, no action here): lack of automated tests (already-accepted milestone-wide gap), and several PRD non-goals mistakenly flagged as bugs (rate limiting, email verification, password-strength UI, audit logging, and email-enumeration-via-409 — the last one is this story's own required behavior, not a defect).

## Design Notes

`ApiException` is a small abstract class (`status`, `code`, `message`, `fieldErrors`) that module-specific exceptions extend. `GlobalExceptionHandler` gets exactly one new `@ExceptionHandler(ApiException.class)` method that reads those fields generically — so `shared` never needs to import a module's exception classes (which would invert AD-2's dependency direction), and Story 1.3's `AUTH_INVALID_CREDENTIALS`, Story 1.5's `QUOTE_VALIDATION_ERROR`, etc. all reuse this same handler with zero changes to `shared`.

Only `spring-security-crypto` is added this story (for `BCryptPasswordEncoder`), not the full `spring-boot-starter-security` — that starter auto-secures every endpoint by default, which would break Story 1.1's health check and isn't needed until Story 1.3/1.4 wire up the real JWT filter chain.

## Verification

**Commands:**
- `cd backend && mvn spring-boot:run` -- expected: starts cleanly, Flyway applies `V2__create_users_table`
- `curl -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" -d '{"email":"a@test.com","password":"password123"}'` -- expected: 201, no password in response
- Repeat the same curl -- expected: 409, `code: "AUTH_EMAIL_TAKEN"`
- `cd frontend && npm run typecheck && npm run build` -- expected: clean

**Actually run (all passed):**
- `mvn -DskipTests package` (backend) -- clean build.
- `mvn spring-boot:run` against `docker compose up postgres` -- started cleanly; log confirms `Successfully applied 1 migration ... now at version v2` and Hibernate's `ddl-auto: validate` accepted `User` against the new table (no schema-mismatch failure).
- All three curl cases from this section run for real: happy path returned `201 {"id":...,"email":"a@test.com","role":"CLIENT"}` (no password field); repeat returned `409 {"code":"AUTH_EMAIL_TAKEN",...}`; malformed email + short password returned `400` with both `fieldErrors` entries. Confirmed in Postgres directly: `password_hash` is a real BCrypt hash (`$2a$10$...`), not plaintext.
- `npm run typecheck` and `npm run build` (frontend) -- both clean.
- Exercised `RegisterForm` in a live browser against the running backend (not just curl): happy path shows the success state with no redirect; a duplicate submission renders "This email is already registered." with every field still populated and enabled (not locked); an invalid submission renders the backend's per-field messages under the matching inputs. Test rows were deleted from `users` afterward.

**Re-run after review-loop iteration 1's 4 patch fixes (all passed):**
- `mvn -DskipTests package` (backend) and `npm run typecheck && npm run build` (frontend) -- both clean, rebuilt from scratch after every patch.
- Backend restarted fresh against `docker compose up postgres`; Flyway/`ddl-auto: validate` unaffected.
- Sequential happy-path → `201`, then repeat → clean `409 {"code":"AUTH_EMAIL_TAKEN",...}` -- unchanged, confirming the fast `findByEmail` path still works.
- **Race backstop, specifically re-confirmed per the coordinator's ask**: 3 truly concurrent `POST /api/v1/auth/register` for one brand-new email → `201`/`409`/`409`, all three clean AD-7 envelopes, no 500; exactly one row landed in `users`. (First attempt with plain `save()` instead of `saveAndFlush()` still produced a raw `500` under concurrency — see Spec Change Log — so this was genuinely re-tested until it held, not just re-run once.)
- `>255`-char email → clean `400` with a `size must be between 0 and 255` field error instead of a DB-layer 500.
- Live browser re-test of `RegisterForm` after the `cancelledRef` fix: submit → success state renders (previously got stuck forever on "Creating account…" after the first mount, per Spec Change Log); duplicate submit on a fresh mount → mapped message renders correctly, form stays editable.
- Live browser check of the new `/register` link in `RootLayout.tsx`'s header: present on the home route, navigates to `/register` client-side (no full reload).
- All test rows deleted from `users` afterward; ports 8080/5173 and temporary `.claude/launch.json` cleaned up.

## Suggested Review Order

**Backend: `shared` — the reusable error-mapping seam**

- New base type every module-specific exception extends; `shared` never imports a module's concrete exception class (AD-2 direction preserved).
  [`ApiException.java:13`](../../backend/src/main/java/com/motorinsurance/shared/api/ApiException.java#L13)
- The one new handler method that makes future module errors (Story 1.3's `AUTH_INVALID_CREDENTIALS`, etc.) free — no further changes to this class required.
  [`GlobalExceptionHandler.java:63`](../../backend/src/main/java/com/motorinsurance/shared/api/GlobalExceptionHandler.java#L63)

**Backend: `auth` — registration use case**

- Privilege-escalation invariant lives here: no `role` field on the request, service hardcodes `Role.CLIENT`.
  [`RegistrationService.java:26`](../../backend/src/main/java/com/motorinsurance/auth/application/RegistrationService.java#L26)
  [`RegisterRequest.java:9`](../../backend/src/main/java/com/motorinsurance/auth/api/RegisterRequest.java#L9)
- Duplicate-email → 409 `AUTH_EMAIL_TAKEN`, routed through the generic `ApiException` handler above, not a one-off catch block.
  [`EmailAlreadyRegisteredException.java:13`](../../backend/src/main/java/com/motorinsurance/auth/application/EmailAlreadyRegisteredException.java#L13)
- `saveAndFlush` + `DataIntegrityViolationException` catch closes the check-then-act race on the DB's `UNIQUE` constraint (review-loop patch; live-verified with concurrent requests — see Verification).
  [`RegistrationService.java:38`](../../backend/src/main/java/com/motorinsurance/auth/application/RegistrationService.java#L38)
- `email` now bounded to match `VARCHAR(255)`, so an oversized email 400s instead of 500ing at the DB layer (review-loop patch).
  [`RegisterRequest.java:14`](../../backend/src/main/java/com/motorinsurance/auth/api/RegisterRequest.java#L14)
- Entity mirrors `V2__create_users_table.sql` exactly (`ddl-auto: validate` would fail startup otherwise — verified live).
  [`User.java:20`](../../backend/src/main/java/com/motorinsurance/auth/domain/User.java#L20)
  [`V2__create_users_table.sql:9`](../../backend/src/main/resources/db/migration/V2__create_users_table.sql#L9)
- BCrypt-only bean; deliberately not `spring-boot-starter-security`.
  [`PasswordEncoderConfig.java:18`](../../backend/src/main/java/com/motorinsurance/auth/config/PasswordEncoderConfig.java#L18)

**Frontend: registration screen + client extension**

- Form states (editing/submitting/success), the AUTH_EMAIL_TAKEN → mapped-copy branch, and the fieldErrors → per-field branch.
  [`RegisterForm.tsx:60`](../../frontend/src/features/auth/RegisterForm.tsx#L60)
- Unmount guard for the async submit; note the explicit reset on mount, not just the cleanup (review-loop patch — the reset is required for StrictMode's dev double-invoke, see Spec Change Log for the stuck-forever bug this avoids).
  [`RegisterForm.tsx:52`](../../frontend/src/features/auth/RegisterForm.tsx#L52)
- `ApiRequestError` now carries `code`/`fieldErrors` from the AD-7 envelope (see Spec Change Log above for why this file needed touching beyond the Code Map).
  [`client.ts:70`](../../frontend/src/api/client.ts#L70)
- New `/register` route.
  [`router.tsx:9`](../../frontend/src/app/router.tsx#L9)
- `/register` entry point in the header nav (review-loop patch — the route existed but nothing linked to it).
  [`RootLayout.tsx:15`](../../frontend/src/app/RootLayout.tsx#L15)
