---
title: 'Story 3.2b: Error and Validation Message Translation'
type: 'feature'
created: '2026-08-29'
status: 'in-review'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Every failure path still speaks English, and worse, some of it is backend prose rendered straight into the DOM. `toFieldErrorMap` in both forms does `map[error.field] = error.message`, so raw Bean Validation text ("must be a well-formed email address") and hardcoded backend strings ("Email already registered", "Unknown region code: XY") reach the user verbatim. `zoneName` is `"Zone 1"`, seeded in English in the database. All of this contradicts AD-7/AD-8, which say the backend emits stable codes and structural data only, and that `message` is developer-facing and never displayed.

**Approach:** Make the frontend the sole author of every user-facing failure message. Map each backend `code` to a catalog entry; render field-level errors from the **field name** rather than the backend's prose; and label the tariff zone from the numeric `zoneId` the response already carries. The backend is not touched — every input needed is already on the wire.

## Boundaries & Constraints

**Always:**
- **Field-error decision (human-approved 2026-08-29).** The frontend ignores `ApiFieldError.message` entirely and selects copy by `field` name (`auth.fieldErrors.email`, `quote.fieldErrors.driverAge`, …). Adding a per-rule `code` to `ApiError.FieldError` was considered and rejected for this milestone: it is a backend change, which PRD §4.5 forbids. Note the resulting limitation honestly in the message wording — a per-field message must cover that field's whole constraint set, since which rule failed is no longer knowable.
- Every one of the ten codes the backend can emit gets an entry, added in the same change (AD-7): `AUTH_UNAUTHENTICATED`, `AUTH_FORBIDDEN`, `AUTH_INVALID_CREDENTIALS`, `AUTH_EMAIL_TAKEN`, `PRICING_UNKNOWN_REGION`, `PRICING_UNSUPPORTED_INSTALLMENTS`, `QUOTE_NOT_FOUND`, `SHARED_VALIDATION_ERROR`, `SHARED_NOT_FOUND`, `SHARED_INTERNAL_ERROR`.
- One shared code→message resolver, not a `switch` re-implemented per form. An unrecognized or absent code resolves to the existing generic fallback — a new backend code must degrade gracefully, never render blank or leak the raw `message`.
- `ApiRequestError.message` and `ApiFieldError.message` are never rendered, anywhere. They stay available for logging.
- Preserve the existing failure *behaviour* exactly: forms stay editable after an error, `role="alert"` and the `aria-invalid`/`aria-describedby` wiring are unchanged, and the resubmit guard still holds. This story changes what the text says, not how failures work.
- The zone label comes from `zoneId` (already typed in `QuoteResponse`), not from `zoneName`.

**Ask First:**
- Any backend `code` found in the wild that is not in the list above — the list must be complete before this ships.
- Any case where a per-field message genuinely cannot express the constraint set without becoming useless. That is evidence the rejected backend-code option was the right one, and is worth reopening rather than shipping vague copy.

**Never:**
- No backend change: no new codes, no `FieldError.code`, no migration to translate `tariff_zone.zone_name`, no `Accept-Language`.
- No change to which HTTP status or code any path returns, and no change to validation rules themselves.
- No money or date reformatting (Story 1.6/1.7 constraint).
- No static screen copy — that is Story 3.2a's, and this story should not touch a heading or label.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Known code | login returns `AUTH_INVALID_CREDENTIALS` | translated message; form stays editable | N/A |
| Field errors | register returns `fieldErrors: [{email, "must be…"}]` | translated per-field message beside the input; backend prose never shown | N/A |
| Unknown code | backend returns a code with no entry | generic translated fallback | logged, not displayed |
| No code at all | network failure, non-JSON body | generic translated fallback | as today |
| Unmapped field | `fieldErrors` names a field this form does not render | existing form-level fallback, now translated | as today |
| Zone label | quote succeeds with `zoneId: 3` | "Зона 3" / "Zone 3" from the catalog | N/A |
| Live switch | an error is on screen, toggle language | the visible error re-renders in the new language | N/A |

</frozen-after-approval>

## Code Map

- `frontend/src/i18n/bg.json`, `en.json` -- add `errors.codes.<CODE>` (ten entries), `errors.generic`, `auth.fieldErrors.*`, `quote.fieldErrors.*`, and `quote.result.zones.<1-5>`.
- `frontend/src/i18n/errorMessages.ts` -- **new**. The shared resolver: takes an `ApiRequestError` (or unknown throwable) plus `t`, returns the form-level message; and a field-error mapper turning `ApiFieldError[]` into a `Record<field, translatedMessage>`. Keep it beside the catalogs, not in `features/`, since both feature areas consume it.
- `frontend/src/features/auth/LoginForm.tsx:21-22,~106-116` -- delete `INVALID_CREDENTIALS_MESSAGE`/`GENERIC_ERROR_MESSAGE`; route the `error.code === 'AUTH_INVALID_CREDENTIALS'` branch and the fallback through the resolver.
- `frontend/src/features/auth/RegisterForm.tsx:18-19,21-26,~85` -- same for `EMAIL_TAKEN_MESSAGE`; replace `toFieldErrorMap`'s `map[error.field] = error.message`.
- `frontend/src/features/quote/QuoteForm.tsx:45,53-58,~125` -- same; this form has four mapped fields plus the two `PRICING_*` codes that arrive with both a top-level code and a `fieldErrors` entry. Decide once, in the resolver, which wins — and make it the same choice in both places.
- `frontend/src/features/quote/QuoteResult.tsx:19-20` -- `{quote.zoneName}` → `t('quote.result.zones.' + quote.zoneId)`. `zoneId` is already on `QuoteResponse` in both languages of the codebase; `zoneName` becomes unused by the UI but stays on the type and in the persisted quote.
- **Read-only evidence:** `backend/.../GlobalExceptionHandler.java:37-39,47-52,100-106,123-128` and `SecurityConfig.java:68-69` are where the ten codes and the field prose originate — read them to confirm the code list is complete, then change nothing. `backend/src/main/resources/db/migration/V3__create_pricing_tables.sql:49-53` holds the five English zone names this story routes around.
- **Test files:** `LoginForm.test.tsx`, `RegisterForm.test.tsx`, `QuoteForm.test.tsx` — each already has field-error and generic-error cases asserting the current English strings; re-point them at the catalog, keeping every case.
- **Closes deferred-work entries:** the `spec-1-2` "raw Bean Validation text rendered to the user" item, and the AD-7/i18n half of the `spec-1-6` `MethodArgumentTypeMismatchException` item.

## Tasks & Acceptance

**Execution:**
- [x] `frontend/src/i18n/bg.json`, `en.json` -- add the code, field-error, generic, and zone keys -- everything below depends on them.
- [x] `frontend/src/i18n/errorMessages.ts` -- **new**; the one resolver both feature areas use -- prevents three divergent `switch` statements.
- [x] `frontend/src/i18n/errorMessages.test.ts` -- **new**; cover all ten codes, unknown code, absent code, and non-`ApiRequestError` throwable -- the I/O matrix's core rows.
- [x] `frontend/src/features/auth/LoginForm.tsx`, `RegisterForm.tsx` -- delete the message constants, route through the resolver, translate field errors -- removes the first backend prose from the DOM.
- [x] `frontend/src/features/quote/QuoteForm.tsx` -- same, plus settle top-level-code vs `fieldErrors` precedence for the two `PRICING_*` cases.
- [x] `frontend/src/features/quote/QuoteResult.tsx` -- zone label from `zoneId` -- removes the last English string from a successful quote.
- [x] `frontend/src/features/**/*.test.tsx` -- re-point existing error assertions at the catalog; add a case proving a visible error re-renders on language switch.
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- mark the two entries above RESOLVED with the commit -- they were explicitly waiting on this story.
- [x] `README.md`, `frontend/README.md` -- record that translation coverage is complete and that a new backend code requires a catalog entry in the same change.

**Acceptance Criteria:**
- Given any backend error response, when it is displayed, then the message comes from the catalog keyed by `code` — and the raw backend `message` is never rendered.
- Given a validation failure on any form field, when it is shown beside the input, then it is a translated per-field message, never Bean Validation's English text.
- Given a backend code with no catalog entry, when it arrives, then the generic translated fallback is shown and the app does not break.
- Given a successful quote in Bulgarian, when the breakdown renders, then the zone reads "Зона N" and no English remains anywhere on the screen.
- Given an error is on screen, when I switch language, then that error re-renders in the new language without resubmitting.
- Given `npm run typecheck`, `npm run build`, and `npm test` in `frontend/`, when run, then all pass with no behavioural case dropped.

## Spec Change Log

## Design Notes

Per-field messages must cover the field's whole constraint set, because the rejected backend-code option is what would have made individual rules distinguishable. Write them to be true for every rule on that field, e.g. `quote.fieldErrors.driverAge` → "Възрастта трябва да е цяло число между 18 и 99." rather than a message that names only one bound. Where a rule has its own top-level code already (`PRICING_UNKNOWN_REGION`), prefer that more specific message over the generic per-field one.

## Verification

**Commands:**
- `cd frontend; npm run typecheck; npm run build; npm test` -- expected: all clean.
- `cd frontend; grep -rn "error.message\|\.message}" src/features --include=*.tsx | grep -v test` -- expected: no hits; backend prose no longer reaches a component.

**Manual checks (backend + Postgres running):**
- Wrong password; register a taken email; submit a quote with age 5, an unknown region code, and 3 installments; stop the backend mid-session and submit. Each in both languages — every message translated, none showing English or a raw backend sentence.
- Trigger any error, then toggle language with it on screen: it re-renders translated.
