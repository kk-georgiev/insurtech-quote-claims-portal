---
title: 'Story 1.3: Login Issuing a Role-Bearing JWT'
type: 'feature'
created: '2026-08-23'
status: 'done'
review_loop_iteration: 0
baseline_commit: 'be8ae4123b4d9b5bbf6e02ebc59539b808a6ded1'
context: ['{project-root}/_bmad-output/planning-artifacts/architecture/architecture-motor-insurance-quote-claims-portal-2026-08-23/ARCHITECTURE-SPINE.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A registered user has no way to prove their identity to the backend — nothing issues a token yet, so every future protected endpoint (Story 1.4+) has nothing to check.

**Approach:** Add `POST /api/v1/auth/login`: verify email+password against the existing `users` table, issue a signed JWT carrying user id + Role on success, and return one generic error on any failure (wrong password or unknown email are indistinguishable). Add a matching frontend login screen that stores the token and shows the decoded role.

## Boundaries & Constraints

**Always:**
- Wrong password and unknown email both produce the exact same response: 401, code `AUTH_INVALID_CREDENTIALS`, via one new `InvalidCredentialsException extends ApiException` — no signal that distinguishes the two cases (AD-3, no user enumeration).
- The password check always runs `PasswordEncoder.matches(...)` even when no user is found (against a fixed dummy BCrypt hash), so a missing-user response takes the same time as a wrong-password response — timing must not leak account existence either.
- JWT is signed HS256 with a secret read from `JWT_SECRET` (env var, already documented in `.env.example` from Story 1.1) via one `JwtService`; claims are exactly `sub` (user id) and `role` (Role enum name), multi-hour expiry (AD-3/AD-11).
- Blank/malformed email or password still surfaces as the existing generic 400 validation error (format-only, reveals nothing about account existence) — only a value+lookup mismatch collapses into the 401 above.
- Frontend stores the token (`frontend/src/api/authToken.ts`, `localStorage`) and decodes its payload client-side (no signature verification client-side — that's the backend's job) only to display the role; no redirect/role-based routing yet (Epic 2).

**Ask First:** Any deviation from HS256/JJWT, the exact claim set, or the `localStorage` token-storage choice.

**Never:**
- No token validation filter or protected endpoints yet (Story 1.4's job) — this story only issues tokens.
- No automatic `Authorization` header attachment in `frontend/src/api/client.ts` yet — nothing to test it against until Story 1.4.
- No refresh token, rate limiting, or lockout (explicit PRD non-goals, AD-3).
- No role-based post-login routing or redirect (Epic 2, Story 2.2).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path | Registered email + correct password | 200; JWT with `sub`=user id, `role`=user's Role, multi-hour `exp` | N/A |
| Wrong password | Registered email + wrong password | 401, `code: AUTH_INVALID_CREDENTIALS` | Frontend shows one generic message |
| Unknown email | Unregistered email + any password | Identical 401 response to the wrong-password case | Frontend shows the same generic message — no distinguishing signal |

</frozen-after-approval>

## Code Map

- `backend/pom.xml` -- add `io.jsonwebtoken:jjwt-{api,impl,jackson}` (latest stable)
- `backend/src/main/resources/application.yml` -- add `jwt.secret: ${JWT_SECRET:...insecure-dev-only-default...}` and `jwt.expiration-hours: 8`, mirroring the Postgres-defaults pattern from Story 1.1
- `.env.example` -- update the `JWT_SECRET` comment: it's now actually read by code (was "not read yet" per Story 1.2's comment)
- `backend/src/main/java/com/motorinsurance/auth/application/JwtService.java` -- issues signed tokens (`sub`, `role`, `exp`) from `jwt.secret`/`jwt.expiration-hours`
- `backend/src/main/java/com/motorinsurance/auth/application/AuthenticationService.java` -- `login(email, password)`: lookup, timing-safe verify, issue token or throw
- `backend/src/main/java/com/motorinsurance/auth/application/InvalidCredentialsException.java` -- extends `ApiException`, 401 `AUTH_INVALID_CREDENTIALS`
- `backend/src/main/java/com/motorinsurance/auth/api/{LoginRequest,LoginResponse}.java` -- request (email, password) / response (`token`)
- `backend/src/main/java/com/motorinsurance/auth/api/AuthController.java` -- MODIFY: add `POST /login`
- `frontend/src/api/authToken.ts` -- `saveToken`, `getToken`, `decodeToken` (payload-only, no verification)
- `frontend/src/features/auth/LoginForm.tsx` -- login screen; on success, stores token and shows decoded role
- `frontend/src/app/router.tsx` -- MODIFY: add `/login` route
- `frontend/src/app/RootLayout.tsx` -- MODIFY: add a "Login" nav link next to "Register"

## Tasks & Acceptance

**Execution:**
- [x] `backend/pom.xml` -- add JJWT dependencies -- token signing/parsing
- [x] `application.yml` -- add `jwt.secret`/`jwt.expiration-hours` with insecure dev default -- works out of the box, matches Story 1.1's Postgres pattern
- [x] `.env.example` -- fix the stale "not read yet" comment on `JWT_SECRET` -- keeps docs accurate
- [x] `auth/application/JwtService.java` -- add token issuance -- core of this story
- [x] `auth/application/InvalidCredentialsException.java` -- add, extends `ApiException` -- 401 `AUTH_INVALID_CREDENTIALS`
- [x] `auth/application/AuthenticationService.java` -- add: lookup, timing-safe password check (dummy hash when no user), issue-or-throw -- no user enumeration
- [x] `auth/api/{LoginRequest,LoginResponse,AuthController}.java` -- add DTOs + `POST /login` -- public endpoint
- [x] `frontend/src/api/authToken.ts` -- add save/get/decode helpers -- shared by the login screen and (later) the API client
- [x] `frontend/src/features/auth/LoginForm.tsx` -- add login form + success state showing decoded role
- [x] `frontend/src/app/router.tsx` -- add `/login` route
- [x] `frontend/src/app/RootLayout.tsx` -- add "Login" nav link

**Acceptance Criteria:**
- Given valid credentials, when I log in, then I receive a JWT with user id + Role and a multi-hour expiry.
- Given an incorrect password or an unknown email, when I try to log in, then I get the same generic `AUTH_INVALID_CREDENTIALS` error either way — no signal reveals whether the email is registered.

## Spec Change Log

- **Orchestrator verification (before review loop)**: the implementing subagent had no Java/Maven toolchain available and could not compile or run the backend — it verified the JJWT 0.13.0 API by reading Maven Central/GitHub sources instead. Actually compiling caught a real defect: `JwtService.signingKey` was typed `java.security.Key` (the return type of `Keys.hmacShaKeyFor(...)` is `javax.crypto.SecretKey`, a subtype), which broke `signWith`'s generic type inference (`method <K> signWith(K, SecureDigestAlgorithm<? super K,?>)`, bounds unsatisfiable from the wider declared type) — `mvn compile` failed outright. Fixed by declaring the field as `SecretKey` instead of `Key`. Re-verified end to end after the fix: `mvn compile`/`spring-boot:run` clean, full register→login round trip via curl (happy path, wrong password, unknown email — all matching the I/O matrix), JWT payload decoded and confirmed (`sub`/`role`/8h `exp`), timing sanity-checked as comparable between the two failure modes, and the login flow re-tested live in a browser (success state shows "as CLIENT", error state shows the generic message, form stays editable).
- **Review-loop iteration (3-reviewer adversarial pass, 1 patch applied, no spec renegotiation needed)**: email was never normalized anywhere — `RegistrationService.register` stored whatever casing the client sent, and `AuthenticationService.login` did a case-sensitive lookup, so a user registering as `User@Example.com` and logging in as `user@example.com` would get a false `AUTH_INVALID_CREDENTIALS`. Two independent reviewers converged on this (and it was the exact risk flagged in `deferred-work.md` after Story 1.2, now confirmed rather than deferred further). Fixed by normalizing (`.trim().toLowerCase()`) email in both methods, before the lookup/persistence. **Discovered while applying this fix**: the literal instruction (`email = email.trim().toLowerCase();`, reassigning the parameter) failed `mvn compile` in `RegistrationService` — that variable is captured by the `.ifPresent(...)` lambda a few lines down, and a reassigned parameter is no longer effectively final. Fixed by introducing a `final String normalizedEmail` local instead, used throughout the method (lookup, the lambda's exception message, `new User(...)`, and the catch block). Re-verified live: registered `Mixed.Case@Test.com`, then logged in successfully with `mixed.case@test.com`, `Mixed.Case@Test.com`, and `MIXED.CASE@TEST.COM` (all issuing a token for the same user id); registered `dup@test.com` then confirmed `DUP@test.com` correctly gets `409 AUTH_EMAIL_TAKEN` instead of creating a second account; confirmed the wrong-password/unknown-email paths still both return the identical `401 AUTH_INVALID_CREDENTIALS`.

## Design Notes

**Timing-safe lookup:** hashing is the expensive part of a credentials check; skipping it entirely when the email isn't found would make "unknown email" responses measurably faster than "wrong password" ones, leaking account existence via timing even though the response bodies are identical. `AuthenticationService` always calls `passwordEncoder.matches(...)` — against a fixed dummy hash when no user was found — so both failure paths take comparable time.

**`JWT_SECRET` dev default:** Story 1.1 documented the variable with no real value; this story adds an insecure, clearly-labeled fallback default in `application.yml` (`${JWT_SECRET:...}`) so `mvn spring-boot:run` still works out of the box on a clean checkout, mirroring the Postgres credentials' `${VAR:-default}` pattern already established. A real deployment would override it via the actual environment variable.

**`localStorage` for the token:** simplest option, persists across reloads/tabs. Hardening this (httpOnly cookies, XSS mitigation) is explicitly out of scope — the PRD excludes "production-grade auth hardening" from this milestone (AD-3's rationale).

## Verification

**Commands:**
- `cd backend && mvn spring-boot:run` -- expected: starts cleanly
- Register a user via the Story 1.2 endpoint, then: `curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"a@test.com","password":"password123"}'` -- expected: 200, response has a `token` field
- Decode the returned JWT payload (e.g. `echo <payload> | base64 -d`) -- expected: `sub` = the registered user's id, `role: "CLIENT"`, `exp` ~8h out
- Same email, wrong password -- expected: 401, `code: "AUTH_INVALID_CREDENTIALS"`
- Unregistered email -- expected: identical 401 response shape to the wrong-password case
- `cd frontend && npm run typecheck && npm run build` -- expected: clean

## Suggested Review Order

**No user enumeration: the core invariant**

- Both failure modes throw the identical exception — nothing distinguishes "unknown email" from "wrong password".
  [`AuthenticationService.java:61`](../../backend/src/main/java/com/motorinsurance/auth/application/AuthenticationService.java#L61)
- Dummy hash always compared, even for a missing user — closes the timing side-channel the response-body identity alone wouldn't.
  [`AuthenticationService.java:58`](../../backend/src/main/java/com/motorinsurance/auth/application/AuthenticationService.java#L58)
- One exception type, no detail that could leak which check failed.
  [`InvalidCredentialsException.java:15`](../../backend/src/main/java/com/motorinsurance/auth/application/InvalidCredentialsException.java#L15)

**Email normalization (review-loop patch, spans this story + Story 1.2)**

- Login-side lookup normalized to match whatever registration stored.
  [`AuthenticationService.java:52`](../../backend/src/main/java/com/motorinsurance/auth/application/AuthenticationService.java#L52)
- Registration-side: a `final` local (not a reassigned parameter) because the duplicate-check lambda captures it — the literal patch instruction didn't compile without this.
  [`RegistrationService.java:40`](../../backend/src/main/java/com/motorinsurance/auth/application/RegistrationService.java#L40)

**JWT issuance**

- Claims are exactly `sub`/`role`, HS256, `SecretKey`-typed signing key (orchestrator-level compile fix — was `java.security.Key`, broke `signWith`'s generic inference).
  [`JwtService.java:45`](../../backend/src/main/java/com/motorinsurance/auth/application/JwtService.java#L45)
- Insecure dev-only fallback secret, mirrors Story 1.1's Postgres-credentials pattern.
  [`application.yml:16`](../../backend/src/main/resources/application.yml#L16)
- Public endpoint, no auth guard yet (Story 1.4).
  [`AuthController.java:38`](../../backend/src/main/java/com/motorinsurance/auth/api/AuthController.java#L38)

**Frontend: login screen**

- Form state machine + generic-message mapping for `AUTH_INVALID_CREDENTIALS`, mirrors `RegisterForm.tsx`'s established pattern.
  [`LoginForm.tsx:35`](../../frontend/src/features/auth/LoginForm.tsx#L35)
  [`LoginForm.tsx:79`](../../frontend/src/features/auth/LoginForm.tsx#L79)
- Unmount guard, same StrictMode-aware reset pattern as `RegisterForm.tsx`.
  [`LoginForm.tsx:47`](../../frontend/src/features/auth/LoginForm.tsx#L47)
- Token storage + payload-only client-side decode (no signature check — display purposes only).
  [`authToken.ts:14`](../../frontend/src/api/authToken.ts#L14)
  [`authToken.ts:42`](../../frontend/src/api/authToken.ts#L42)
