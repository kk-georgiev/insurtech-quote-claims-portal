# Epic 10 Context: File a Claim With Photos

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic turns a policy from a receipt into something a client can act against. A logged-in client opens one of their own policies, describes what happened, attaches photos, and receives back a claim with its own human-readable number and a `SUBMITTED` status — then finds it again under "My claims" with everything they submitted, its status history, and its images. It is the first time this system accepts a file from a user and serves it back, which makes it the largest new security surface in the project: the upload validation rules here are release-blocking requirements, not hardening. Four stories: a storage port with a validated local-filesystem adapter, the claim-submission backend (coverage check, claim number), the FNOL form, and the My Claims list and detail screens.

## Stories

- Story 10.1: Attachment Storage and Validated Upload
- Story 10.2: Claim Submission, Coverage Check and Claim Number
- Story 10.3: The FNOL Form
- Story 10.4: My Claims — List and Detail

## Requirements & Constraints

**Upload validation (release-blocking, each independently testable)**
- File type is determined by **sniffing actual content** — never the filename extension, never the client-supplied `Content-Type`. Only JPEG, PNG and WebP pass; a PDF renamed `.jpg` is rejected.
- A per-file size cap and a per-claim file-count cap, both resolved from one configured value rather than literals at call sites.
- Each stored file is written under a **randomly generated storage key**; the client-supplied filename is display metadata only. No client-supplied string reaches the filesystem path, so traversal sequences and executable extensions are unrepresentable by construction.
- Rejections are specific, translated, field-level errors — never a generic 500.
- Bytes live on a storage volume; Postgres holds only storage key, content type, size, hash and upload time. The volume is never statically served by any handler.

**Filing**
- A claim is filed against one of the client's **own** policies: incident date, description, location, photos. Initial status `SUBMITTED`, set by the backend and never accepted from the caller.
- Coverage is validated **on the incident date**, not on "is the policy active now". A claim against an expired policy for an incident inside its coverage window is accepted; one outside the window is a conflict, not a validation error. Boundaries inclusive at both ends.
- A future incident date is input validation: a 400 with a field-level error. Description has enforced min and max length, also field-level.
- Multiple claims against the same policy are allowed — no uniqueness constraint.

**Reading back**
- Every client-facing read is owner-scoped **in the query**, not fetched-then-checked in Java. Someone else's resource is 404, never 403.
- Attachment download is permitted to the claim's own CLIENT owner **or any LIQUIDATOR**; anyone else 404; unauthenticated 401.
- List endpoints are bare ordered JSON arrays, unpaginated, newest first, using the same DTO the detail endpoint returns.
- Detail returns everything submitted plus current status, full status history, and attachment metadata.

**Cross-cutting**
- New error codes are namespaced `CLAIM_*` / `ATTACHMENT_*` and ship with both `bg` and `en` translations in the same change; CI's error-code contract check is the gate. Every screen and message renders in both languages with no untranslated fallback.
- Business dates evaluated in `Europe/Sofia` through an injected `Clock`; no production code calls `LocalDate.now()` directly. `LocalDate`/`DATE` for business dates, `Instant`/`TIMESTAMPTZ` for events.
- Every new screen is built from the existing component library and usable from ~375px up. No new one-off styling, no new colour, radius or motion.
- Test coverage expectations: sniffing, allowlist, size cap, count cap and key generation each get a direct unit test including the renamed-PDF case; incident-outside-coverage, future-date and cross-client-isolation cases are on the acceptance checklist.
- The claims feature must never be documented or described as a full implementation of the legal motor third-party-liability claims process — it is the authenticated policyholder's own journey against their own policy.

## Technical Decisions

- **No separate attachment module.** `claim` owns the attachments table and every ownership check, because an attachment has no life outside its claim and its permission rule *is* the claim's. The byte-level concern is a `Storage` port in `shared.storage` with a local-filesystem adapter beside it (chosen over MinIO deliberately; the port keeps an S3-compatible backend a substitution rather than a rewrite). `claim` depends on the port, never on the adapter.
- **A claim and its photos arrive in one request.** Claim creation is `multipart/form-data`: FNOL fields and files together, one transaction. Every file is fully validated *before* anything is written; if any file fails, neither a claim row nor a stored file exists. Bytes already written are best-effort deleted and the failure logged, but correctness does not depend on that delete — serving a file requires an attachments row, so an orphaned byte is unreachable and inert.
- **Download is nested under its claim**, served by the claim API, because permission is a property of the claim rather than of the file. A storage key is not a capability.
- **Claim status is a stored column**, deliberately unlike quote and policy status which are derived from dates. A claim's status records a *human decision* and cannot be recomputed from anything persisted. Its only writer is a transition operation, and the legal-transition rule lives once in the claim domain.
- **A claim references its policy; it does not snapshot it.** A real foreign key to the policy, because a policy is already immutable (it snapshots rather than references), so nothing underneath a claim can drift. The only copied value is the policy number, and only so claims list without a join. Coverage dates are read from the policy.
- **Claim numbers come from a dedicated global PostgreSQL sequence** plus a UNIQUE constraint, formatted `CL-{year}-{8 digits, zero-padded}` with the year in the business zone. No per-year reset; gaps expected and acceptable. No code path reads the highest existing number and increments it. This mirrors the policy-number rule exactly, on purpose.
- **Error codes and their HTTP mapping:** `CLAIM_NOT_FOUND` (404), `CLAIM_INCIDENT_OUTSIDE_COVERAGE` (409), `ATTACHMENT_UNSUPPORTED_TYPE` (400), `ATTACHMENT_TOO_LARGE` (400), `ATTACHMENT_TOO_MANY` (400), `ATTACHMENT_NOT_FOUND` (404). Note the split: a future incident date is a 400 field-level error; an incident outside coverage is a 409 conflict with the policy's state.
- **Module boundaries:** the `claim` module is created by the story that first needs it and reaches `policy` only through `policy.application`. This is asserted by a test-only guard added later in the milestone, so keep the boundary clean from the start.
- **Migrations continue the numbered sequence**, each with its backfill in the same migration and a header naming its story: claims plus the claim-number sequence first, then attachments.
- **Not in scope:** no limit of liability is modelled anywhere; no claim filtering by status, date or client; no PDF generation; no fraud flags; no third-party FNOL filing.

## UX & Interaction Patterns

- **Routes:** `/claims` and `/claims/:id` under the CLIENT role guard. The FNOL form is reached from a policy detail screen ("File a claim"), so a claim always starts from the policy it belongs to. "My claims" joins the client header nav.
- **Claim status vocabulary is fixed at five states**, each with one label per language and one badge variant, defined once in a `claimStatusPresentation` module matching how quote and policy status presentation already work: `SUBMITTED` neutral, `UNDER_REVIEW` info, `APPROVED` success, `REJECTED` danger, `PAID` success. No screen invents a sixth.
- **The FNOL is a screen section, not a modal**, in reading order: which policy → what happened → when → where → photos → submit. One primary button labelled with its outcome. Native date input, no custom picker; a future date is refused with a field-level translated message.
- **Photo selection is a native multi-file input** with an image accept list — no drag-and-drop library, no upload widget. Chosen files are listed with name and size before submit, and a rejected file names itself and its reason so a client with four photos knows which one failed.
- **Photos render as a plain responsive thumbnail grid** linking to the full image in a new tab. No lightbox, no carousel, no gallery dependency.
- **List rows are card + badge with the whole row as one link target** — not a card with a "View" button inside it. Single column at every width; no table, no horizontal scroller.
- **Four states on every new surface:** loading, empty (named cause plus the one action that fills it, never an error tone), error (alert keyed off the backend error code), content. Submitted values are preserved on submission failure.
- **Reuse the existing shared form-submission hook** rather than another verbatim copy of the cancelled-ref/phase/double-submit guard.
- Dates render through the existing UTC-pinned date formatter in the active language's convention; money is identical in both languages with an explicit currency from the API, never locale-derived.
- Accessibility floor only: semantic elements, real labels, `role="alert"`, unsuppressed focus rings, status never signalled by colour alone, 44px tap targets at small widths. Inherited from the primitives.

## Cross-Story Dependencies

- **Milestone prerequisite before Story 10.1:** a shared `currentUserId(Authentication)` helper must be extracted first — it is currently duplicated byte-for-byte between two controllers, and this epic adds the third copy. Extract before writing it, not after. A tracker-reconciliation data fix also runs ahead of the epic.
- **Story 10.1 → 10.2:** the storage port and its validated adapter must exist before claim submission can accept photos in one transaction.
- **Story 10.2 → 10.3, 10.4:** the frontend stories consume the submission, list, detail and download endpoints.
- **Story 10.3** depends on the existing policy detail screen as its entry point.
- **Story 10.4** establishes the claim status presentation module and the claim detail screen that Epic 11's liquidator screens and Epic 12's notifications both build on; the status history it renders is populated by Epic 11's transitions (a freshly filed claim shows only its `SUBMITTED` entry).
- Epic 13 is should-have and must never block anything in this epic.
