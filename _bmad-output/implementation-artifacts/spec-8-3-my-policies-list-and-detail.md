---
title: 'Story 8.3: My Policies — List and Detail'
type: 'feature'
created: '2026-09-01'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '62bf971a55b6e88aa215583b7fac9ed6e24ac1ff'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A client can now issue a policy and never see it again. `policies` rows exist and the accept endpoint returns one, but there is no way to list them, no way to open one, and no route to link to — which is also why Story 6.3's accepted-quote state is still a dead-end notice and Story 8.2 confirms in place instead of navigating.

**Approach:** Give `policy` its api layer — `GET /api/v1/policies` and `GET /api/v1/policies/{id}`, owner-scoped, with status derived from the coverage dates in `policy`'s domain — and build the two screens that read them. Then close the two loose ends this unblocks: an accepted quote links to its policy, and a successful acceptance navigates to it.

## Boundaries & Constraints

**Always:** Both endpoints are `@PreAuthorize("hasRole('CLIENT')")`, take the customer id from the `SecurityContext`, and are owner-scoped **in the query** — someone else's policy is **404, never 403** (AD-10). The list returns a bare, newest-first JSON array of the same DTO the detail endpoint returns: no envelope, no pagination (AD-12). Status is **derived, never stored** — `today < coverage_start` → `SCHEDULED`, `today > coverage_end` → `EXPIRED`, else `ACTIVE` — implemented **once** in `policy`'s domain layer against the injected business-zone clock, with `CANCELLED` reserved and unreachable (AD-3, AD-6). Both routes sit under the existing CLIENT `RoleGuard`; no new guard logic. Rows are `Card` + `Badge`, the **whole row one link target**, single column at every width from 375px (UX-DR4, NFR-5). The policy breakdown is presented **identically** to the quote's, by reusing the existing presentation rather than writing a second one (FR-M3-10, FR-M3-07). New strings live under `policies.*` in both languages, dates follow the active language and money renders as the API sent it (NFR-4, UX-DR11); the new error code ships with both translations (AD-11).

**Ask First:** Any change to the accept endpoint's response shape or to `policies` persistence. Any need to paginate.

**Never:** No `status` column on `policies` and no scheduler (AD-3). An **expired policy renders in the neutral/muted treatment, never `danger`** — a policy that ran its full term is the successful outcome, unlike an expired quote (UX-DR2). No new UI primitive. No policy cancellation, no PDF, no claim entry point — Milestone 4 owns those. No second breakdown component. No change to the quote's own fields beyond the one additive `policyId` (AD-13).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| List, populated | CLIENT with policies | Bare array, newest-first; each row shows number, vehicle, status, whole row one link | N/A |
| List, owner scoping | Another client's policies exist | They never appear under any parameter | Enforced in the query (AD-10) |
| Detail, own policy | Valid id | Number, coverage period, premium, vehicle, and the full issued breakdown | N/A |
| Detail, someone else's | Valid id, wrong owner | **404** `POLICY_NOT_FOUND` | Never 403 |
| Detail, unknown id | Random UUID | **404** `POLICY_NOT_FOUND` | N/A |
| Status derivation | `today` before / within / after coverage | `SCHEDULED` / `ACTIVE` / `EXPIRED` | Boundaries inclusive: first and last day are `ACTIVE` |
| Expired policy row | `today > coverage_end` | **Neutral/muted** badge, never `danger` | The successful outcome, not a failure (UX-DR2) |
| Empty list, client has quotes | No policies, ≥1 quote | Empty state pointing at My Quotes | N/A |
| Empty list, no quotes either | No policies, no quotes | Empty state pointing at the quote form instead | Nobody is sent to a second empty screen (UX-DR6) |
| Loading / error, both screens | in flight, or request fails | Spinner, then `Alert` `danger` keyed off the backend `code` with a retry | Never a blank screen (UX-DR6) |
| Accepted quote's detail | `status === 'ACCEPTED'` | The notice is replaced by a link to its policy | Completes Story 6.3's stub (UX-DR7) |
| Acceptance succeeds | 201/200 from accept | Client is taken to that policy's detail screen | Replaces Story 8.2's interim in-place confirmation |
| No / wrong-role token | Anonymous, or AGENT | **401** / **403** | Existing gates, unchanged |

</frozen-after-approval>

## Code Map

**Backend — `policy` gets its api layer**

- `backend/src/main/java/com/motorinsurance/policy/domain/PolicyStatus.java` -- NEW enum, modelled on `quote/domain/QuoteStatus.java`: `SCHEDULED`, `ACTIVE`, `EXPIRED`, plus `CANCELLED` reserved with no producer and no branch in the derivation.
- `backend/src/main/java/com/motorinsurance/policy/domain/Policy.java:130` -- ADD `status(LocalDate today)`, the mirror of `Quote#status:237`: a pure function of its argument, so the caller resolves "today" from the injected clock and the rule stays testable without Spring. Inclusive at both ends — the coverage start and end dates are themselves `ACTIVE`.
- `backend/src/main/java/com/motorinsurance/policy/application/PolicyView.java:36` -- ADD `status`, filled by `PolicyService.toView` from `LocalDate.now(clock)`. The clock is already injected there for the policy number's year.
- `backend/src/main/java/com/motorinsurance/policy/application/PolicyService.java:136` -- ADD `listForCustomer` and `getById`, both owner-scoped, mirroring `QuoteService.listForCustomer:112`/`getById:100`.
- `backend/src/main/java/com/motorinsurance/policy/persistence/PolicyRepository.java:19` -- ADD `findByIdAndCustomerId` and `findAllByCustomerIdOrderByIssuedAtDesc`. Newest-first by `issued_at`, the policy's own creation fact.
- `backend/src/main/java/com/motorinsurance/policy/application/PolicyNotFoundException.java` -- NEW `ApiException`: 404, `POLICY_NOT_FOUND`, modelled on `QuoteNotFoundException.java` including its "404, never 403" javadoc.
- `backend/src/main/java/com/motorinsurance/policy/api/PolicyController.java` -- NEW. `@RequestMapping("/api/v1/policies")`, the two GETs, `currentUserId(authentication)` cast exactly as `QuoteController:120` does. `policy` owns its own URL space (AD-2).

**Backend — the quote→policy link**

- `backend/src/main/java/com/motorinsurance/policy/application/PolicyService.java` -- ADD a batch lookup returning quote id → policy id for one customer, so the quote list resolves every link in one query rather than N (see Design Notes).
- `backend/src/main/java/com/motorinsurance/quote/api/QuoteResponse.java:47` -- ADD `policyId` (nullable), the last field AD-13 anticipates. Every existing field keeps its name, type and meaning.
- `backend/src/main/java/com/motorinsurance/quote/application/QuoteService.java:117` -- populate it via `policy.application`, the permitted direction (AD-1). `toResponse` gains the id as a parameter rather than reaching for it per row.

**Frontend**

- `frontend/src/features/policy/policyTypes.ts:19` -- ADD `status` to `PolicyResponse`, and a `PolicyStatus` union mirroring the backend enum (`CANCELLED` included, as `QuoteStatus` already does).
- `frontend/src/features/policy/policyStatusPresentation.ts` -- NEW, modelled on `quote/quoteStatusPresentation.ts`. **`EXPIRED` maps to `neutral`, not `danger`** — the one place UX-DR2's rule is expressed.
- `frontend/src/features/policy/MyPolicies.tsx` -- NEW. Structure copied from `MyQuotes.tsx:29`, including its four states. The empty state fetches the quote list once to choose between the two CTAs.
- `frontend/src/features/policy/PolicyDetail.tsx` -- NEW. Modelled on `QuoteDetail.tsx`, using `useCancelledRef`. Number, coverage period and premium render heavier and larger than their labels (UX-DR12); the breakdown reuses the quote's presentation.
- `frontend/src/features/quote/QuoteResult.tsx:15` -- GENERALIZE to accept either a quote or a policy, so both screens render one identical breakdown (FR-M3-10). Its `data-testid`s and copy stay as they are; `QuoteDetail`/`QuoteForm` keep passing a quote.
- `frontend/src/features/quote/QuoteDetail.tsx:128` -- the `ACCEPTED` notice gains the link to `policyId`.
- `frontend/src/features/quote/AcceptQuoteForm.tsx:121` -- REPLACE the interim in-place success with `navigate('/policies/' + issued.id)`, as Story 8.2 recorded it would. Its success-block tests are replaced by a navigation assertion.
- `frontend/src/app/router.tsx:41` -- ADD `policies` and `policies/:id` beside the quote routes, inside the same CLIENT `RoleGuard`.
- `frontend/src/app/RootLayout.tsx:71` -- ADD "My policies" beside "My quotes"; the nav already wraps rather than collapsing.
- `frontend/src/i18n/{bg,en}.json` -- ADD the `policies.*` namespace and the `POLICY_NOT_FOUND` code. READ-ONLY: `LanguageToggle.test.tsx:370` fails any Bulgarian value identical to its English one — write real translations, do not extend the allowlist.
- READ-ONLY evidence: `Badge` already has the `neutral` variant this story needs. `useCancelledRef`/`useFormSubmission` (Story 8.2) are the load/submit mechanics — no new copies. `formatDate` still lacks an explicit `timeZone` (deferred item 46), so a policy date can render a day off for negative-UTC-offset viewers; out of scope here, still open.

## Tasks & Acceptance

**Execution:**
- [x] `PolicyStatus.java` + `Policy#status` + `PolicyStatusTest` -- derived status, boundaries inclusive -- FR-M3-09, AD-3.
- [x] `PolicyRepository` + `PolicyService` reads + `PolicyNotFoundException` -- owner-scoped, 404 -- AD-10, AD-11.
- [x] `PolicyController.java` -- the two endpoints -- FR-M3-10, AD-2, AD-12.
- [x] `PolicyControllerTest.java` -- its own file, every matrix row for the endpoints -- NFR-6.
- [x] `QuoteResponse.policyId` + `QuoteService` + the batch lookup -- the quote→policy link -- AD-13, AD-1.
- [x] `policyTypes.ts`, `policyStatusPresentation.ts` (+ test) -- the frontend vocabulary, `EXPIRED` → neutral -- UX-DR2.
- [x] `MyPolicies.tsx` + `PolicyDetail.tsx` (+ tests) -- the two screens and their four states -- FR-M3-10, UX-DR4/6/12.
- [x] `QuoteResult.tsx` -- render either shape, one identical breakdown -- FR-M3-07.
- [x] `QuoteDetail.tsx` + `AcceptQuoteForm.tsx` -- the link, and the navigation that replaces the interim success -- UX-DR7.
- [x] `router.tsx` + `RootLayout.tsx` -- routes under the existing guard, nav entry -- UX-DR3.
- [x] `i18n/{bg,en}.json` + `errorMessages.test.ts` -- `policies.*` and `POLICY_NOT_FOUND` -- NFR-3, NFR-4.

**Acceptance Criteria:**
- Given a client holding policies and another client's policies in the same database, when the list is called, then only the caller's appear and the query itself is what excludes the rest.
- Given a policy whose coverage has ended, when its row renders, then its badge is the neutral treatment and no `danger` styling appears anywhere on the screen.
- Given the same policy open in detail and the quote it came from open in detail, when both breakdowns render, then they are the same component showing the same figures.
- Given a client with no policies and no quotes, when My Policies renders, then the empty state points at the quote form, not at an empty My Quotes.
- Given the full suite, when it runs, then backend and frontend are green with no test weakened or skipped.

## Design Notes

**Why the quote carries `policyId` rather than the screen searching for it.** Story 6.3 stubbed the accepted state and 8.3's AC needs a link. The alternative — fetching every policy and matching on `quoteId` client-side — makes a list screen load an unrelated collection to render one link. AD-13 already names this field as part of the milestone's additive growth, so it lands here, filled through `policy.application` (the permitted direction, AD-1). The list path resolves all ids in **one batch lookup** keyed by quote id, never one query per row.

**`policy` still never reads `quotes`.** The batch lookup takes quote ids as plain values and returns policy ids; nothing joins the two tables and no `Policy`→`Quote` association appears (AD-4).

**The breakdown is generalized, not duplicated.** `QuoteResult` already renders every component the policy also stores under the same names, so it takes the wider shape rather than gaining a second implementation — which is what makes "presented identically" checkable rather than aspirational.

## Verification

**Commands:**
- `cd backend && mvn clean test` -- expected: green, including the status-boundary and owner-scoping cases. Docker running, `JAVA_HOME` on JDK 21.
- `cd frontend && npx vitest run --maxWorkers=2` -- expected: green. Cap the workers; full parallelism produces phantom timeouts on this machine.
- `cd frontend && npm run typecheck` -- expected: clean.
- `node scripts/check-error-code-contract.mjs` -- expected: 16 backend codes in both catalogs.

**Manual checks (if no CLI):**
- At 375px, both screens are single column, rows are one tap target each, and an expired policy reads as muted rather than red.

## Suggested Review Order

**The derived status — the rule everything else reads**

- The whole rule, pure and boundary-inclusive; the caller supplies "today".
  [`Policy.java:281`](../../backend/src/main/java/com/motorinsurance/policy/domain/Policy.java#L281)

- Where it is applied: the clock is read once per mapping, never stored.
  [`PolicyService.java:162`](../../backend/src/main/java/com/motorinsurance/policy/application/PolicyService.java#L162)

**The read endpoints**

- `policy` finally owns its own URL space; both reads CLIENT-only, 404 never 403.
  [`PolicyController.java:32`](../../backend/src/main/java/com/motorinsurance/policy/api/PolicyController.java#L32)

**The quote→policy link**

- One query for a whole list, taking quote ids as plain values — no join, no `Quote` import.
  [`PolicyService.java:179`](../../backend/src/main/java/com/motorinsurance/policy/application/PolicyService.java#L179)

- Completes Story 6.3's stub: the accepted state gains a way through.
  [`QuoteDetail.tsx:147`](../../frontend/src/features/quote/QuoteDetail.tsx#L147)

**The screens**

- The one place UX-DR2 lives: an expired policy is neutral, never danger.
  [`policyStatusPresentation.ts:28`](../../frontend/src/features/policy/policyStatusPresentation.ts#L28)

- The list, and the empty state that refuses to send anyone to a second empty screen.
  [`MyPolicies.tsx:31`](../../frontend/src/features/policy/MyPolicies.tsx#L31)

- The detail: three headline facts, then the shared breakdown.
  [`PolicyDetail.tsx:32`](../../frontend/src/features/policy/PolicyDetail.tsx#L32)

- Widened so one component serves both screens — what makes "identical" checkable.
  [`QuoteResult.tsx:13`](../../frontend/src/features/quote/QuoteResult.tsx#L13)
