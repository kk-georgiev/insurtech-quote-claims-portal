# Epic 3 Context: The Portal Speaks Bulgarian and English

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Every screen and user-facing message already delivered by Epics 1 and 2 — login, registration, the quote form and its premium breakdown, all four role shells, navigation labels, and every validation/error message — must be fully usable in Bulgarian or English, switchable by the user at any moment. Bulgarian is the default because it is the product's real market language; English exists so a non-Bulgarian-speaking reviewer (the mentor) can evaluate the demo. This is a frontend-only capability: the backend is not touched, and the work here proves that the language-independent error-code contract established in earlier epics actually holds up when something finally consumes it.

## Stories

- Story 3.1: i18n Infrastructure and Language Toggle
- Story 3.2: Full Translation Coverage of Milestone 1 Screens

## Requirements & Constraints

- Any user — authenticated or not — can switch the display language between Bulgarian and English at any time, from anywhere in the app.
- A first-time visitor with no stored preference sees Bulgarian.
- Switching languages re-renders all currently visible text immediately, without navigating away or losing the user's place in the flow.
- The selection survives page reloads, persisted client-side only. There is no server-side or per-account language preference in this milestone, and no API call changes because of the selected language.
- Coverage must be complete: in the Bulgarian pass, no untranslated key and no English-fallback artifact may be visible anywhere. This is a tracked success measure for the milestone, not a best-effort goal.
- Backend responses, including errors, carry stable language-independent codes and structural data only. Any human-readable `message` a response carries is developer- and log-facing; it must never be rendered to an end user.
- Adding a new screen or message later must never require a backend change purely to support translation — translation stays frontend-owned end to end.
- Scope is exactly two languages. Anything beyond Bulgarian and English, and any server-side localization (Accept-Language handling, server message bundles), is explicitly out of scope.

## Technical Decisions

- **Translation library and layout:** `react-i18next`, set up under the frontend's dedicated `i18n` directory with one catalog per language (`bg.json`, `en.json`).
- **Key namespacing:** i18n keys are namespaced per frontend feature — `auth.*`, `quote.*`, `shells.*` — mirroring the existing feature folder structure.
- **Error-code mapping (the central pattern of this epic):** every backend error arrives in a uniform envelope of `{timestamp, status, code, message, fieldErrors}`. The `code` value is the *only* thing the frontend uses to select the message it shows. Codes are namespaced `MODULE_REASON` (e.g. `AUTH_INVALID_CREDENTIALS`, `QUOTE_VALIDATION_ERROR`), and each maps to exactly one entry under that module's i18n namespace. The envelope's `message` is never displayed.
- **Code/translation pairing contract:** a backend code and its translation entry ship in the same change — never one without the other. Any code the backend can emit today needs an entry now, and this rule constrains all future work, not just this epic.
- **Persistence mechanism:** client-side storage (e.g. local storage) in the browser. No backend read or write is involved in language selection.
- **Backend is out of bounds:** no work in this epic should require modifying backend code. If a screen cannot be translated without a backend change, that signals a missing or unstable error code, not a need for backend localization.
- **Money and structural data:** premium values come from the backend as exact decimal data; language selection changes only surrounding copy and labels, never the numeric values themselves.

## UX & Interaction Patterns

No separate UX design document exists for this milestone — visual design is deliberately out of scope, and the placeholder role shells remain static and non-interactive. The only interaction requirement is the toggle itself: it must be reachable from every screen, in both authenticated and unauthenticated states, and take effect in place rather than through a reload or re-navigation.

## Cross-Story Dependencies

- Both stories depend on all screens from Epics 1 and 2 already existing: auth screens, the client quote form and breakdown, and the four role shells. Epic 3 retrofits them rather than building new surfaces.
- Story 3.2 depends on Story 3.1's infrastructure (library setup, catalogs, toggle, persistence) being in place first.
- Story 3.2 is the first real consumer of the backend error-code contract. The typed fetch client already surfaces the envelope's `code` and `fieldErrors` to callers — the earlier deferred-work item about `code` being discarded was resolved before Epic 3 began, so no client plumbing is a prerequisite. What does remain: some backend validation paths were accepted with human-readable field messages on the understanding that Epic 3 would give them stable codes plus i18n entries — expect to audit and fill those gaps.
- Epic 4 depends on Epic 3: the one-command full-stack demo is expected to exercise Epic 1–3 functionality, including the language toggle.
