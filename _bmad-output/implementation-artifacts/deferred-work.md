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

## Deferred from: code review of story-1-5 (2026-08-26)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-quote-calculation-with-transparent-breakdown.md`
  summary: No overlap-prevention constraint exists on `tariff_rate`/`age_surcharge` ranges (only a within-row `max >= min` CHECK) — a future migration mistake introducing an overlapping cc-band or age-band row would surface only as a runtime `NonUniqueResultException` (opaque 500) from `TariffRateRepository.findApplicableRate`/`AgeSurchargeRepository.findApplicableSurcharge`, not something caught at migration/insert time.
  evidence: Review-loop finding (blind-hunter). Current seed data (`V3__create_pricing_tables.sql`) has no such overlap — 5 zones x 4 cc bands and 3 age bands, all verified contiguous and non-overlapping. Real risk only materializes if a future migration adds a bad row; a DB-level exclusion constraint (Postgres `EXCLUDE USING gist` with a range type) or an application-level check on insert would close it, deferred as structural hardening beyond this story's scope.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-quote-calculation-with-transparent-breakdown.md`
  summary: No end-to-end frontend exists for quote calculation — Story 1.5's AC is phrased "as a client, I want to submit driver/vehicle parameters and see the calculated premium," but only the backend endpoint exists.
  evidence: Review-loop finding (blind-hunter). Pre-existing scope gap, not introduced by this diff — no frontend story for the quote flow exists anywhere in `epics.md` yet either (Epic 2's Story 2.3 covers role placeholder screens only, not a quote form/result view). Worth raising as a possible `epics.md` gap separately from this story's own fixes.

## Deferred from: code review of story-1-6 (2026-08-26)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: `Quote`'s constructor narrows `int installments` to `short` with no bounds check, unlike `PricingService`'s explicit guard against the identical overflow risk.
  evidence: Review-loop finding (acceptance-auditor, edge-case-hunter, blind-hunter — 3 of 4 layers). Not reachable today: the only call path to `Quote`'s constructor is `QuoteService.calculate`, which always passes `PricingResult.installments()`, already range-checked by `PricingService` before it ever builds that result. Worth a defensive check on `Quote` itself if a second construction path is ever added.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: `QuoteService.calculate`'s `quoteRepository.save(quote)` call doesn't catch `DataIntegrityViolationException` — an unexpected DB constraint violation (e.g. a corrupted FK) would surface as the generic 500 rather than a controlled error.
  evidence: Review-loop finding (edge-case-hunter). Same "shouldn't happen given upstream validation" category already accepted for `PricingService`'s tariff-rate/age-surcharge lookups (see Story 1.5's spec) — every value reaching `save()` has already passed through validated request DTOs and successful reference-data lookups.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: No explicit length validation before persisting `regionCode`/`zoneName`/`currency` against their `VARCHAR(5)`/`VARCHAR(20)`/`VARCHAR(3)` column widths in `quotes`.
  evidence: Review-loop finding (edge-case-hunter). Low risk given provenance: `zoneName`/`currency` come from `pricing`'s seed data (bounded, controlled at migration time); `regionCode` only reaches persistence after a successful `region_zone_map` lookup, which means it already matched a `VARCHAR(5)` primary key.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: `QuoteController.currentUserId()` casts `Authentication.getPrincipal()` straight to `UUID` with no failure handling — a `ClassCastException` would surface as an opaque 500 if that assumption is ever violated.
  evidence: Review-loop finding (blind-hunter). Safe today: `auth.config.JwtAuthenticationFilter` is the only thing in this codebase that ever populates the Spring Security context, and it always sets the principal to the JWT subject's `UUID` directly (see its own javadoc). Revisit if a second authentication mechanism (e.g. session-based staff login) is ever added alongside JWT.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: `QuoteControllerTest.extractId()` parses response JSON with a hand-written regex (`"id":"..."`) instead of proper deserialization.
  evidence: Review-loop finding (blind-hunter). Works today because every response DTO is flat with one unique `id` field; would silently break or match the wrong occurrence if a response ever nests another `id`-bearing object. Test-quality cleanup, not a production code issue.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: `quotes.customer_id REFERENCES users(id)` has no explicit `ON DELETE` behavior, defaulting to Postgres's `NO ACTION`/`RESTRICT`.
  evidence: Review-loop finding (blind-hunter). Only matters once account deletion exists (no such feature anywhere in the app yet) — deleting a user with existing quotes would fail outright under the current default, which nobody has visibly decided is the right behavior for that not-yet-built feature.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: No documented retention/PII stance for `driverAge`/`regionCode`/`engineCc` now that Story 1.6 makes them durable indefinitely (Story 1.5 only calculated them transiently).
  evidence: Review-loop finding (blind-hunter). A data-governance/compliance question (how long is a quote retained, does this data need special handling) rather than a code defect — worth a decision once the project has an actual retention policy to point to.

## Deferred from: code review of story-1-6 (2026-08-27, second run)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: `PricingService.calculate` normalizes `regionCode` with default-locale `trim().toUpperCase()`, which is now load-bearing for the persisted `quotes.region_code` value.
  evidence: Review-loop finding (blind-hunter, 2nd run). `Locale.ROOT` is the correct call (Turkish-locale `i`→`İ`, `ß`→`SS` length expansion against the `VARCHAR(5)` column). Pre-existing from Story 1.5, not introduced by 1.6.
  status: RESOLVED 2026-08-27 — fixed alongside epic-1 retro action item 3 (auth got the shared `auth.domain.Emails.normalize` helper using `Locale.ROOT`; `PricingService` got the same `Locale.ROOT` argument on its own `toUpperCase`). A single shared normalizer across both modules was not extracted — email and region-code canonicalization are different rules (lower- vs upper-case, different provenance) and `pricing` importing an `auth`-owned helper would invert AD-2's dependency direction.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: `Quote` (assigned `@Id`, no `@Version`, not `Persistable`) causes `SimpleJpaRepository.save()` to call `merge()`, issuing a SELECT before every INSERT on `calculate`.
  evidence: Review-loop finding (blind-hunter, 2nd run). Codebase-wide — `auth.domain.User` has the identical shape and the identical behavior. Performance-only, negligible at current data volumes. Candidate for a shared base-entity / `Persistable` mix-in fix applied across all assigned-id entities at once, not a per-story change.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: `idx_quotes_customer_id` is not used by the story's only query (`findByIdAndCustomerId` resolves via the PK index); the V4 migration comment claiming it "backs the ownership-scoped lookup" overstates its role.
  evidence: Review-loop finding (blind-hunter, 2nd run). The index is harmless and anticipates a future "list my quotes" feature — which would actually want `(customer_id, created_at DESC)`. Revisit (index shape + comment wording) when/if that endpoint is specced.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: The new `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` in `GlobalExceptionHandler` is application-wide — every controller's typed `@PathVariable`/`@RequestParam` mismatch now returns 400 instead of the previous 500.
  evidence: Review-loop finding (blind-hunter + edge-case-hunter, 2nd run). The 400 is the correct response, so the widening is an improvement, but: (a) no test covers a query-param mismatch or a non-quote controller; (b) the message (`"Malformed request parameter"` / `"Malformed value"`) names no expected type and does not go through the AD-7/AD-8 i18n path the class javadoc describes; (c) `ex.getName()` is passed into `ApiError.FieldError` with no null guard. Polish + coverage, deferred as non-blocking.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-quote-persistence-and-retrieval.md`
  summary: `quotes.created_at` has no DB `DEFAULT now()` and is populated solely from the app clock (`Instant.now()` in the `Quote` constructor), with no injectable `Clock`.
  evidence: Review-loop finding (blind-hunter, 2nd run). Codebase-wide — `auth.domain.User` is identical. Consequences: the timestamp is not controllable in tests, is subject to multi-instance clock skew, and any future insert path that forgets to set it trips the `NOT NULL`. Best addressed as a cross-cutting convention (injectable `Clock` and/or DB defaults for all `created_at` columns).
