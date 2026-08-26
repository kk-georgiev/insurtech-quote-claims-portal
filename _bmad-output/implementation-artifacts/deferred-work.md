# Deferred Work

Append-only. Entries collected from bmad-build review loopbacks. Do not modify existing entries or look for duplicates.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-project-scaffolding-runnable-backend-and-frontend-skeleton.md`
  summary: GlobalExceptionHandler has no handlers for common REST failure modes (malformed JSON body, wrong HTTP verb, unsupported media type) beyond the generic 500 fallback.
  evidence: PARTIALLY RESOLVED in Story 1.5 - added a `HttpMessageNotReadableException` handler (malformed JSON body / wrong-typed field) once `POST /api/v1/quotes` became the second controller with a request body and the first with number-typed fields a client can plausibly mistype. Wrong HTTP verb and unsupported media type are still open - no story has exercised those paths yet.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-project-scaffolding-runnable-backend-and-frontend-skeleton.md`
  summary: No root-level README ties `backend/README.md` and `frontend/README.md` together into one "get the whole stack running" entry point.
  evidence: PRD success metric SM-1 values a single documented command for a teammate to reach a working app from a clean checkout; worth adding once Epic 4 (one-command full-stack startup) lands, or sooner if onboarding friction shows up.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-project-scaffolding-runnable-backend-and-frontend-skeleton.md`
  summary: GlobalExceptionHandler's MethodArgumentNotValidException handler only maps field-level errors; global (class-level) Bean Validation errors are silently dropped from `fieldErrors`.
  evidence: No class-level validation constraint exists anywhere in the codebase yet; revisit when a story (likely quote input validation, e.g. experience > age-17) introduces one.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-project-scaffolding-runnable-backend-and-frontend-skeleton.md`
  summary: `fe.getDefaultMessage()` in GlobalExceptionHandler can be null for a binding error, which would serialize as JSON `null` in a field error message.
  evidence: Narrow edge case with no current trigger (no validated fields exist yet); revisit alongside the global-validation-errors gap above.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-project-scaffolding-runnable-backend-and-frontend-skeleton.md`
  summary: `frontend/src/api/client.ts`'s `apiFetch` has no request timeout — the returned promise never settles if the backend hangs or the network stalls.
  evidence: Not required by this story's I/O matrix (only "no crash" on failure is specified); worth hardening once more of the app depends on this shared client for user-facing flows (login, quote submission).

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-project-scaffolding-runnable-backend-and-frontend-skeleton.md`
  summary: The backend's `ApiError.code` (the field AD-7/AD-8 designate as the sole driver of frontend i18n) is discarded by `ApiRequestError` and never surfaced to callers of `apiFetch`.
  evidence: No module throws a coded error the frontend needs to translate yet; revisit when Epic 3 (i18n) or any story first needs to show a translated error to the user.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-project-scaffolding-runnable-backend-and-frontend-skeleton.md`
  summary: `docker-compose.yml`'s Postgres service maps its port with no host binding, so it listens on all interfaces rather than `127.0.0.1` only.
  evidence: Low risk on a personal dev machine given default `postgres`/`postgres` credentials are already documented as insecure-by-design local-dev-only; worth tightening if this compose file is ever run somewhere less trusted.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-project-scaffolding-runnable-backend-and-frontend-skeleton.md`
  summary: `application.yml` bakes the same insecure local-dev Postgres credentials in as its own defaults, with no separate profile that fails fast if those defaults are still active outside local dev.
  evidence: No production or shared deployment target exists yet (Epic 4 is still local Docker Compose); revisit once a real deployment target is defined.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-client-self-registration.md`
  summary: Field-level validation messages (`ApiError.FieldError.message`) are raw Bean Validation text rendered directly to the user in `RegisterForm`, which contradicts AD-8's "backend never emits user-facing prose — only stable codes" invariant (the top-level `ApiError.code` already follows this; per-field errors don't).
  evidence: Proper fix needs a stable code per validation rule plus a frontend i18n mapping, which is Epic 3's job; this milestone's I/O matrix explicitly expects "field-level messages" to reach the user, so it's an accepted simplification for now, not a regression.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-client-self-registration.md`
  summary: `GlobalExceptionHandler` has no handler for a malformed JSON request body (`HttpMessageNotReadableException`) — it falls through to the generic 500 instead of a clean 400. Now that `/api/v1/auth/register` is the first real controller with a request body, this is reachable, not purely theoretical.
  evidence: Not required by this story's AC/I/O matrix; consistent with the same category already deferred from Story 1.1. Worth adding once a second controller with a body lands, to do it once for the general case rather than per-endpoint.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-client-self-registration.md`
  summary: Email is not normalized (trimmed/lowercased) before the duplicate-email check or persistence in `RegistrationService`, so `User@Example.com` and `user@example.com` can register as two distinct accounts (or collide unpredictably depending on DB collation).
  evidence: RESOLVED in Story 1.3's review loop — two independent reviewers confirmed this exact risk materializes for login (a correctly-typed login could fail to match a differently-cased stored email), so it was fixed as a patch across both `RegistrationService` and `AuthenticationService` rather than deferred further. Kept here for history.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-3-login-issuing-a-role-bearing-jwt.md`
  summary: `RegisterRequest`/`LoginRequest` allow passwords up to 100 characters, but `BCryptPasswordEncoder` only considers the first 72 bytes of input — two different passwords sharing a 72-byte prefix authenticate identically, with no truncation, warning, or documentation of the mismatch anywhere.
  evidence: Well-known, industry-wide BCrypt characteristic, not a security bypass (doesn't weaken auth for typical passwords) — worth a consistency pass (e.g. cap both DTOs at 72, or document the limitation) whenever registration/login validation is revisited, not urgent enough to block this story.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-3-login-issuing-a-role-bearing-jwt.md`
  summary: No logout capability anywhere — `frontend/src/api/authToken.ts` has `saveToken`/`getToken` but no `clearToken`, and `RootLayout`'s nav isn't auth-aware (no "Logout" link, still shows plain Register/Login after a successful login).
  evidence: Not required by this story's AC (no role-based post-login UX yet — that's Epic 2). Natural follow-up once Epic 2's role-based routing/nav lands.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-4-backend-enforced-access-to-the-quote-endpoints.md`
  summary: `JwtAuthenticationFilter`'s `"Bearer "` prefix match is case-sensitive, so a technically-valid `Authorization: bearer <token>` header (RFC 7235 scheme names are case-insensitive) is silently treated as anonymous and gets the generic 401 instead of being accepted.
  evidence: Review-loop finding (edge-case-hunter). Every client this project controls (the frontend's own `apiFetch`, manual curl in docs) sends canonical `"Bearer"` casing, so there is no current trigger; worth a `regionMatches(true, ...)` fix if a non-canonical client is ever integrated.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-4-backend-enforced-access-to-the-quote-endpoints.md`
  summary: `JwtAuthenticationFilterTest`'s expired-token test case re-derives the HMAC signing key from `jwt.secret` via `Keys.hmacShaKeyFor(...)` to forge a token, duplicating `JwtService`'s own private key-construction logic instead of using a seam on `JwtService` itself.
  evidence: Review-loop finding (blind-hunter). Works correctly today because both places read the same property with the same JJWT call; if `JwtService`'s key-construction ever changes (e.g. a different KDF or key format), this test could silently start signing with a different key than production code verifies with, and drift undetected. Worth exposing a package-private/test-scoped key accessor on `JwtService` if this pattern is needed again.
