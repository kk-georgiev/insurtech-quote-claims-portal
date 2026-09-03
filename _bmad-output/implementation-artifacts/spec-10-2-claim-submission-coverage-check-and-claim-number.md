---
title: 'Story 10.2 — Claim Submission, Coverage Check and Claim Number'
type: 'feature'
created: '2026-09-02'
status: 'done'
baseline_commit: '30dd91929b095a0f949fd01cbb79f2e94b39129d'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-10-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A policy is inert until a client can file a claim against it. Story 10.1 built the storage/validation port but persists nothing; this story is the first write path into a new `claim` module, and it must reject a photo upload above Spring's own multipart limit as a translated 400, not the opaque 500 recorded in `deferred-work.md`.

**Approach:** A new `claim` module (domain/persistence/application/api) mirroring `policy`'s structure exactly. `POST /api/v1/claims` (`multipart/form-data`) validates ownership via the existing `policyService.getById`, checks the incident date against the policy's coverage window, validates+stores photos via Story 10.1's `AttachmentValidator`/`Storage`, and persists a `Claim` plus its `Attachment` rows in one transaction. A dedicated `MaxUploadSizeExceededException` handler in `GlobalExceptionHandler` closes the Story 10.1 gap. P-2 (extract `currentUserId`) is done alongside this.

## Boundaries & Constraints

**Always:**
- `claim` reaches `policy` only through `policy.application` (`PolicyService`/`PolicyView`/`PolicyNotFoundException`) — never `policy.domain` or `policy.persistence`.
- Initial status is `SUBMITTED`, set by the backend only; no caller-supplied status field exists anywhere in the request.
- Every file is validated in full before any byte is written; on any later failure, already-stored bytes are best-effort deleted (logged, not required for correctness) and the whole request fails — no claim row, no attachment row.
- Claim number is `CL-{year}-{8 digits}` from a dedicated `claim_number_seq`, never max+1.
- New codes (`CLAIM_INCIDENT_OUTSIDE_COVERAGE` 409, `CLAIM_INCIDENT_DATE_IN_FUTURE` 400) ship with `bg`+`en` entries in this change; `check-error-code-contract.mjs` must pass.
- `currentUserId(Authentication)` is extracted once to a shared helper and used by `QuoteController`, `PolicyController`, and the new `ClaimController` — no third copy.

**Ask First:** none identified — no product ambiguity blocks this story.

**Never:**
- No `CLAIM_NOT_FOUND` code or any GET/list/detail endpoint — Story 10.4.
- No status-history table, no `@Version`/optimistic locking — Story 11.1 (`V12`).
- No frontend (FNOL form) — Story 10.3.
- No change to `AttachmentValidator`'s allowlist/size-cap rules — reuse as-is; only add a `validateCount(int)` entry point and filename sanitization (both already flagged in `deferred-work.md` as this story's job).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Happy path, with photos | Own policy, incident date inside coverage, 2 valid JPEGs | 201, claim with `SUBMITTED`, claim number, 2 attachment entries | N/A |
| No photos | Own policy, valid dates, no `attachments` part | 201, empty attachments list | N/A |
| Someone else's policy | `policyId` belongs to another customer | 404, never 403 | `POLICY_NOT_FOUND` |
| Incident before coverage_start | Date one day before | Rejected | `CLAIM_INCIDENT_OUTSIDE_COVERAGE` (409) |
| Incident after coverage_end | Date one day after | Rejected | `CLAIM_INCIDENT_OUTSIDE_COVERAGE` (409) |
| Incident on coverage_end (boundary) | Date == coverage_end | Accepted (inclusive) | N/A |
| Incident on an EXPIRED policy, inside its window | Policy status derives to EXPIRED today | Accepted | N/A |
| Future incident date | Date after today (business zone) | Rejected, field `incidentDate` | `CLAIM_INCIDENT_DATE_IN_FUTURE` (400) |
| Description too short/long | Below/above configured bounds | Rejected, field `description` | `SHARED_VALIDATION_ERROR` (400) |
| One bad file in a batch | 3 valid + 1 renamed PDF | Nothing written, no claim row | `ATTACHMENT_UNSUPPORTED_TYPE` (400) |
| File over Spring's own multipart cap | Single file > `spring.servlet.multipart.max-file-size` | Clean 400, not 500 | `ATTACHMENT_TOO_LARGE` (400) |
| DB failure after files stored | Simulated persistence failure post-storage | Stored bytes best-effort deleted, original error surfaces | (unchanged) |

</frozen-after-approval>

## Code Map

- `backend/.../policy/application/PolicyService.java:148-154` -- `getById(id, customerId)` — reuse verbatim for owner-scoped policy lookup; already throws `PolicyNotFoundException` (404).
- `backend/.../policy/application/PolicyView.java` -- carries `policyNumber`, `coverageStart`, `coverageEnd` — everything the coverage check and the copied `policyNumber` need.
- `backend/.../quote/application/QuoteAcceptanceTransaction.java:74-86` -- pattern for a business-clock-aware date check + dedicated `ApiException` (mirror for both new claim exceptions).
- `backend/.../policy/application/PolicyService.java:85-136`, `policy/domain/PolicyNumber.java`, `policy/persistence/PolicyRepository.java:56-57` -- mirror exactly for `ClaimSubmissionService`/`ClaimNumber`/`ClaimRepository.nextClaimNumberValue()`.
- `backend/.../shared/storage/{Storage,AttachmentValidator,StoredFile,ImageType}.java` -- reuse as-is; `AttachmentValidator.validate(List<Candidate>)` returns `ValidatedAttachment` (`content()`, `type()`, `displayFilename()`).
- `backend/.../shared/api/{ApiException,ApiError,GlobalExceptionHandler}.java` -- base classes; add one `@ExceptionHandler(MaxUploadSizeExceededException.class)` to `GlobalExceptionHandler` (400, reuse `"ATTACHMENT_TOO_LARGE"` literal, field `attachments`).
- `backend/.../quote/api/QuoteController.java:105-107`, `policy/api/PolicyController.java:57-59` -- the two byte-for-byte `currentUserId` copies to delete, replaced by a static import of the new shared helper.
- `backend/src/main/resources/db/migration/V9__create_policies_table.sql` -- header/style template for `V10`/`V11`.
- `backend/src/main/resources/application.yml:40-61` -- no new keys needed; reuse `storage.attachment.*`.
- `frontend/src/i18n/{bg,en}.json` -- `errors.codes` object, insert the two new codes near `POLICY_NOT_FOUND`.
- `backend/src/test/java/com/motorinsurance/policy/api/PolicyControllerTest.java` -- full-stack Testcontainers + `RestClient` pattern (`@SpringBootTest(webEnvironment=RANDOM_PORT)`, `@Container` Postgres) to mirror for `ClaimControllerTest`, adapted to `MultipartBodyBuilder` for the multipart body.
- `backend/src/test/java/com/motorinsurance/shared/storage/AttachmentValidatorTest.java` -- `jpeg()`/`png()`/`pdf()` byte-fixture helpers to mirror in the new test.

## Tasks & Acceptance

**Execution:**
- [x] `backend/.../shared/api/CurrentUser.java` -- new; `public static UUID currentUserId(Authentication)` -- the P-2 extraction.
- [x] `backend/.../quote/api/QuoteController.java`, `policy/api/PolicyController.java` -- delete the private `currentUserId` method, static-import the shared one instead.
- [x] `backend/.../shared/api/GlobalExceptionHandler.java` -- add the `MaxUploadSizeExceededException` handler described above.
- [x] `backend/.../shared/storage/AttachmentValidator.java` -- add `public void validateCount(int count)` (same `TooManyAttachmentsException`, usable before bytes are read); sanitize+cap `displayName()` (strip control chars, cap 255 chars) before it can reach a bounded DB column.
- [x] `backend/.../claim/domain/{ClaimStatus,ClaimNumber,Claim,Attachment}.java` -- new, mirroring `policy.domain`'s style; `ClaimStatus` has all 5 MVP values (BA §8.3), no transition logic (Story 11.1).
- [x] `backend/.../claim/persistence/{ClaimRepository,AttachmentRepository}.java` -- new; `ClaimRepository.nextClaimNumberValue()` native query.
- [x] `backend/.../claim/application/{ClaimView,AttachmentView,SubmitClaimCommand,ClaimSubmissionService,ClaimIncidentOutsideCoverageException,ClaimIncidentDateInFutureException}.java` -- new.
- [x] `backend/.../claim/api/{SubmitClaimForm,ClaimController}.java` -- new; `SubmitClaimForm` is a `@Valid @ModelAttribute`-bound record (`policyId` UUID, `incidentDate` LocalDate, `description` `@NotBlank @Size(min=10,max=2000)`, `location` `@NotBlank @Size(min=2,max=200)`); controller takes `attachments` as a separate `@RequestParam(required=false) List<MultipartFile>`, calls `attachmentValidator.validateCount(...)` before converting to `Candidate`s.
- [x] `backend/src/main/resources/db/migration/V10__create_claims_table.sql` -- `claim_number_seq` + `claims` (customer_id/policy_id FKs, policy_number copy, claim_number unique, incident_date, description, location, status, submitted_at; index on customer_id and policy_id).
- [x] `backend/src/main/resources/db/migration/V11__create_attachments_table.sql` -- `attachments` (claim_id FK ON DELETE CASCADE, storage_key unique, content_type, size_bytes, sha256_hex, display_filename, uploaded_at; index on claim_id).
- [x] `frontend/src/i18n/bg.json`, `en.json` -- add `CLAIM_INCIDENT_OUTSIDE_COVERAGE`, `CLAIM_INCIDENT_DATE_IN_FUTURE`.
- [x] `frontend/src/i18n/errorMessages.test.ts` -- add the two new codes to the hand-maintained `CODES` list (epic-3 item 27); not anticipated in planning, caught by running the frontend suite.
- [x] `backend/src/test/java/com/motorinsurance/claim/api/ClaimControllerTest.java` -- new full-stack test covering the I/O matrix above, including the real Spring multipart-size failure and cross-client isolation.
- [x] `backend/src/test/java/com/motorinsurance/claim/application/ClaimSubmissionServiceTest.java` -- new mocked unit test for the one matrix row HTTP can't reach deterministically: a DB failure after a photo is already stored.
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- append `status: RESOLVED 2026-09-02 (Story 10.2)` lines to the three Story-10.1 entries this closes (`MaxUploadSizeExceededException`, count-cap-before-materialization, unbounded/unsanitized filename).
- [x] `_bmad-output/implementation-artifacts/sprint-status.yaml` -- fix stale `10-1-...: review` → `done`; mark `epic-8-retro-item-50` done; set this story's own key to `done`.

**Acceptance Criteria:** see the frozen I/O matrix above plus epics-milestone-4.md's own Story 10.2 AC block (already read in full this session) — no restatement needed.

## Spec Change Log

- Finding (implementation, not review): `RestClient` + `MultipartBodyBuilder` throws `NoClassDefFoundError: org/reactivestreams/Publisher` on this project's classpath (no reactor-core/reactive-streams jar — pure servlet stack, no WebFlux). Amended: `ClaimControllerTest` encodes the multipart body by hand as a `byte[]` with a manually-set `Content-Type: multipart/form-data; boundary=...` header instead. Avoids: a test suite that cannot run at all. KEEP: the hand-rolled encoder is self-contained in the test file; no production code or dependency was added to work around it.
- Finding (implementation): `attachments.sha256_hex CHAR(64)` (as originally drafted) failed Hibernate schema validation against the plain `String sha256Hex` entity field, which defaults to `VARCHAR`. Amended: `V11` uses `VARCHAR(64)` instead. Avoids: every `@SpringBootTest` in the whole suite failing to start (schema validation runs for the whole persistence unit at context load, not just the touched table).
- Finding (implementation): the frontend's hand-maintained `errorMessages.test.ts` `CODES` list (epic-3 retro item 27) isn't checked by the Java-side `check-error-code-contract.mjs` script it duplicates, but does fail its own test once two new backend codes exist. Amended: added `CLAIM_INCIDENT_OUTSIDE_COVERAGE`/`CLAIM_INCIDENT_DATE_IN_FUTURE` there too. KEEP for future stories: the Node contract script alone is not sufficient proof the frontend suite is green when new error codes are added — run `npm test` too.

## Design Notes

**Why `@ModelAttribute` record over raw `@RequestParam`s.** Spring Framework 6.1+ (in the Spring Boot 4.1.1 / Framework 7 BOM this project pins) binds multipart form fields to a single-constructor record via `@ModelAttribute`, and a bound record's `@Valid` failure already routes through the existing `handleMethodArgumentNotValid` — same shape as JSON body validation, zero new exception-handling code, and record component names survive reflection regardless of compiler flags (unlike normal constructor parameter names).

**Why `CLAIM_INCIDENT_DATE_IN_FUTURE` is a new code, not a reuse of `SHARED_VALIDATION_ERROR`.** It depends on the injected business-zone `Clock` (Architecture AD-6), exactly like `QUOTE_COVERAGE_START_IN_PAST` — that precedent always mints a dedicated module code for a clock-aware date check rather than reusing the generic one, so this follows it rather than the epics doc's non-exhaustive AD-11 code list.

## Verification

**Commands:**
- `cd backend && mvn -q clean test` -- BUILD SUCCESS, all existing tests plus the new `claim` suite green.
- `node scripts/check-error-code-contract.mjs` -- exit 0.
- `cd frontend && npm run typecheck` -- clean (i18n catalogs are typed).
