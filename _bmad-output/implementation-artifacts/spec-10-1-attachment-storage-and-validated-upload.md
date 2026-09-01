---
title: 'Story 10.1 — Attachment Storage and Validated Upload'
type: 'feature'
created: '2026-09-01'
status: 'done'
baseline_commit: '9ea93a340ad7f87947e442149735e3198489b039'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-10-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Milestone 4 needs to accept photos with a claim, and this is the first code in the backend that takes a file from a user and later serves it back — the largest new attack surface in the project. Story 10.2 will write the claim transaction; it must not also have to invent file handling under time pressure.

**Approach:** A `Storage` port in a new `shared.storage` package with a local-filesystem adapter behind it, plus a validator that decides what a valid image *is* by reading its bytes. `shared.storage` owns the byte-level concern only — what the file is, where it lands, what it is called. Who may see it is the claim's rule and belongs to `claim` in Story 10.4. No claim entity, no table, no endpoint in this story.

## Boundaries & Constraints

**Always:**
- Content type is decided by **sniffing magic bytes**, never from the filename extension and never from the client-supplied `Content-Type` header.
- The stored filename is a **randomly generated key produced by this code**. The client-supplied filename survives only as display metadata and never reaches a filesystem path.
- The adapter resolves every final path and verifies it stays under the configured base directory before reading or writing — defence in depth even though keys are self-generated.
- Size cap, count cap and base directory are each resolved from **one configured value** via `@Value` constructor injection, validated fail-fast at startup (the `PolicyService.java:57-64` idiom), never a literal at a call site.
- New error codes ship with their `bg` **and** `en` entries in the same change (`ATTACHMENT_UNSUPPORTED_TYPE`, `ATTACHMENT_TOO_LARGE`, `ATTACHMENT_TOO_MANY` — 400, all three).
- `shared` never imports a module type. The port is depended on; the adapter is not.

**Ask First:**
- Adding any new Maven dependency. The plan is a hand-rolled magic-byte check precisely to avoid one.
- Introducing a database table or migration. The `attachments` table belongs to Story 10.2 (`V11`); this story persists nothing.
- Changing any existing error code, or the shape of `ApiError`.

**Never:**
- No `MultipartFile` in `shared.storage` — the port takes bytes and a declared filename, so it is testable without a servlet and reusable by Milestone 5's PDF output.
- No claim entity, table, controller, endpoint or ownership check — Stories 10.2 and 10.4.
- No `ATTACHMENT_NOT_FOUND` code: nothing in this story can emit it, and the CI contract check fails on an i18n entry with no backend code just as it fails the reverse.
- No image resizing, re-encoding, thumbnailing, EXIF stripping or virus scanning.
- No directory sharding of the storage tree.
- No S3/MinIO implementation. The port exists so that stays a substitution, not a rewrite.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Valid JPEG | Bytes starting `FF D8 FF`, under the size cap | Stored; metadata returns a fresh key, `image/jpeg`, byte length, SHA-256 hex | N/A |
| Valid PNG | Bytes starting `89 50 4E 47 0D 0A 1A 0A` | Stored; `image/png` | N/A |
| Valid WebP | `RIFF` at offset 0 **and** `WEBP` at offset 8 | Stored; `image/webp` | N/A |
| PDF renamed `.jpg` | `%PDF-` bytes, filename `photo.jpg` | Rejected before any write | `ATTACHMENT_UNSUPPORTED_TYPE` (400) |
| Lying `Content-Type` | Valid PNG bytes, caller declares `image/gif` | **Stored as `image/png`** — declared type is ignored entirely | N/A |
| Truncated header | 3 bytes total | Rejected, no exception escapes as a 500 | `ATTACHMENT_UNSUPPORTED_TYPE` (400) |
| Empty file | 0 bytes | Rejected | `ATTACHMENT_UNSUPPORTED_TYPE` (400) |
| `RIFF` but not WebP | `RIFF` at 0, `AVI ` at 8 | Rejected — both markers required | `ATTACHMENT_UNSUPPORTED_TYPE` (400) |
| Over the size cap | Bytes longer than the configured cap | Rejected before any write | `ATTACHMENT_TOO_LARGE` (400) |
| Too many files | A batch longer than the configured count cap | Whole batch rejected, nothing written | `ATTACHMENT_TOO_MANY` (400) |
| One bad file in a batch | Three valid, one PDF | **Nothing is written** — the batch is validated fully before any store | `ATTACHMENT_UNSUPPORTED_TYPE` (400) |
| Key collision / traversal | Any generated key | Resolved path is under the base directory or the operation fails | `IllegalStateException` (500, not client-triggerable) |
| Base directory absent | Configured path does not exist at startup | Created on demand; the container starts healthy with no pre-existing host path | N/A |
| Cap misconfigured | Size or count cap `<= 0` at startup | Application fails to start with a message naming the property and the bad value | `IllegalArgumentException` |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/motorinsurance/shared/config/ClockConfig.java` -- canonical `@Configuration` shape: bare `@Configuration`, bare `@Bean`, long justificatory class Javadoc naming the AD and story. Mirror exactly.
- `backend/src/main/java/com/motorinsurance/shared/config/CorsConfig.java:41-42` -- the only field-level `@Value` precedent; constructor injection is preferred over it.
- `backend/src/main/java/com/motorinsurance/shared/api/ApiException.java:15-43` -- abstract base to extend. Protected ctors `(status, code, message)` at `:21` and `(status, code, message, fieldErrors)` at `:25`.
- `backend/src/main/java/com/motorinsurance/shared/api/ApiError.java:19-41` -- envelope record; nested `FieldError` at `:39`. Read-only here.
- `backend/src/main/java/com/motorinsurance/shared/api/GlobalExceptionHandler.java:133-137` -- **read-only.** One generic `ApiException` handler already maps every subclass. Do **not** add a handler; `shared` must not import concrete module exceptions.
- `backend/src/main/java/com/motorinsurance/quote/application/VehicleIdentifierRequiredException.java` -- reference for a 400 exception carrying a `FieldError`.
- `backend/src/main/java/com/motorinsurance/policy/application/PolicyService.java:56-64` -- the `@Value` constructor-injection + fail-fast-validation idiom to copy for the caps.
- `backend/src/main/resources/application.yml:40-41` -- `policy.coverage-months` block; insert the new `storage:` keys immediately after, before `spring:` at `:43`.
- `backend/src/main/resources/application.yml:43-59` -- `spring:` block; `spring.servlet.multipart.*` keys go here.
- `scripts/check-error-code-contract.mjs` -- **regex is context-blind**: matches any quoted `[A-Z]{2,}(_[A-Z]{2,})+` literal in any `.java` file and requires a matching key in both i18n catalogs. Constrains naming of every string constant in the new code.
- `frontend/src/i18n/bg.json`, `frontend/src/i18n/en.json` -- `errors.codes` object; the three new codes go in both, same commit.
- `docker-compose.yml:37-70` -- `backend` service, currently has **no** `volumes:` key. `:83-84` -- top-level `volumes:` block with the single existing `postgres_data`.
- `backend/src/test/java/com/motorinsurance/shared/config/OpenApiConfigTest.java` -- closest existing analogue for testing a `shared` component.
- `backend/src/test/java/com/motorinsurance/policy/domain/PolicyNumberTest.java` -- pure-domain test with no annotations; the shape for the sniffer and key-generator tests.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/motorinsurance/shared/storage/StoredFile.java` -- new record carrying storage key, content type, size in bytes, SHA-256 hex and original display filename -- the metadata Story 10.2 will persist; defining it here keeps this story shippable without a table.
- [x] `backend/src/main/java/com/motorinsurance/shared/storage/ImageType.java` -- new enum of the three allowed types, each knowing its MIME string and its magic-byte signature -- puts the allowlist in one place. **Constant names must not match the error-code regex** (use `JPEG`, `PNG`, `WEBP`, never `IMAGE_JPEG`), and the MIME strings are lowercase so they cannot match either.
- [x] `backend/src/main/java/com/motorinsurance/shared/storage/ImageContentSniffer.java` -- new; decides type from a byte prefix, returns empty for anything unrecognised -- the rule that a renamed PDF is caught. WebP requires both `RIFF` at 0 and `WEBP` at 8.
- [x] `backend/src/main/java/com/motorinsurance/shared/storage/Storage.java` -- new port interface: store bytes + display filename → `StoredFile`; read by key; delete by key -- `claim` will depend on this and never on the adapter.
- [x] `backend/src/main/java/com/motorinsurance/shared/storage/LocalFilesystemStorage.java` -- new `@Component` adapter; generates a UUID-based key, creates the base directory on demand, verifies the resolved path stays under the base directory, writes atomically -- the only implementation.
- [x] `backend/src/main/java/com/motorinsurance/shared/storage/AttachmentValidator.java` -- new `@Component`; enforces the allowlist via the sniffer plus the size and count caps, `@Value`-injected and fail-fast validated -- validates a whole batch before anything is stored.
- [x] `backend/src/main/java/com/motorinsurance/shared/storage/UnsupportedAttachmentTypeException.java`, `AttachmentTooLargeException.java`, `TooManyAttachmentsException.java` -- new, each `extends ApiException` with a 400 and its code -- no `GlobalExceptionHandler` change needed.
- [x] `backend/src/main/resources/application.yml` -- add `storage.local.base-dir` (env-indirected, `${STORAGE_DIR:./data/attachments}`), `storage.attachment.max-file-size-bytes`, `storage.attachment.max-count`; **and** set `spring.servlet.multipart.max-file-size` / `max-request-size` above our own cap -- Spring's 1MB default would otherwise reject a large upload before our validator runs and surface an opaque 500.
- [x] `docker-compose.yml` -- add a `volumes:` key to the `backend` service and a named volume beside `postgres_data` -- stored bytes must survive a container restart; the CI job runs `up --wait`, so the directory must auto-create.
- [x] `.env.example` -- document `STORAGE_DIR` with a commented explanation, matching the existing entries.
- [x] `frontend/src/i18n/bg.json`, `frontend/src/i18n/en.json` -- add the three `errors.codes` entries -- the contract check fails in both directions.
- [x] `backend/src/test/java/com/motorinsurance/shared/storage/ImageContentSnifferTest.java` -- new; unit-test every row of the I/O matrix's sniffing cases including the renamed PDF, the truncated header, the empty file, the `RIFF`-but-not-WebP case, and the lying `Content-Type`.
- [x] `backend/src/test/java/com/motorinsurance/shared/storage/AttachmentValidatorTest.java` -- new; size cap, count cap, one-bad-file-in-a-batch, and the fail-fast startup validation for a non-positive cap.
- [x] `backend/src/test/java/com/motorinsurance/shared/storage/LocalFilesystemStorageTest.java` -- new; round-trip store/read, key uniqueness across many calls, base-directory auto-creation, and the path-containment guard. Use JUnit's `@TempDir`.

**Acceptance Criteria:**
- Given a valid image of each allowed type, when stored and read back, then the returned bytes are byte-identical to the input and the SHA-256 matches.
- Given two stores of the *same* bytes, when both succeed, then the two storage keys differ — the key is an identifier, not a content hash.
- Given the full backend suite, when `mvn test` runs, then every existing test still passes unchanged — this story adds no behaviour to any existing endpoint.
- Given `node scripts/check-error-code-contract.mjs`, when it runs, then it passes — no new ALL-CAPS literal in the added Java is scraped as an unmatched error code.
- Given a clean checkout with no pre-existing storage directory, when `docker compose up --build -d --wait` runs, then the backend reaches a healthy state.

## Design Notes

**Why hand-rolled sniffing rather than a library.** Apache Tika is not on the classpath and is not in the Spring Boot 4.1.1 BOM, so it would need a pinned dependency with a compatibility justification. The JDK's `URLConnection.guessContentTypeFromStream` is already available but **does not recognise WebP**, which the allowlist requires. Three signatures is a dozen lines and the story's ACs demand unit tests for them anyway:

```
JPEG  FF D8 FF                    (3 bytes)
PNG   89 50 4E 47 0D 0A 1A 0A     (8 bytes)
WebP  "RIFF" at 0 AND "WEBP" at 8 (12 bytes; both required)
```

**Why the caps are checked twice.** Spring's own multipart limits fire inside the servlet layer before any application code sees the request, so leaving them at their 1MB default would make `MaxUploadSizeExceededException` — an opaque 500 — the real behaviour, not our `ATTACHMENT_TOO_LARGE`. Setting Spring's limits deliberately *above* our configured cap makes our cap the authority the spec claims it is. Story 10.2 owns the controller, so it inherits this; the config belongs here with the cap it protects.

**Why the port takes bytes, not `MultipartFile`.** Keeping the servlet type out of `shared.storage` means the sniffer and adapter are testable with plain byte arrays and no web context, and Milestone 5's PDF policy can reuse the same port for output it generates rather than receives.

**Naming constraint worth restating.** The contract check scrapes `"[A-Z]{2,}(_[A-Z]{2,})+"` from *any* `.java` file with no awareness of context. A constant named `"IMAGE_JPEG"` or a log tag `"ATTACHMENT_STORAGE"` would be read as an error code and fail CI with a confusing message. Keep enum constants single-segment and MIME strings lowercase.

## Verification

**Commands:**
- `cd backend && mvn -q clean test` -- expected: BUILD SUCCESS, all existing tests plus the three new test classes green
- `node scripts/check-error-code-contract.mjs` -- expected: exit 0, no unmatched codes in either direction
- `cd frontend && npm run typecheck` -- expected: clean (i18n catalogs are typed)
- `docker compose up --build -d --wait --wait-timeout 120` then `docker compose down -v` -- expected: backend healthy with no pre-created host directory
