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

## Deferred from: Epic 1 retrospective (2026-08-26)

- source_spec: `_bmad-output/implementation-artifacts/epic-1-retro-2026-08-26.md`
  summary: No token revocation/logout mechanism exists — a role change or account deactivation for an existing user wouldn't take effect until their JWT expires (up to 8h).
  evidence: Not a Milestone-1 defect (Story 1.4's spec explicitly names "no refresh token, login rate limiting, or lockout" as non-goals). Becomes a real design question once Epic 2 introduces administratively-managed staff roles — a demoted/deactivated staff account would keep acting under its old role/claims for up to 8h. Revisit when Epic 2 specs role management; likely resolution is either a shorter-lived token, a revocation list, or accepting the window as a documented trade-off.

- source_spec: `_bmad-output/implementation-artifacts/epic-1-retro-2026-08-26.md`
  summary: No documented decision recording that staff roles (AGENT/LIQUIDATOR/ADMINISTRATOR) are locked out of the Quote module in Epic 1.
  evidence: Every quote endpoint hardcodes `hasRole('CLIENT')`; no staff path and no per-staff-role test exist. Given Epic 2 is explicitly titled "Every Role Gets Their Own Workspace" and Epic 1's own spec never mentions staff quote access, this is deliberate scope sequencing, not an oversight — recorded here so Epic 2's implementer doesn't have to rediscover it.

## Deferred from: Story 2.1 review (2026-08-28)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-seeded-staff-demo-accounts.md`
  summary: `V5__seed_staff_accounts.sql` is ungated, so the three demo staff accounts — including an ADMINISTRATOR whose password is published in `README.md` — would be provisioned into any database the app is ever pointed at, including a real deployment.
  evidence: Review-loop finding (blind-hunter + edge-case-hunter, 1st run). `application.yml` sets `spring.flyway.locations: classpath:db/migration` with no profile split, and the project has no active profile at all. The README's "any real deployment must delete or rotate these accounts" is an unenforced honour-system note. The correct fix (a separate `classpath:db/demo` location enabled only under a `local`/`demo` profile) requires a profile strategy this project has not yet defined — the same blocker as open epic-1 retro action item 10 (JWT-secret fail-fast guard + Postgres-credentials deferred item, both awaiting "a real deployment target/profile strategy"). Bundle all three when that strategy lands. Not a Milestone 1 demo risk: the only databases in play are a local Docker volume and throwaway Testcontainers instances.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-seeded-staff-demo-accounts.md`
  summary: No CI runs the backend test suite, so the backend suite (76 tests as of Story 2.1) only executes when someone runs `mvn test` locally with a Docker daemon up.
  evidence: Review-loop finding (verification-gap, 1st run). `.github/` contains only Java-upgrade tooling artifacts and no `workflows/` directory; `CONTRIBUTING.md:161` still lists "`.github/workflows/` — CI за build + тестове" as an unchecked TODO. Pre-existing and repo-wide, surfaced incidentally by this story rather than caused by it — but every story that adds Testcontainers-backed tests raises the cost of the gap.
  status: RESOLVED 2026-08-28 — `.github/workflows/ci.yml` added (see `spec-ci-pipeline.md`), running the full backend suite (now 76 tests) and the frontend typecheck/build on every PR and push to `main`/`dev`.

## Deferred from: CI pipeline review (2026-08-28)

- source_spec: `_bmad-output/implementation-artifacts/spec-ci-pipeline.md`
  summary: On a failing CI run, no test-report artifacts (Surefire/JUnit XML) are uploaded and no annotation/summary step surfaces which test failed — only the raw console log.
  evidence: Review-loop finding (blind-hunter). Adding structured reporting (e.g. a JUnit-report annotation action) is a real improvement but a distinct piece of work with its own choices (which action, retention policy); not needed for CI to exist and catch regressions in the first place.

- source_spec: `_bmad-output/implementation-artifacts/spec-ci-pipeline.md`
  summary: The Testcontainers `postgres:18` image is re-pulled from Docker Hub on every CI run with no layer/image caching, adding run time and exposure to Docker Hub's anonymous-pull rate limit on shared GitHub-hosted runner IPs.
  evidence: Review-loop finding (blind-hunter). Inherent to using Testcontainers in CI at all, not something this workflow file introduces or can fix alone — would need a registry mirror or a self-hosted runner with a warm image cache. Revisit if CI runs start failing/slowing from rate-limiting in practice.

- source_spec: `_bmad-output/implementation-artifacts/spec-ci-pipeline.md`
  summary: The frontend has no lint/format tooling (no ESLint/Prettier in `package.json` or anywhere else) and therefore no lint step in CI.
  evidence: Review-loop finding (blind-hunter). Pre-existing gap, not caused by adding CI — the workflow can only run scripts that exist. Installing and configuring lint tooling is its own task with its own rule-set decisions.

- source_spec: `_bmad-output/implementation-artifacts/spec-ci-pipeline.md`
  summary: The frontend job never runs tests — `frontend/package.json` has no `test` script, so CI only typechecks and builds; a frontend logic regression is caught solely by manual QA.
  evidence: Review-loop finding (blind-hunter). No frontend test framework (Vitest, React Testing Library, etc.) is installed yet. Adding one is a separate, non-trivial decision (framework choice, first test conventions) beyond wiring up CI for what already exists.
  status: RESOLVED 2026-08-28 — Story 2.2 landed the Vitest/RTL toolchain in the same merge window; `ci.yml`'s frontend job now runs `npm test` between typecheck and build. See the Story 2.2 entry below.

- source_spec: `_bmad-output/implementation-artifacts/spec-ci-pipeline.md`
  summary: `.github/PULL_REQUEST_TEMPLATE.md` still does not exist, even though it's the next item on `CONTRIBUTING.md` §6's checklist right after CI.
  evidence: Review-loop finding (blind-hunter). A distinct, independently shippable deliverable (its own checklist content to design) — kept out of this change's scope per the workflow's single-goal rule.

- source_spec: `_bmad-output/implementation-artifacts/spec-ci-pipeline.md`
  summary: No dependency/security scanning (`npm audit`, OWASP dependency-check, Dependabot config) exists anywhere under `.github/`.
  evidence: Review-loop finding (blind-hunter). Real, but a separate concern with its own tooling choice and noise-tuning; out of scope for a first CI pipeline that only needed to wire up the build/test commands that already exist.

## Deferred from: Story 2.2 (2026-08-28)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-role-based-post-login-routing.md`
  summary: After Story 2.2 a logged-in CLIENT lands on a bare `features/shells/client/ClientShell.tsx` stub instead of a quote flow, because Epic 1's quote-flow frontend was never built — only the backend `POST /api/v1/quotes` endpoint exists.
  evidence: Story 2.2 deliberately ships the client shell as a bare route target at `/` (Story 2.3 adds real shell content and chrome; the client shell's *actual* home is Epic 1's quote form/result view). This extends the pre-existing gap already recorded above in this file's `spec-1-5-quote-calculation-with-transparent-breakdown.md` entry ("No end-to-end frontend exists for quote calculation") — no quote-flow frontend story exists anywhere in `epics.md`. Turning this into an `epics.md` story is a PM decision, not Story 2.2's to make. RESOLVED 2026-08-28 by Story 1.7 (`spec-1-7-client-quote-flow-submit-and-see-the-breakdown.md`, merged as #21): `ClientShell` now renders `QuoteForm`, so a logged-in CLIENT lands on the real quote flow. The root Story 1.5 entry above records the same underlying gap and is closed by the same change.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-role-based-post-login-routing.md`
  summary: The new frontend Vitest suite (`frontend/`, `npm test`) has no CI runner — like the backend `mvn test` suite (see the Story 2.1 entry above), it only executes when someone invokes it by hand.
  evidence: Same root gap as the Story 2.1 verification-gap finding: `.github/` has no `workflows/` directory and `CONTRIBUTING.md` still lists CI as an unchecked TODO. Story 2.2 ships the first frontend test toolchain, so the gap now covers two independent suites (JVM + Node) that both rely on the honour system. Bundle a `frontend` job into the same CI workflow whenever the backend one is set up.
  status: RESOLVED 2026-08-28 — exactly this bundling, done in the same merge window. `ci.yml`'s frontend job runs `npm test` (`vitest run`) between typecheck and build.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-role-based-post-login-routing.md`
  summary: `frontend/src/app/router.tsx` has no `errorElement` and no catch-all `*` route — the table grew from 2 routes to 7, and a mistyped/unknown path now drops the user on React Router's raw default error screen.
  evidence: Not in Story 2.2's I/O matrix (which only covers the four role routes, the auth screens, and direct staff-URL visits). Wants a `NotFound` screen (Story 2.3-style copy) plus an `errorElement` on the `RootLayout` route so route errors render in-shell. A deliberate error-UX decision for Story 2.3 or a PM call, not a one-liner to bolt on here.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-role-based-post-login-routing.md`
  summary: The first frontend test toolchain ships with no coverage story — no `@vitest/coverage-v8`, no `test:coverage` script, no `coverage/` entry in `.gitignore`, no thresholds.
  evidence: Story 2.2's scope was the routing behaviour and the toolchain to pin it, not a coverage regime. Worth a deliberate testing-strategy decision (which provider, what thresholds, enforce in CI or advisory-only) before Stories 2.3, 2.4, and Epic 3 add substantially more frontend code and tests.

## Deferred from: PR template review (2026-08-28)

- source_spec: none
  summary: `.github/PULL_REQUEST_TEMPLATE.md` has no reminder of the optional release-tag step (`git tag -a v0.2.0-epic2 ...`) that `CONTRIBUTING.md` §3a lists for a `dev → main` promotion.
  evidence: Review-loop finding (blind-hunter). Tagging happens after merge, by whoever merges the release PR — not something the PR author self-checks before opening it, so it doesn't fit the template's pre-submission checklist shape. Worth a one-line callout in §3a's own text instead, if this keeps getting missed in practice.

## Deferred from: Story 1.7 review (2026-08-28)

- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-client-quote-flow-submit-and-see-the-breakdown.md`
  summary: `QuoteForm`'s `installments` field is a free-typed `<input type="number">` (`min=1 max=4`), even though the backend only ever accepts `{1, 2, 4}` — `3` is in the HTML range but always server-rejects.
  evidence: Review-loop finding (blind-hunter). A `<select>` restricted to the three real values would prevent the round trip entirely, but the frozen I/O Matrix's "Unsupported installments" row is specifically demonstrated today by typing `3` into this input — restricting the control would make that row's current test unreachable via the UI and needs a coordinated spec/test change, not a standalone patch.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-client-quote-flow-submit-and-see-the-breakdown.md`
  summary: The form's `<form noValidate>` disables the browser's enforcement of the visible `required`/`min`/`max` attributes; a blank numeric field becomes `Number('') === 0` and is sent as a real value.
  evidence: Review-loop finding (blind-hunter). Not a correctness bug — every such case (driverAge=0, engineCc=0, installments=0, blank regionCode) already round-trips to the exact bean-validation field error the frozen I/O Matrix expects, verified by an existing passing test. Purely an avoidable extra network round trip; client-side pre-validation would need to exactly mirror the backend's constraints to be worth adding.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-client-quote-flow-submit-and-see-the-breakdown.md`
  summary: No test exercises `QuoteForm`'s `cancelledRef` unmount guard (a response resolving after the component unmounts should not call any state setter).
  evidence: Review-loop finding (blind-hunter). The mechanism is documented and mirrors `LoginForm.tsx`'s identical, equally-untested guard — a pre-existing coverage gap in the pattern this story copied, not something newly introduced here specifically.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-client-quote-flow-submit-and-see-the-breakdown.md`
  summary: Field-level error `<p role="alert">` elements in `QuoteForm.tsx` have no `id`, and their `<input>`s carry no `aria-describedby`/`aria-invalid` pointing at them — a screen-reader user tabbing back into an already-errored field gets no re-announcement.
  evidence: Review-loop finding (blind-hunter + verification-gap, both independently). Mirrors an identical, pre-existing gap in `LoginForm.tsx`/`RegisterForm.tsx` — worth a single a11y pass across all three forms together rather than a one-off fix here.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-client-quote-flow-submit-and-see-the-breakdown.md`
  summary: The numeric inputs (`driverAge`, `engineCc`, `installments`) set no `step`, so a value like `25.5` can be typed and, combined with `noValidate`, is sent as-is — the backend's `Integer`-typed DTO fields would then fail JSON deserialization rather than a clean bean-validation field error.
  evidence: Review-loop finding (blind-hunter). Not covered by the frozen I/O Matrix's "Bean-validation failure" row (that row covers valid-typed values outside range, not type-mismatched JSON). Fixing properly needs either stricter client-side parsing or confirming/improving the backend's malformed-body error path — a small design decision, not a one-line patch.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-client-quote-flow-submit-and-see-the-breakdown.md`
  summary: `QuoteForm` has no guard against a rapid double-submit (e.g. double Enter) firing `handleSubmit` twice before the `disabled` attribute re-renders, potentially sending two concurrent `POST /api/v1/quotes` requests.
  evidence: Review-loop finding (edge-case-hunter). Mirrors `LoginForm.tsx`'s identical structure and identical latent gap — pre-existing pattern this story copied, not newly introduced. Worth a shared fix (e.g. a `phase === 'submitting'` guard at the top of `handleSubmit`) applied to all three forms together.

## Deferred from: quote input bounds review (2026-08-28)

- source_spec: `_bmad-output/implementation-artifacts/spec-quote-input-bounds.md`
  summary: `QuoteForm`'s `driverAge`/`engineCc` inputs give the user no visible hint of the new 100/8000 ceilings before they submit — `noValidate` suppresses the browser's native tooltip for `max`, so an over-ceiling value only surfaces as an error after a round trip to the server.
  evidence: Review-loop finding (blind-hunter). Same root cause as the already-deferred `noValidate` UX round-trip finding from Story 1.7's own review — worth a single client-side pre-validation pass across all bounded fields together, not a one-off fix here.

## Deferred from: minimal styling review (2026-08-28)

- source_spec: none
  summary: `index.css` gives error text (`role="alert"`) a distinct red color, but nothing changes the associated `<input>`'s border/background when it's currently invalid — an erroring field looks identical to a valid one unless the red text below it is noticed.
  evidence: Review-loop finding (blind-hunter). Fixable with a `:has()` selector (e.g. `div:has([role="alert"]) input`, well-supported in evergreen browsers) or an `aria-invalid` attribute the components would need to start setting — either way a small design decision (how prominent, which mechanism) beyond this pass's "target what's already there" scope.

- source_spec: none
  summary: `index.css` styles forms/buttons via bare element selectors (`form div`, `button`) with no class-based scoping or escape hatch — any future non-field `div` inside a form, or a secondary/tertiary button (e.g. a header logout button), will silently inherit this styling.
  evidence: Review-loop finding (blind-hunter). A deliberate trade-off for this pass (zero JSX/className changes, matching the "minimal, no framework" scope) — resolving it properly means introducing component-scoped classes across every form, a bigger, more invasive change than "add a stylesheet."

- source_spec: none
  summary: No "skip to content" link exists — `RootLayout`'s nav (Register/Login/Health) sits before `<main>` with no bypass, so keyboard/screen-reader users tab through it on every single page before reaching the actual screen.
  evidence: Review-loop finding (blind-hunter). Real a11y gap, but fixing it needs a new JSX element in `RootLayout.tsx`, which this pass deliberately avoided (CSS-only diff). Worth doing alongside a broader a11y pass (see the `aria-describedby` finding already deferred from Story 1.7's review).

- source_spec: none
  summary: No required-field indicator (e.g. `*`) exists even though every current input across `LoginForm`/`RegisterForm`/`QuoteForm` is `required`.
  evidence: Review-loop finding (blind-hunter). Low value today (everything happens to be required, so nothing is being distinguished in practice) and a pure-CSS solution would need a fragile `label:has(+ div input:required)`-style selector; revisit if/when a genuinely optional field appears.

## Deferred from: Story 2.3 (2026-08-28)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-placeholder-screens-for-agent-liquidator-and-administrator.md`
  summary: No per-route `document.title` — `/agent`, `/liquidator` and `/administrator` all keep the single `index.html` title, so browser tabs, history entries and bookmarks are indistinguishable across the role areas.
  evidence: Review-loop finding (blind-hunter). Sharp for a story whose entire deliverable is *labeling*: the screens are now distinct on-page but identical in the tab strip. Not fixed here because title management is cross-cutting — it belongs to every route (auth screens, `/health`, the client shell), not just the three staff shells, and adding an effect to components the spec defines as "static and non-interactive" would contradict this story's own contract. Wants one deliberate decision on where route titles live (a `<title>` per route element, a router-level effect, or a small `useDocumentTitle` hook) applied across the whole table.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-placeholder-screens-for-agent-liquidator-and-administrator.md`
  summary: "Staff role" (`Exclude<Role, 'CLIENT'>`) is a domain concept currently invented inside `frontend/src/features/shells/shells.test.tsx` rather than exported from `app/roleHome.ts`.
  evidence: Review-loop finding (blind-hunter). `roleHome.ts` declares itself "the frontend's single source of truth for which roles exist", and Story 2.4's route guard needs exactly the same staff/client distinction to decide who may reach which route. Not done here because Story 2.3's frozen spec forbids touching `roleHome.ts` or `router.tsx`. Story 2.4 should export `StaffRole` + `STAFF_ROLES` from `roleHome.ts` and have the test import them instead of re-deriving the filter.
