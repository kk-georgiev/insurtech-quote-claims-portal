---
title: 'Story 1.4: Backend-Enforced Access to the Quote Endpoints'
type: 'feature'
created: '2026-08-24'
status: 'done'
review_loop_iteration: 0
baseline_commit: '862df9c5f23b0c7c58d1e3a66edda9a29df60998'
context: ['{project-root}/_bmad-output/planning-artifacts/architecture/architecture-motor-insurance-quote-claims-portal-2026-08-23/ARCHITECTURE-SPINE.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** No token validation exists yet — `AuthController`/`JwtService` only issue tokens (Story 1.3). Nothing checks `Authorization: Bearer <jwt>` on any request, so there is no way to gate the Quote endpoints Story 1.5 is about to add, and no shared authorization mechanism any future module can rely on (AD-2, AD-3, AD-4).

**Approach:** Add `spring-boot-starter-security` and wire one shared JWT authentication filter (`auth` module, per AD-2's "auth's JWT filter") that validates the token and populates the Spring Security context with the user id and a `ROLE_<role>` authority. Configure a stateless `SecurityFilterChain`: existing public endpoints stay public, everything else requires authentication; per-endpoint role checks use `@PreAuthorize` (method security) so Story 1.5's Quote controller can declare `hasRole('CLIENT')` without this story knowing Quote's URL shape. Since no protected business endpoint exists yet (Quote lands in 1.5), prove the gate with a test-only controller under `src/test/java`.

## Boundaries & Constraints

**Always:**
- `/actuator/health`, `/api/v1/auth/register`, `/api/v1/auth/login` stay reachable with no token — zero regression on Stories 1.1–1.3.
- 401 (missing/invalid/expired token) and 403 (valid token, wrong role) both render the exact AD-7 `ApiError` envelope — Spring Security's default handlers bypass `GlobalExceptionHandler` entirely, so the entry point/access-denied handler must build and write that JSON themselves.
- Session policy `STATELESS`, CSRF disabled (no cookies, bearer-only, AD-3).
- CORS must keep working through the new filter chain (preflight + actual cross-origin requests from the Vite dev origin) — Security's filter chain sits in front of every request, including permitted ones, so the existing `WebMvcConfigurer`-based CORS in `CorsConfig` must be re-integrated as a `CorsConfigurationSource` the `SecurityFilterChain` itself consumes, not left as a separate, now-bypassable mechanism.
- New codes: `AUTH_UNAUTHENTICATED` (401), `AUTH_FORBIDDEN` (403) — namespaced per AD-7; no i18n entry yet, matching the precedent already accepted in Stories 1.2/1.3 (deferred to Epic 3).

**Ask First:** Removing/replacing `CorsConfig`'s existing `WebMvcConfigurer` bean outright vs. keeping both mechanisms side by side.

**Never:**
- No `quote`/`pricing` module, controller, or endpoint (AD-6 — Story 1.5's job).
- No refresh token, login rate limiting, or lockout (PRD non-goals, AD-3).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| No token | Test protected endpoint, no `Authorization` header | 401, AD-7 envelope, `code: AUTH_UNAUTHENTICATED` | N/A |
| Invalid/expired token | Malformed JWT or expired `exp` | Same 401 as above | N/A |
| Wrong role | Valid token, role ≠ required role, on a role-restricted test endpoint | 403, `code: AUTH_FORBIDDEN` | N/A |
| Correct role | Valid CLIENT token on a CLIENT-only test endpoint | 200 | N/A |
| Public endpoints unaffected | No token, hits `/actuator/health` or `/api/v1/auth/login` | Unchanged from Stories 1.1–1.3 | N/A |

</frozen-after-approval>

## Code Map

- `backend/pom.xml` -- MODIFY: add `spring-boot-starter-security`; drop the now-redundant standalone `spring-security-crypto` (transitively included)
- `backend/src/main/java/com/motorinsurance/auth/application/JwtService.java` -- MODIFY: add `parseToken(String token)` returning user id + `Role` (or throwing on bad signature/expiry); signing method (`issueToken`) unchanged
- `backend/src/main/java/com/motorinsurance/auth/config/JwtAuthenticationFilter.java` -- NEW: `OncePerRequestFilter`; reads `Authorization: Bearer`, calls `JwtService.parseToken`, populates `SecurityContextHolder` with authority `ROLE_<role>`; on missing/invalid token, leaves the context empty and continues the chain (the entry point handles the eventual 401 uniformly)
- `backend/src/main/java/com/motorinsurance/auth/config/SecurityConfig.java` -- NEW: `SecurityFilterChain` bean (stateless, CSRF off, `permitAll` for `/actuator/health`, `/api/v1/auth/**`, `anyRequest().authenticated()`); registers the filter before `UsernamePasswordAuthenticationFilter`; `@EnableMethodSecurity`; custom `AuthenticationEntryPoint`/`AccessDeniedHandler` writing the `ApiError` envelope directly; consumes the CORS source below
- `backend/src/main/java/com/motorinsurance/shared/config/CorsConfig.java` -- MODIFY: expose a `CorsConfigurationSource` bean (same `app.dev-cors-origins` property) for `SecurityConfig` to wire via `.cors(...)`, replacing the `WebMvcConfigurer` mapping so there is one CORS mechanism, not two that can drift
- `backend/src/main/java/com/motorinsurance/shared/api/ApiError.java` -- READ-ONLY: reused for the 401/403 body shape
- `backend/src/test/java/com/motorinsurance/auth/config/JwtAuthenticationFilterTest.java` -- NEW: `@SpringBootTest` + `TestRestTemplate`/MockMvc; a nested test-only `@RestController` (`/api/v1/_test/protected` = authenticated-only, `/api/v1/_test/client-only` = `@PreAuthorize("hasRole('CLIENT')")`) proves the I/O matrix; also asserts `/actuator/health` and `/api/v1/auth/login` stay reachable without a token

## Tasks & Acceptance

**Execution:**
- [x] `backend/pom.xml` -- add `spring-boot-starter-security`, remove standalone `spring-security-crypto` -- brings in the filter/context/method-security machinery
- [x] `JwtService.java` -- add `parseToken` -- symmetric counterpart to `issueToken`, single source of truth for validation
- [x] `auth/config/JwtAuthenticationFilter.java` -- add -- the one shared filter (AD-2/AD-3)
- [x] `shared/config/CorsConfig.java` -- expose `CorsConfigurationSource`, drop the `WebMvcConfigurer` bean -- one CORS mechanism the Security chain actually sees
- [x] `auth/config/SecurityConfig.java` -- add filter chain + entry point + access-denied handler -- AD-4/AD-7 enforcement point
- [x] `auth/config/JwtAuthenticationFilterTest.java` -- add, with the nested test-only controller -- proves the gate works before Quote exists to consume it
- [x] `shared/api/GlobalExceptionHandler.java` -- add `AccessDeniedException` re-throw handler (discovered during implementation, not in original Code Map) -- without it, `@PreAuthorize` denials were caught by the generic `Exception` handler and returned an opaque 500 instead of reaching `SecurityConfig`'s `AccessDeniedHandler`

**Acceptance Criteria:**
- Given a request with no token, when it hits a protected endpoint, then it's rejected 401 with the AD-7 envelope.
- Given a non-CLIENT token on a CLIENT-only action, when received, then it's rejected 403 with the AD-7 envelope.
- Given a valid CLIENT token, when used on a CLIENT-only action, then the request proceeds normally (200).
- Given the existing public endpoints, when called with no token, then they behave exactly as in Stories 1.1–1.3 (no regression).

## Design Notes

**Why a test-only controller:** Story 1.5 (not this story) creates the first real protected endpoint (AD-6 — modules created on demand). The AC is still concretely testable now by proving the shared mechanism against a throwaway controller defined only in `src/test/java`, never shipped. Story 1.5 then just annotates its real controller — no changes needed here.

**Why the entry point/handler write JSON directly:** Spring Security rejects unauthenticated/unauthorized requests inside its filter chain, before `DispatcherServlet` ever routes to a controller — `@RestControllerAdvice` (`GlobalExceptionHandler`) never sees these. The `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) must serialize `ApiError` themselves.

**Why `@PreAuthorize` over path-role mapping in `SecurityConfig`:** hardcoding `/api/v1/quotes/**` → `CLIENT` here would make this story guess Quote's URL shape and force a change here whenever a role-restricted route is added. Method security keeps each endpoint's own role requirement colocated with its controller (AD-4: "every Role-restricted endpoint independently checks").

## Verification

**Commands:**
- `cd backend && mvn compile` -- expected: clean
- `cd backend && mvn test` -- expected: new `JwtAuthenticationFilterTest` cases pass (401/403/200 matrix + public-endpoint regression)
- `cd backend && mvn spring-boot:run` then `curl http://localhost:8080/actuator/health` -- expected: `{"status":"UP"}`, no token
- `curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"...","password":"..."}'` -- expected: still 200 with a token, unchanged from Story 1.3
- `cd frontend && npm run typecheck && npm run build` -- expected: clean (no frontend files touched, regression check only)

## Suggested Review Order

**JWT validation & the shared gate**

- Entry point: every "no valid token" failure mode collapses here — now including the missing-`sub`/missing-`exp` claims a review pass caught mid-story.
  [`JwtService.java:84`](../../backend/src/main/java/com/motorinsurance/auth/application/JwtService.java#L84)
- The filter never inspects a token itself — only `JwtException`/`IllegalArgumentException` are treated as "leave context empty", which is exactly why the claims above had to throw one of those two.
  [`JwtAuthenticationFilter.java:61`](../../backend/src/main/java/com/motorinsurance/auth/config/JwtAuthenticationFilter.java#L61)
- The gate itself: public paths scoped to their actual HTTP method (not bare-path `permitAll`), everything else authenticated.
  [`SecurityConfig.java:88`](../../backend/src/main/java/com/motorinsurance/auth/config/SecurityConfig.java#L88)

**403 across two mechanisms (URL-level vs `@PreAuthorize`)**

- One place both a URL-level rule and a method-level `@PreAuthorize` denial render the same AD-7 403 envelope.
  [`SecurityConfig.java:112`](../../backend/src/main/java/com/motorinsurance/auth/config/SecurityConfig.java#L112)
- Why this handler must decline (re-throw) rather than catch — otherwise a `@PreAuthorize` denial would 500, not 403.
  [`GlobalExceptionHandler.java:86`](../../backend/src/main/java/com/motorinsurance/shared/api/GlobalExceptionHandler.java#L86)

**CORS re-wired to survive the new filter chain**

- Replaces the old `WebMvcConfigurer` mapping outright — Security now sits in front of every request, including permitted ones.
  [`CorsConfig.java:44`](../../backend/src/main/java/com/motorinsurance/shared/config/CorsConfig.java#L44)
- The Actuator-specific CORS property this superseded, left in place but no longer silently stale about why.
  [`application.yml:45`](../../backend/src/main/resources/application.yml#L45)

**Test coverage**

- Focused unit coverage for every `parseToken` failure mode, including the two the review loop added (missing `sub`/`exp`) and a wrong-signature case.
  [`JwtServiceTest.java:58`](../../backend/src/test/java/com/motorinsurance/auth/application/JwtServiceTest.java#L58)
- Proves the CORS boundary automatically instead of only by manual curl — this is what caught a pre-existing header-dropping bug in the test's own `toEntity` helper.
  [`JwtAuthenticationFilterTest.java:146`](../../backend/src/test/java/com/motorinsurance/auth/config/JwtAuthenticationFilterTest.java#L146)
  [`JwtAuthenticationFilterTest.java:216`](../../backend/src/test/java/com/motorinsurance/auth/config/JwtAuthenticationFilterTest.java#L216)
- Dependency swap that brings in the filter/context/method-security machinery.
  [`pom.xml:46`](../../backend/pom.xml#L46)
