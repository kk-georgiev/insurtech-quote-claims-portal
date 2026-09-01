---
title: 'Story 9.1: OpenAPI Documentation'
type: 'feature'
created: '2026-09-01'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '4f739c3d8e8cd0dbf478c63b3710c5827cf3ed00'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** All 8 `/api/v1` endpoints across 3 controllers exist only as Java source — a reviewer or teammate has to read controller code to learn the contract (FR-M3-14).

**Approach:** Add `springdoc-openapi-starter-webmvc-ui` 3.1.0 (verified compatible with Spring Boot 4.1.x — see Design Notes). No controller code changes: springdoc infers request/response shapes from existing Spring MVC method signatures and DTOs automatically. Add one `@Configuration` bean for API metadata, including the bonus-malus provenance disclaimer, and permit springdoc's own endpoints through `SecurityConfig`.

## Boundaries & Constraints

**Always:**
- Pin `springdoc-openapi-starter-webmvc-ui` to `3.1.0` explicitly (not in the Spring Boot 4.1.1 BOM) — matches the existing `jjwt-*` precedent for non-BOM dependencies.
- All 8 existing endpoints (`AuthController`: register, login; `QuoteController`: calculate, getById, list, accept; `PolicyController`: list, getById) must appear in the generated docs with their real request/response DTOs — zero controller/DTO changes needed to achieve this.
- The OpenAPI info description states, in the same substance as `BonusMalusClass.java`'s javadoc and the frontend `bonusMalusNote` string, that the bonus-malus scale is this project's own demo model, not official or regulatorily determined Bulgarian market values (NFR-8).
- `/v3/api-docs/**` and `/swagger-ui/**` (springdoc's own routes) are added to `SecurityConfig`'s existing public permit-list (alongside `/actuator/health`) — a docs endpoint for reviewers/teammates, not user data; this milestone explicitly excludes production hardening (see `deferred-work.md`).
- No behavior change to any existing endpoint's request handling, response shape, status codes, or auth requirements.

**Ask First:** none identified.

**Never:**
- Do not downgrade Spring Boot to force compatibility with an older springdoc release — already checked: 3.1.0 targets 4.1.0, project is on the patch release 4.1.1 (same minor line).
- Do not add `@Operation`/`@Schema` annotations to every controller method as a first pass — springdoc's auto-inference already satisfies the AC; only add manual annotations if the self-review in Verification finds a gap auto-inference can't cover (e.g. the bonus-malus description, which needs its own annotation since it's not inferable from the enum's Java name alone).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Fetch generated spec | `GET /v3/api-docs`, no auth header | 200, JSON OpenAPI document listing all 8 endpoints with schemas | N/A |
| Swagger UI reachable | `GET /swagger-ui/index.html`, no auth header | 200, renders UI backed by the same spec | N/A |
| Existing endpoints unaffected | `POST /api/v1/quotes` with a valid body, valid token | Identical 201 response to pre-change behavior | N/A |
| Public auth endpoints still public | `POST /api/v1/auth/login` | Still reachable with no token, unchanged | N/A |

</frozen-after-approval>

## Code Map

- `backend/pom.xml:24-120` -- `<dependencies>` block; add `springdoc-openapi-starter-webmvc-ui` after the existing groups, following the blank-line + explanatory-comment + explicit-`<version>` convention already used for `jjwt-*` (lines 65-81).
- `backend/src/main/java/com/motorinsurance/shared/config/ClockConfig.java` -- template to follow for the new `OpenApiConfig`: package `shared.config`, class-level Javadoc citing Story 9.1, single `@Bean`-annotated factory method.
- `backend/src/main/java/com/motorinsurance/auth/config/SecurityConfig.java:76,88-93` -- `PUBLIC_POST_ENDPOINTS` array and the `authorizeHttpRequests` chain; add a `GET` permit rule for `/v3/api-docs/**` and `/swagger-ui/**`, following the same narrow-by-method pattern already used for `/actuator/health`.
- `backend/src/main/java/com/motorinsurance/pricing/domain/BonusMalusClass.java:16-21` -- canonical provenance javadoc wording to echo in the OpenAPI description.
- `frontend/src/i18n/en.json:58`, `frontend/src/i18n/bg.json:58` -- existing `bonusMalusNote` disclaimer strings; match substance, not literal translation (the OpenAPI doc is English-only, no i18n).
- `backend/src/main/resources/application.yml` -- no changes required; springdoc's defaults (`/v3/api-docs`, `/swagger-ui.html`) need no override for this story's scope.

## Tasks & Acceptance

**Execution:**
- [x] `backend/pom.xml` -- add `springdoc-openapi-starter-webmvc-ui:3.1.0` dependency -- brings OpenAPI generation with zero controller changes needed for shape inference
- [x] `backend/src/main/java/com/motorinsurance/shared/config/OpenApiConfig.java` (new) -- `@Configuration` class with one `@Bean OpenAPI` exposing API title/version and a description carrying the bonus-malus provenance disclaimer -- gives the generated docs the one piece of context auto-inference cannot supply
- [x] `backend/src/main/java/com/motorinsurance/auth/config/SecurityConfig.java` -- permit `GET /v3/api-docs/**` and `GET /swagger-ui/**` -- without this, springdoc's own routes 401 under the existing `anyRequest().authenticated()` catch-all
- [x] `backend/src/test/java/com/motorinsurance/shared/config/OpenApiConfigTest.java` (new) -- integration test asserting `GET /v3/api-docs` returns 200 and the response body's `paths` object contains all 8 known endpoint paths -- pins the AC ("every endpoint appears") as a regression-checkable fact, not a manual eyeball. Strengthened during Matrix Test Audit to also assert `components`/`QuoteResponse` schema presence, the bonus-malus disclaimer text, and a second test for Swagger UI reachability (the I/O matrix's row 2, initially uncovered by an automated test).

**Acceptance Criteria:**
- Given the app running, when `GET /v3/api-docs` is called with no auth header, then it returns 200 with a valid OpenAPI document listing all 8 existing endpoints and their real request/response schemas (FR-M3-14).
- Given the generated documentation, when the bonus-malus scale is described anywhere in it, then the description states it is the project's own demo data, not official or regulatorily determined Bulgarian market values (NFR-8).
- Given any existing endpoint, when it is called exactly as before this change, then its response (status, body shape, auth requirement) is byte-for-byte unchanged.

## Spec Change Log

## Design Notes

**Why `springdoc-openapi-starter-webmvc-ui` 3.1.0, not assumed:** verified directly against the springdoc-openapi GitHub releases page (not a secondary source) — 3.1.0's release notes state "Upgrade Spring Boot to version 4.1.0". This project runs Spring Boot 4.1.1, a patch release in the same minor line, so binary/API compatibility holds. Per the story's own AC, if no compatible release existed this story would be deferred rather than downgrading the framework — that gate is satisfied, so the story proceeds.

**Why no `@Operation`/`@Schema` annotations on controllers:** springdoc-openapi generates the OpenAPI document by reflecting over Spring MVC's own `@RequestMapping`/`@PathVariable`/`@RequestBody` annotations and the DTOs' field types — the same information already fully present. Adding manual annotations everywhere would duplicate that information with no behavioral gain; only the one piece of context that can't be inferred (the bonus-malus provenance note) needs an explicit description, on the `OpenApiConfig` bean's `Info` object rather than scattered `@Schema` annotations, since it's a project-wide disclaimer rather than a per-field constraint.

**Why the docs endpoints go public, matching `/actuator/health`:** this milestone's architecture spine and PRD explicitly exclude production-hardening as a non-goal (see `deferred-work.md`'s Story 4.1 entry on missing security headers, accepted as out of scope for the same reason). A reviewer/teammate reading the API contract is exactly this story's own user story — gating it behind a CLIENT/staff JWT would defeat the purpose for a reviewer who has no test account.

## Verification

**Commands:**
- `mvn clean test` -- expected: all existing tests still pass plus the new `OpenApiConfigTest`, 0 failures
- `mvn spring-boot:run` then `curl -s http://localhost:8080/v3/api-docs | jq '.paths | keys'` -- expected: all 8 endpoint paths listed
- `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/swagger-ui/index.html` -- expected: `200`

**Manual checks (if no CLI):**
- Open `http://localhost:8080/swagger-ui/index.html` in a browser, confirm all 3 controllers' endpoints render with expandable request/response schemas, and the bonus-malus disclaimer is visible in the API description.

## Suggested Review Order

**API metadata & provenance disclaimer**

- Entry point: the one new bean supplying what auto-inference can't — title, version, and the bonus-malus provenance note (NFR-8).
  [`OpenApiConfig.java:22`](../../backend/src/main/java/com/motorinsurance/shared/config/OpenApiConfig.java#L22)

- The disclaimer text itself, echoing `BonusMalusClass.java`'s existing javadoc wording.
  [`OpenApiConfig.java:27`](../../backend/src/main/java/com/motorinsurance/shared/config/OpenApiConfig.java#L27)

**Security: exposing springdoc's own routes**

- The permit-list, widened during review to also cover springdoc's conventional `/swagger-ui.html` entry (not just `/swagger-ui/**`) after a live 401 was caught.
  [`SecurityConfig.java:83`](../../backend/src/main/java/com/motorinsurance/auth/config/SecurityConfig.java#L83)

- Where the permit-list is wired into the existing `authorizeHttpRequests` chain, alongside `/actuator/health`.
  [`SecurityConfig.java:99`](../../backend/src/main/java/com/motorinsurance/auth/config/SecurityConfig.java#L99)

**Dependency**

- The one new dependency, pinned explicitly since it's outside the Spring Boot 4.1.1 BOM — see its comment for the compatibility check.
  [`pom.xml:95`](../../backend/pom.xml#L95)

**Tests**

- Full-stack proof all 8 endpoints appear with real schemas, plus the bonus-malus disclaimer text, against a live Testcontainers-backed app.
  [`OpenApiConfigTest.java:41`](../../backend/src/test/java/com/motorinsurance/shared/config/OpenApiConfigTest.java#L41)

- Swagger UI reachable at its direct path.
  [`OpenApiConfigTest.java:76`](../../backend/src/test/java/com/motorinsurance/shared/config/OpenApiConfigTest.java#L76)

- Swagger UI reachable at its conventional redirect entry point — the test added for the review-loop patch.
  [`OpenApiConfigTest.java:87`](../../backend/src/test/java/com/motorinsurance/shared/config/OpenApiConfigTest.java#L87)
