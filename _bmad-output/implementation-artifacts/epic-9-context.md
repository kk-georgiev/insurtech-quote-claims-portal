# Epic 9 Context: Documented and Tidy

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic closes out two cleanups that make the milestone legible to a reviewer rather than adding new product surface. Neither story is release-blocking — the epic is should-have and yields first if time runs short, and sequences after Epics 6-8. It has two independent purposes: give the REST API browsable, generated documentation (since the API surface roughly triples across Milestones 3-4, making this cheaper now than retrofitted later), and bring the repository's own documentation and stylesheet in line with reality — fixing a root README status section that is several epics stale, and closing an acceptance gap Milestone 2 left open (its own FR-1, "no hardcoded hex on a touched screen," is not literally true today).

## Stories

- Story 9.1: OpenAPI Documentation
- Story 9.2: Documentation and Legacy-CSS Cleanup

## Requirements & Constraints

- The API documentation must cover every `/api/v1` endpoint with accurate request/response shapes, generated rather than hand-written.
- Adding the OpenAPI tooling dependency is conditional: its compatibility with the project's Spring Boot version must be verified against the current release, not assumed. If no compatible release exists, the story defers rather than pinning the framework backwards — it's a should-have precisely so it can't hold up the milestone.
- Provenance constraint (binding, product owner-level): wherever the bonus-malus rating scale is surfaced to a reader — seed migration comment, README, OpenAPI description, UI — it must carry an explicit note that the coefficients are the project's own internal demo model, inherited from the team's prototype, and are not official, actuarially derived, or regulatorily mandated values for the Bulgarian insurance market. This applies directly to the OpenAPI description work in Story 9.1 and the README work in Story 9.2.
- The root README's "Status" section must be updated to reflect the real project state (it currently reads several epics stale).
- The README must record that driving experience (стаж) is deliberately excluded from the rating model — a documented choice, not an oversight.
- The claims/FNOL feature (when documented) must not be described as a full implementation of the legal motor third-party-liability process — it is out of scope for this project; this is a general documentation-accuracy constraint worth keeping in mind if the README touches claims language.

## Technical Decisions

- Story 9.1 depends on adding `springdoc-openapi` (or equivalent) as a new dependency, gated on a real compatibility check against the current Spring Boot release rather than an assumption.
- Story 9.2 targets `frontend/src/index.css`'s surviving `@layer legacy` block. After prior cleanup passes, only four legacy rules still have a live consumer: the `box-sizing` reset, the `body` rule (color/background/fallback font), a `dt { font-weight: 600 }` rule (QuoteResult's `<dt>` has no weight utility yet), and a `[data-testid='quote-result']` rule supplying `padding-top` and `border-top: 1px solid #e2e8f0` above the quote breakdown card (that screen's own `className="mt-6"` only overrides `margin-top`, so the hardcoded hex still renders). Everything else in the legacy block is dead — no screen still depends on it.
- The cleanup should delete every legacy rule with no live consumer, and migrate the four survivors to tokens/utilities: the `dt` font-weight becomes a utility class on `QuoteResult`'s `<dt>`; the quote-result `padding-top`/`border-top` divider becomes a token-based utility or a `Card` prop rather than a raw hex value. Only `box-sizing` and `body` are expected to remain as genuinely global, non-component-specific rules.
- Completing this migration is what closes Milestone 2's own open acceptance gap: FR-1 ("no hardcoded hex in any screen touched by that milestone") is satisfied in JSX/className but violated via this legacy stylesheet import, and the fix is what makes FR-1 literally true.

## Cross-Story Dependencies

- Story 9.1 (OpenAPI documentation) has already been implemented on a separate, not-yet-merged branch; its scope does not overlap with Story 9.2's README/CSS cleanup work.
- Story 9.2's README update should incorporate the same bonus-malus provenance disclosure language that Story 9.1 adds to the OpenAPI description, so the two stay consistent with each other and with the seed-migration comment.
