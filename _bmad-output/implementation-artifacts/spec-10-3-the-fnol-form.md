---
title: 'Story 10.3 — The FNOL Form'
type: 'feature'
created: '2026-09-03'
status: 'review'
baseline_commit: 'ce922c0a62810c3c5c41cd3e204c62bfa0e29824'
review_loop_iteration: 1
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-10-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 10.2 built claim submission on the backend, but a client has no screen to file one, and this is the frontend's first file-upload UI — no multipart or file-picker pattern exists yet to reuse.

**Approach:** New `FnolForm` screen at `/policies/:policyId/claims/new`, reached via a "File a claim" link on `PolicyDetail`. Loads the policy (owner-scoped, `PolicyDetail`'s phase pattern) to show which policy the claim is against, then a form (incident date, description, location, native multi-file photo picker) submitting one `multipart/form-data` request via a small additive extension to `apiFetch`, using `useFormSubmission` for the submit lifecycle.

## Boundaries & Constraints

**Always:**
- **Verified contract (`ClaimController.java:57-68`, `SubmitClaimForm.java:29-33`):** `POST /api/v1/claims`, `consumes multipart/form-data`, `@PreAuthorize("hasRole('CLIENT')")`. Scalar `FormData` field names, exact: `policyId` (UUID string), `incidentDate` (ISO `yyyy-MM-dd`), `description` (10–2000 chars), `location` (2–200 chars). Photos are a sibling repeated field, exact name `attachments` (0 or more `File` entries) — not nested under a JSON-like key.
- Reuse `useFormSubmission('claim.form', t, { knownFields })` — no fifth hand-rolled cancelled-ref/phase copy.
- `apiFetch`: when `body instanceof FormData`, send it as-is — no `JSON.stringify`, and do **not** set `Content-Type` at all (neither `application/json` nor a manual `multipart/form-data`); the browser must compute and attach its own boundary parameter, which is only possible when `fetch` is left to set the header itself. Additive — existing JSON callers unaffected.
- Client-side file screening at selection time (type via `accept`, size ≤ 5 MiB, count ≤ 10, mirroring `storage.attachment.*`): a rejected file is excluded from submission and listed with its own name + reason. **UX convenience only — the backend remains the sole authority** and re-validates type (content-sniffed), size and count independently; a client-side pass is never treated as proof a file is valid.
- `incidentDate` input gets both bounds, client-side: `min={policy.coverageStart}`, `max={min(today, policy.coverageEnd)}` — `today` is the business-zone date, computed client-side same as `AcceptQuoteForm`. This keeps the picker inside the policy's own coverage window *and* disallows a future date in one native control. The backend independently re-checks both: `CLAIM_INCIDENT_DATE_IN_FUTURE` (future date, 400) and `CLAIM_INCIDENT_OUTSIDE_COVERAGE` (outside the window, 409) — the client bound is a UX nicety, not a substitute for either check.
- Register `'claim.form'` in `FieldErrorNamespace`; register `CLAIM_INCIDENT_OUTSIDE_COVERAGE`/`CLAIM_INCIDENT_DATE_IN_FUTURE`→`incidentDate` and `ATTACHMENT_UNSUPPORTED_TYPE`/`_TOO_LARGE`/`_TOO_MANY`→`attachments` in `FIELD_SPECIFIC_CODES` (translations already exist).
- Every new string ships in both `bg.json` and `en.json` in this change.
- Built only from existing components (`Card`, `FormField`, `Input`, `Alert`, `Button`, `Spinner`); usable from ~375px up.
- **Route safety (verified):** `/policies/:policyId/claims/new` sits inside the CLIENT-only `RoleGuard`, same as every other client route — no new guard logic. Ownership is enforced twice, independently: the screen's own `GET /api/v1/policies/{policyId}` returns `POLICY_NOT_FOUND` (404) for a policy that doesn't exist *or* belongs to someone else (identical, per the project's 404-never-403 rule), so the form never renders for it — and even if a stale/forged `policyId` reached submit anyway, `ClaimSubmissionService.submit` (`ClaimSubmissionService.java:74-75`) calls the same owner-scoped `PolicyService.getById(policyId, customerId)` and throws the same 404. Neither check depends on the other.

**Ask First:** none — the post-submit destination is decided below rather than deferred.

**Never:**
- No navigate to `/claims/:id` on success (doesn't exist until 10.4) — inline confirmation instead (claim number + link back to the policy).
- No drag-and-drop library, no upload-progress widget — bare `<input type="file" multiple accept="image/jpeg,image/png,image/webp">`.
- No `claimStatusPresentation.ts` (10.4) — a fresh claim is always `SUBMITTED`.
- No backend changes — 10.2 already shipped the contract this consumes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path | Valid fields, 2 photos | `FormData` POST; success screen shows claim number | N/A |
| No photos | Valid fields, no files | Submits with zero attachment entries | N/A |
| Oversized file | 6 MB JPEG | Excluded client-side, listed with "too large" reason | No request |
| Wrong type | `.pdf` chosen | Excluded client-side, listed with "unsupported" reason | No request |
| 11th file | Count > cap | Excess excluded, listed with "too many" reason | No request |
| Future incident date | `max` blocks it in-browser; still sent (e.g. edited devtools) | Field error under Incident date | `CLAIM_INCIDENT_DATE_IN_FUTURE` (400) |
| Outside coverage | `min`/`max` block it in-browser; still sent, or policy state changed since load | Form-level `Alert` (this exception carries no `fieldErrors`) | `CLAIM_INCIDENT_OUTSIDE_COVERAGE` (409) |
| Backend rejects a file client missed | e.g. content-sniff mismatch | Field error under photo list | `ATTACHMENT_*` (400) |
| Not the client's policy | Direct nav to another client's `:policyId` | Same not-found screen as `PolicyDetail` | `POLICY_NOT_FOUND` → 404 |
| Double submit | Two rapid clicks | One request only | N/A |

</frozen-after-approval>

## Spec Change Log

- Finding (implementation, verified against `ClaimIncidentOutsideCoverageException.java`): the frozen I/O matrix's "Outside coverage" row originally read "Field error under Incident date," but this exception is a 409 conflict-with-state and carries no `fieldErrors` at all — unlike `CLAIM_INCIDENT_DATE_IN_FUTURE` (400), which does. Amended: the row now reads "Form-level `Alert` (this exception carries no `fieldErrors`)," matching what `FnolForm.test.tsx`'s "renders a form-level error for an outside-coverage rejection" test actually exercises. Avoids: a spec that asserts behavior the current backend contract cannot produce. KEEP: `CLAIM_INCIDENT_OUTSIDE_COVERAGE` stays registered in `FIELD_SPECIFIC_CODES` (Boundaries) as forward-compatible dead weight — harmless now, and no rework needed if a future story adds a `fieldErrors` entry to that exception. No backend or test change made; this entry only reconciles the spec's own text with already-verified, already-tested behavior.

## Code Map

- `frontend/src/app/router.tsx:41-51` -- add `{ path: 'policies/:policyId/claims/new', element: <FnolForm /> }` inside the existing CLIENT `RoleGuard` children; no new guard.
- `frontend/src/features/policy/PolicyDetail.tsx:32-58,103-110` -- `Phase` loading/not-found/error/ready pattern to mirror for the new screen's own policy fetch; add a "File a claim" `Link` to `/policies/${id}/claims/new` beside the status badge.
- `frontend/src/features/quote/AcceptQuoteForm.tsx` (whole file) -- pattern to mirror: `useFormSubmission`, native date input with client min/max (here: `min=policy.coverageStart`, `max=min(today, policy.coverageEnd)`, both read off the already-loaded `PolicyResponse`), `Card`+`FormField`+`Input`+`Alert`+`Button`/`Spinner` composition (here: inline confirmation instead of navigate-on-success, since no target route exists yet).
- `backend/src/main/java/com/motorinsurance/claim/api/ClaimController.java:57-68`, `SubmitClaimForm.java:29-33` -- the verified request contract (endpoint, field names, bounds) quoted in Boundaries above.
- `backend/src/main/java/com/motorinsurance/claim/application/ClaimSubmissionService.java:74-75` -- the second, independent owner-scope check on submit (`policyService.getById(policyId, customerId)`), confirming the route needs no extra frontend guard beyond `RoleGuard role="CLIENT"`.
- `frontend/src/hooks/useFormSubmission.ts` -- `(namespace, t, { knownFields }) => { submitting, formError, fieldErrors, submit }`.
- `frontend/src/hooks/useCancelledRef.ts` -- reuse for the screen's own `GET /api/v1/policies/{policyId}` load, separate from `useFormSubmission`'s own guard.
- `frontend/src/api/client.ts:45-46,63-90` -- extend the body-handling branch so `body instanceof FormData` bypasses stringify/`Content-Type`.
- `frontend/src/i18n/errorMessages.ts:27,59-71` -- add `'claim.form'` to `FieldErrorNamespace`; add the five `FIELD_SPECIFIC_CODES` entries above.
- `frontend/src/i18n/{en,bg}.json:51-75` (`quote.form` shape to mirror), `:165-187` (five `errors.codes.*` already present, no change) -- add root `claims.form`: `heading`, `policyLabel`, `incidentDate`, `description`, `location`, `attachments`(+limits hint), `submit`, `submitting`, `success.{heading,claimNumber,backToPolicy}`, `fieldErrors.{incidentDate,description,location,attachments}`; reuse `errors.codes.ATTACHMENT_*` text for per-file rejection reasons rather than duplicating it.
- `frontend/src/features/policy/policyTypes.ts` -- shape reference for new `frontend/src/features/claim/claimTypes.ts` (`ClaimResponse`: `id, claimNumber, policyId, policyNumber, incidentDate, description, location, status, submittedAt, attachments[]`).
- `frontend/src/features/quote/QuoteForm.test.tsx` -- test-convention template (mock `apiFetch`, import `bg.json` for assertions, cover success/field-errors/generic-fallback/double-submit/retry/no-throw-after-unmount) to replicate in `FnolForm.test.tsx`, plus new cases for per-file client-rejection and the `FormData` shape.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/api/client.ts` -- `FormData` body passthrough -- unblocks every multipart caller.
- [x] `frontend/src/i18n/errorMessages.ts` -- `'claim.form'` namespace + five `FIELD_SPECIFIC_CODES` entries.
- [x] `frontend/src/i18n/en.json`, `bg.json` -- add `claims.form` section.
- [x] `frontend/src/features/claim/claimTypes.ts` -- new `ClaimResponse` type.
- [x] `frontend/src/features/claim/FnolForm.tsx` -- new; own-policy load (loading/not-found/error/ready), form in reading order which policy → what happened → when → where → photos → submit, client-side file screening + per-file rejection list, `useFormSubmission('claim.form', ...)`, `FormData` submit, inline success confirmation with claim number on 201.
- [x] `frontend/src/features/policy/PolicyDetail.tsx` -- add the "File a claim" link.
- [x] `frontend/src/app/router.tsx` -- add the new route.
- [x] `frontend/src/features/claim/FnolForm.test.tsx` -- new; covers the I/O matrix plus the standard `useFormSubmission` consumer suite.

**Acceptance Criteria:**
- Given the FNOL screen, when it renders, then reading order is which policy → what happened → when → where → photos → submit, one `primary` submit button labelled with its outcome, usable from ~375px up.
- Given photo selection, when files are chosen, then each is listed with name and size before submit, and a rejected file names itself and its reason inline.
- Given the four surface states, when the screen is used, then loading, error and content are all present, the error `Alert` is keyed off the backend `code`, and submitted field values survive a failed submission.
- Given both languages, when the screen renders, then every label, hint, validation message and error code resolves with no untranslated fallback in either `bg` or `en`.

## Design Notes

**Route shape.** `AcceptQuoteForm` embeds inside `QuoteDetail`, which already has the quote loaded; this screen instead gets its own route because the AC names "which policy" as the form's own first section, and a routed screen survives a refresh/bookmark where nav-state would not. `FnolForm` does its own `GET /api/v1/policies/{policyId}` fetch, which also gives owner-scoping 404 for free.

**No success route to navigate to.** `/claims/:id` doesn't exist until Story 10.4, so this screen renders its own inline success state instead of inventing a placeholder route.

## Verification

**Commands:**
- `cd frontend && npm test` -- all existing tests plus `FnolForm.test.tsx` and updated `errorMessages`/`client` tests green.
- `cd frontend && npm run typecheck` -- clean.
- `node scripts/check-error-code-contract.mjs` -- exit 0.

**Manual checks (if no CLI):**
- Log in as a client with a policy, open it, click "File a claim", submit with photos, verify via DB (`claims`/`attachments` tables) — no `/claims/:id` screen exists yet to see it in the UI.
