---
title: 'Story 10.4 — My Claims: List and Detail'
type: 'feature'
created: '2026-09-03'
status: 'review'
baseline_commit: '8811d0f48947b4fe807737c2dd1ce600af6de1c7'
review_loop_iteration: 1
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-10-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A filed claim (Story 10.2) is invisible to the client who filed it — no list, no detail, no way to see the photos they attached. This is also the backend's first byte-streaming endpoint and its first dual-role (CLIENT-or-LIQUIDATOR) permission check.

**Approach:** Three new `GET` endpoints in `ClaimController` (list, detail, attachment download) backed by a new `ClaimQueryService`, plus two new frontend screens (`MyClaims`, `ClaimDetail`) and a `claimStatusPresentation.ts` module, mirroring the `policy` module's list/detail shape throughout.

## Boundaries & Constraints

**Always:**
- List/detail ownership is baked into the repository query itself (`findByIdAndCustomerId`/`findAllByCustomerIdOrderBy...`), never fetched-then-checked in Java — mirrors `PolicyRepository` exactly. Not-owner and not-found both throw `ClaimNotFoundException` (404) — indistinguishable, same as `PolicyNotFoundException`.
- **Status history is synthetic this story, not a new table, and must not imply transitions that never happened.** `claim_status_history` doesn't exist until Epic 11 Story 1 (V12) — confirmed empty today (`Claim.java` has only `status`+`submittedAt`, and `ClaimView.java`'s own Javadoc already anticipates this). `ClaimView` gets a new `statusHistory: List<StatusHistoryEntry>` field, populated in the mapper as exactly one entry — `List.of(new StatusHistoryEntry(claim.status(), claim.submittedAt()))` — representing only the initial `SUBMITTED` event, the one fact this story actually has. It is not a placeholder for a richer timeline and must never be padded, inferred, or backfilled with anything else. Story 11.1 replaces/extends this with real persisted history once transitions exist; this story's job is only to not lie in the meantime.
- **The attachment-download endpoint must never return 403.** AC requires "the claim's own CLIENT owner or any LIQUIDATOR receives the file; anyone else receives 404, never 403." `@PreAuthorize("hasRole(...))")`-style role gating throws `AccessDeniedException` → 403 by default, which an AGENT/ADMINISTRATOR caller would trigger — forbidden by this AC. Use `@PreAuthorize("isAuthenticated()")` only; do the CLIENT-owner-or-LIQUIDATOR branch inside `ClaimQueryService`, throwing `ClaimAttachmentNotFoundException` (404, code `ATTACHMENT_NOT_FOUND`) uniformly for "wrong role," "not the owner" (a different client's claim), and "genuinely missing" — all three are indistinguishable from the caller's side. Unauthenticated stays 401 (from `isAuthenticated()` itself, unchanged Spring behavior).
- New `CurrentUser.hasRole(Authentication, String)` static helper (authority format confirmed: `ROLE_<role>`, `JwtAuthenticationFilter.java:57`) — the service uses it to decide the LIQUIDATOR branch; no other new authorization helper.
- Download response: `ResponseEntity<byte[]>` (no existing byte-streaming precedent to follow — this is the first; `Storage.read(storageKey)` at `Storage.java:51` already returns `byte[]`, so no new `Storage` method needed) with `Content-Type` set from `Attachment.contentType()` — the **content-sniffed** type `ImageContentSniffer` determined at upload time (Story 10.1), never re-derived or trusted from the filename — and `Content-Disposition: inline; filename="..."` from `Attachment.displayFilename()` — `inline`, not `attachment`, so the browser renders the image rather than downloading it (matches "linking to the full image in a new tab").
- **Frontend images require the `Authorization` header, so a plain `<img src>`/`<a href>` cannot work** (this app has no session cookie — the JWT lives in memory, never auto-attached to a browser-initiated request). `apiFetch` gains an additive `responseType?: 'json' | 'blob'` option (default `'json'`, unchanged behavior) — **the branch applies only after a successful (2xx) response**; a non-2xx response still goes through the existing JSON error-envelope parse (`code`/`fieldErrors`) exactly as today, and the existing 401 → `clearToken()` + `notifySessionExpired()` path (`client.ts:101-104`) fires identically regardless of `responseType`. `ClaimDetail` calls it per attachment to get a `Blob`, builds an object URL via `URL.createObjectURL`, uses that same URL for both the `<img src>` and the wrapping `<a href target="_blank">`.
- **Object URL lifecycle:** every `URL.createObjectURL()` call is paired with a `URL.revokeObjectURL()` — when that attachment's URL is replaced (e.g. a re-fetch) and unconditionally on unmount (`useEffect` cleanup). A leaked object URL holds its blob in memory for the tab's lifetime; this is a correctness requirement, not tidiness, and gets its own test.
- `claimStatusPresentation.ts` mirrors `policyStatusPresentation.ts`'s shape (`{ variant, label }`, a `switch` per status) but only needs `Pick<ClaimResponse, 'status'>` — no dates in a claim status label.
- Every new string ships in both `bg.json`/`en.json`, including the two new codes `CLAIM_NOT_FOUND` (404) and `ATTACHMENT_NOT_FOUND` (404) — neither exists in the catalogs yet.
- List/detail claim endpoints stay `hasRole('CLIENT')` only (AC: "when a CLIENT calls it") — the 403-avoidance rule above is specific to the dual-role download endpoint.

**Ask First:** none — every open question above (status-history shape, the no-403 download rule, the blob/object-URL image approach) is resolved with cited evidence, not guessed.

**Never:**
- No `claim_status_history` table, no `@Version`/optimistic locking — Story 11.1 (V12).
- No editing, cancelling, or re-submitting a claim from these screens — read-only.
- No lightbox/carousel/gallery dependency — plain thumbnail grid, new-tab link only.
- No query-string token/signed-URL scheme for image auth — headers only, per the app's existing auth model.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output | Error Handling |
|----------|--------------|---------------------------|----------------|
| List, own claims | CLIENT with 3 claims | 3 entries, newest first, same DTO as detail | N/A |
| List, none | CLIENT with 0 claims | Empty array; frontend shows empty state | N/A |
| Detail, own claim | Valid id | Full claim incl. 1-entry `statusHistory`, attachments | N/A |
| Detail, someone else's claim | Another client's id | 404, not 403 | `CLAIM_NOT_FOUND` |
| Detail, nonexistent id | Random UUID | 404 | `CLAIM_NOT_FOUND` |
| Download, owner CLIENT | Own claim's attachment | 200, image bytes, `Content-Disposition: inline` | N/A |
| Download, any LIQUIDATOR | Any claim's attachment | 200, image bytes | N/A |
| Download, different CLIENT | Not the owner | 404, not 403 | `ATTACHMENT_NOT_FOUND` |
| Download, AGENT/ADMINISTRATOR | Wrong role entirely | 404, not 403 | `ATTACHMENT_NOT_FOUND` |
| Download, no token | Unauthenticated | 401 | N/A |
| Download, guessed storage key | Direct filesystem/volume access | Not reachable — volume never statically served | N/A |
| Thumbnail rendering | Claim with 2 photos | 2 authenticated blob fetches, both render, both link to themselves in a new tab | N/A |
| Blob request fails | `responseType: 'blob'` call gets a non-2xx | Same JSON error-envelope parse as any other call — never treated as a blob | Existing `ApiRequestError` path, incl. 401 session-expiry |
| Object URL lifecycle | Detail screen re-fetches, or unmounts, with N object URLs already created | Every one of the N is `revokeObjectURL`'d before/on the transition | N/A |

</frozen-after-approval>

## Spec Change Log

- Finding (implementation): the Code Map omitted `frontend/src/app/RootLayout.tsx`, but `epic-10-context.md`'s own UX section states "'My claims' joins the client header nav" — without it, `/claims` would be reachable only by typed URL. Amended: added the nav `Link` there, mirroring the existing `/policies` link exactly, with its own test (`is reachable from the header nav for a client`). Avoids: a shipped list screen nothing links to. No spec renegotiation needed — this was an omission in the Code Map, not a contested decision.

## Code Map

- `backend/src/main/java/com/motorinsurance/policy/api/PolicyController.java:47-57`, `policy/application/PolicyService.java:148-166`, `policy/persistence/PolicyRepository.java:28,35` -- exact list+detail pattern to mirror: `hasRole('CLIENT')`, `listForCustomer`/`getById` naming, ownership-in-query.
- `backend/src/main/java/com/motorinsurance/claim/persistence/ClaimRepository.java` -- add `Optional<Claim> findByIdAndCustomerId(UUID id, UUID customerId)`, `List<Claim> findAllByCustomerIdOrderBySubmittedAtDesc(UUID customerId)`.
- `backend/src/main/java/com/motorinsurance/claim/persistence/AttachmentRepository.java` -- add `List<Attachment> findAllByClaimId(UUID claimId)`, `Optional<Attachment> findByIdAndClaimId(UUID id, UUID claimId)`.
- `backend/src/main/java/com/motorinsurance/claim/application/ClaimView.java:21-32` -- add `statusHistory: List<StatusHistoryEntry>` field; new nested/sibling record `StatusHistoryEntry(ClaimStatus status, Instant occurredAt)`.
- `backend/src/main/java/com/motorinsurance/claim/domain/Claim.java:58-63` -- read-only source of `status`/`submittedAt` for the synthetic history entry; no changes to this file.
- `backend/src/main/java/com/motorinsurance/policy/application/PolicyNotFoundException.java` -- template for new `claim/application/ClaimNotFoundException.java` (404, `CLAIM_NOT_FOUND`) and `ClaimAttachmentNotFoundException.java` (404, `ATTACHMENT_NOT_FOUND`).
- `backend/src/main/java/com/motorinsurance/claim/application/ClaimSubmissionService.java` -- sibling, not modified; new `claim/application/ClaimQueryService.java` owns `listForCustomer`, `getById`, and `downloadAttachment(claimId, attachmentId, customerId, isLiquidator)` returning a small `AttachmentContent(byte[] bytes, String contentType, String displayFilename)` record.
- `backend/src/main/java/com/motorinsurance/shared/storage/Storage.java:43-51` -- `read(storageKey)` already returns `byte[]`; no `Storage` change needed.
- `backend/src/main/java/com/motorinsurance/shared/api/CurrentUser.java` -- add `hasRole(Authentication, String)`; authority format `ROLE_<role>` confirmed at `auth/config/JwtAuthenticationFilter.java:57`.
- `backend/src/main/java/com/motorinsurance/claim/api/ClaimController.java:57-68` -- add the three `@GetMapping`s (list, `/{id}`, `/{claimId}/attachments/{attachmentId}` -- the last with `@PreAuthorize("isAuthenticated()")` only, per Boundaries).
- `backend/src/main/java/com/motorinsurance/policy/api/PolicyControllerTest.java` (list+detail sections, role_action_outcome naming) -- test-convention template for `ClaimControllerTest`'s new list/detail/download sections.
- `frontend/src/features/policy/MyPolicies.tsx` (whole file) -- list pattern to mirror: 3-phase (`loading`/`error`/`ready`, empty is a `ready` sub-branch), `Card`+`Badge` whole-row `Link`, empty-state copy+CTA branching.
- `frontend/src/features/policy/PolicyDetail.tsx` (already known from Story 10.3) -- 4-phase pattern (`loading`/`not-found`/`error`/`ready`) to mirror for `ClaimDetail`.
- `frontend/src/features/policy/policyStatusPresentation.ts` (whole file, + its `.test.ts`) -- exact shape to mirror for `claimStatusPresentation.ts`: `SUBMITTED` neutral, `UNDER_REVIEW` info, `APPROVED`/`PAID` success, `REJECTED` danger.
- `frontend/src/features/claim/claimTypes.ts:31-42` -- `ClaimResponse` (Story 10.3) gains `statusHistory: { status: ClaimStatus; occurredAt: string }[]`; no other changes, reused as-is by both new screens.
- `frontend/src/api/client.ts:63-90,92-125` (already extended once in Story 10.3 for `FormData`) -- add additive `responseType?: 'json' | 'blob'` to `ApiFetchOptions`, default `'json'`. Insert the branch only at the success path (after the existing `!response.ok` block at lines 92-125, which stays untouched byte-for-byte): `'blob'` resolves `response.blob()`, otherwise the current `response.json()`. The 401/`clearToken`/`notifySessionExpired` branch and the error-envelope `code`/`fieldErrors` parse both live inside that untouched `!response.ok` block, so they apply identically regardless of `responseType`.
- `frontend/src/app/router.tsx:42-55` -- add `{ path: 'claims', element: <MyClaims /> }` and `{ path: 'claims/:id', element: <ClaimDetail /> }` inside the existing CLIENT `RoleGuard` children (after Story 10.3's `policies/:policyId/claims/new`); no new guard.
- `frontend/src/i18n/{en,bg}.json` -- add root `claims.list` (mirror `policies.list` shape) and `claims.detail` (mirror `policies.detail` shape) sections, plus `errors.codes.CLAIM_NOT_FOUND`/`ATTACHMENT_NOT_FOUND`.

## Tasks & Acceptance

**Execution:**
- [x] `backend/.../claim/persistence/{ClaimRepository,AttachmentRepository}.java` -- add the four query methods above.
- [x] `backend/.../claim/application/{ClaimNotFoundException,ClaimAttachmentNotFoundException}.java` -- new, mirroring `PolicyNotFoundException`.
- [x] `backend/.../claim/application/ClaimView.java` -- add `statusHistory` + `StatusHistoryEntry` record.
- [x] `backend/.../claim/application/ClaimQueryService.java` -- new; `listForCustomer`, `getById`, `downloadAttachment` (CLIENT-owner-or-LIQUIDATOR branch, uniform 404).
- [x] `backend/.../shared/api/CurrentUser.java` -- add `hasRole(Authentication, String)`.
- [x] `backend/.../claim/api/ClaimController.java` -- add the three GET endpoints.
- [x] `backend/.../i18n` contract: `frontend/src/i18n/en.json`, `bg.json` -- add `CLAIM_NOT_FOUND`, `ATTACHMENT_NOT_FOUND`, `claims.list`, `claims.detail` sections.
- [x] `frontend/src/api/client.ts` -- additive `responseType: 'blob'` support.
- [x] `frontend/src/features/claim/claimTypes.ts` -- add `statusHistory` field.
- [x] `frontend/src/features/claim/claimStatusPresentation.ts` -- new, + test.
- [x] `frontend/src/features/claim/MyClaims.tsx` -- new; list screen, + test.
- [x] `frontend/src/features/claim/ClaimDetail.tsx` -- new; detail screen incl. blob-based thumbnail grid; every `createObjectURL` paired with `revokeObjectURL` on replacement and unmount, + a dedicated test asserting `revokeObjectURL` is called with each previously-created URL (both on re-fetch and on unmount), not just that images render.
- [x] `frontend/src/app/router.tsx` -- add the two new routes.
- [x] `backend/src/test/java/com/motorinsurance/claim/api/ClaimControllerTest.java` -- extend with list/detail/download sections covering the I/O matrix, incl. the AGENT/ADMINISTRATOR-gets-404-not-403 case.

**Acceptance Criteria:**
- Given the list screen, when it renders, then each row is a `Card`+`Badge` whole-row link, and an empty list names its cause with one action, never an error tone.
- Given the detail screen, when a claim isn't the caller's own, then it renders identically to a nonexistent claim (no 403-shaped state anywhere in the frontend).
- Given claim status anywhere it renders, then its label and `Badge` variant come from `claimStatusPresentation.ts` alone.
- Given dates on either screen, then they render through the existing UTC-pinned `formatDate`.
- Given both languages, then every new label, hint, and the two new error codes resolve with no untranslated fallback.

## Design Notes

**Why the download endpoint skips `@PreAuthorize` role-gating entirely.** Every other endpoint in this codebase uses `hasRole(...)` and accepts a 403 for the wrong role. This endpoint can't: its own AC explicitly forbids 403 for *any* caller, including an authenticated AGENT. `isAuthenticated()` plus an in-service branch is the only way to make "wrong role" and "not owner" collapse into the same 404 the AC requires.

**Why images go through `apiFetch` + object URLs, not a plain `<img src>`.** This app authenticates with a bearer token held in memory (no session cookie), and a browser-initiated `<img>`/top-level `<a>` request cannot attach a custom header. The endpoint requiring auth is a hard security requirement (AC: "knowing or guessing a storage key grants nothing"), so the frontend must fetch authenticated and materialize a local blob URL — there's no lighter-weight option that keeps both properties.

## Verification

**Commands:**
- `cd backend && mvn -q clean test` -- BUILD SUCCESS, full suite plus new `ClaimControllerTest` sections green.
- `cd frontend && npm test` -- all existing tests plus `MyClaims.test.tsx`, `ClaimDetail.test.tsx`, `claimStatusPresentation.test.ts` green.
- `cd frontend && npm run typecheck` -- clean.
- `node scripts/check-error-code-contract.mjs` -- exit 0 (23 codes).

**Manual checks (if no CLI):**
- File a claim with 2 photos, open "My claims", open its detail, confirm both thumbnails render and each opens full-size in a new tab.
