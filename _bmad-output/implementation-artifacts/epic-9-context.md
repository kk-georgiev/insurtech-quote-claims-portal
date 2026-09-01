# Epic 9 Context: Documented and Tidy

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This is a should-have epic that closes out Milestone 3 by making the project legible to a reviewer: it adds generated API documentation and brings the README and stylesheet in line with what actually exists. It also closes an open acceptance gap from Milestone 2, where FR-1 ("no hardcoded hex on any touched screen") was satisfied in JSX/className but not in the shared CSS the screens still import. Neither story is release-blocking; if time runs short, this epic yields first (sequenced last, after Epics 6-8).

## Stories

- Story 9.1: OpenAPI Documentation
- Story 9.2: Documentation and Legacy-CSS Cleanup

## Requirements & Constraints

- Every `/api/v1` endpoint must appear in generated OpenAPI documentation with request and response shapes, once a compatible `springdoc-openapi` release exists.
- If no `springdoc-openapi` release is compatible with the current Spring Boot version, the OpenAPI story is deferred rather than pinning the framework backwards — this is not optional-effort scope, it's a hard go/no-go check done first.
- Wherever the bonus-malus coefficient scale is surfaced to a reader — OpenAPI description, README, UI — it must carry an explicit statement that it is the project's own demo/illustrative data, not official or regulatorily determined Bulgarian market values. This is a binding provenance constraint, not a stylistic preference.
- The root README's "Status" section must be updated to reflect the real, current state of the project (it is currently stale by three epics).
- The README must record that driving experience (стаж) is deliberately excluded as a rating factor — a documented team choice, not an oversight.
- Every `@layer legacy` rule in `frontend/src/index.css` with no live consumer must be deleted; surviving rules must be migrated to design tokens or component props rather than left as raw CSS.
- The stale legacy-layer comment (currently claims rules are still load-bearing for unmigrated screens, which is no longer true — all screens have migrated) must be corrected to describe what the block actually still provides.
- This cleanup must not change behavior: the full existing test suite must pass unmodified after the CSS cleanup.

## Technical Decisions

- Stack has no changes for this epic other than the candidate addition of `springdoc-openapi` (Java 21, Spring Boot 4.1.1, Maven backend; React 19/TypeScript 6/Vite 8/Tailwind v4 frontend — all unchanged).
- Before adding `springdoc-openapi`, verify its current release line is actually compatible with Spring Boot 4.1.x — don't assume compatibility just because Spring Boot 4 is recent.
- Errors and other cross-cutting conventions (money as `BigDecimal`/`NUMERIC` HALF_UP, ownership scoped in the query, the AD-7 error envelope, AD-8 i18n) are inherited unchanged and are not themselves in scope for this epic's stories.

### Legacy CSS specifics (frontend/src/index.css)

Per Epic 5 retro items F1-F3 (retro items 36-38), the `@layer legacy` block (originally lines ~63-232) is ~90% dead. Only four rules still have a live consumer as of the retro:
- `box-sizing` reset — keep.
- `body` (color/background/fallback font) — keep.
- `dt { font-weight: 600 }` — `QuoteResult`'s `<dt>` has no weight utility; migrate to a utility class on that element.
- `[data-testid='quote-result']` rule supplying `padding-top` and `border-top: 1px solid #e2e8f0` — the element's `className="mt-6"` only overrides `margin-top`, so this legacy rule still renders a hardcoded-hex divider line above the quote breakdown card on a live screen. Migrate this to tokens or a `Card` prop.

All other element-selector rules in the block (`main`, `main > section`, `form div|label|input|select|textarea`, `button` and its states, remaining `dl`/`dt` color rules, etc.) have no live consumer — every screen that once used them now renders through `components/ui/` primitives that override them — and should be deleted outright.

The existing comment in the block (written during Story 5.4) claims the rules are "still load-bearing for screens not yet migrated (e.g. `HealthStatus`)" — this is stale; `HealthStatus` was migrated in Story 5.6 and no screen remains unmigrated. Replace it with an accurate statement of what the retained block actually provides.

Completing this migration makes Milestone 2's FR-1 ("no hardcoded hex in touched screens") literally true, since the `#e2e8f0` hex was rendering via imported CSS rather than authored component code — closing that milestone's open acceptance gap.

## Cross-Story Dependencies

- Story 9.1 has an internal go/no-go gate: it depends on a `springdoc-openapi` release actually being compatible with Spring Boot 4.1.1. If not, the story is deferred rather than attempted with a downgraded framework — this has no dependency on Story 9.2.
- Story 9.2's CSS cleanup and README update are independent of Story 9.1 and of each other's acceptance criteria, but both stories share the provenance-note requirement for the bonus-malus scale (each must carry the demo-data disclaimer in its own surface — API docs for 9.1, README for 9.2).
