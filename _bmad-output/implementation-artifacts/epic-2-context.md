# Epic 2 Context: Every Role Gets Their Own Workspace

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Any of the four roles — CLIENT plus the three staff roles provisioned via seed data — logs in and lands on their own correctly role-guarded navigation shell, proving the role model is structurally real rather than cosmetic. Epic 1 proved one role end to end; this epic proves the role *system*: staff accounts exist after a fresh migration with no manual setup, login routes each user automatically to their own shell, every role has a distinct role-labeled screen, and the frontend refuses to render another role's screens even on manual URL navigation. Only the CLIENT shell carries real functionality (Epic 1's quote flow); the staff shells are deliberately static placeholders whose whole job is to make role separation visible. This is structural proof for the demo, not the start of staff functionality.

## Stories

- Story 2.1: Seeded Staff Demo Accounts
- Story 2.2: Role-Based Post-Login Routing
- Story 2.3: Placeholder Screens for Agent, Liquidator, and Administrator
- Story 2.4: Frontend Route Guards Per Role

## Requirements & Constraints

- After migrations run against an empty database, exactly one User per staff Role (AGENT, LIQUIDATOR, ADMINISTRATOR) exists and can log in through the existing login path with no manual setup step and no special-case handling for seeded accounts.
- Seeded passwords are hashed by the same adaptive hash the self-registration path uses (BCrypt or equivalent) — no plaintext anywhere, including inside the migration file itself. Working credentials are documented for the team and mentor; the database and the migration hold only hashes.
- Staff roles remain non-self-registrable — provisioning is seed/staff-side only, since self-registration into a staff role would be privilege escalation rather than convenience.
- A User has exactly one Role this milestone; the Role travels in the login token and drives everything downstream.
- Post-login routing is automatic: no manual role-selection step. CLIENT lands on the client shell (the quote flow entry point); each staff role lands on its own shell.
- Every role has at least one reachable, distinctly role-labeled screen. Staff screens are static and non-interactive (e.g. "Agent workspace — coming soon") — exactly one placeholder per staff role, with no sub-navigation inside a role.
- Manual navigation to another role's URL redirects the user back to their own shell and never renders the other role's content; this must hold symmetrically for every role against every other role's routes.
- The frontend guard is a UX convenience, never the security boundary. Backend role checks (401 for missing/invalid token, 403 for wrong role) stand independently of anything the frontend does, and a direct API call carrying the wrong role's token must still be rejected server-side.
- Out of scope: any real staff functionality (agent-assisted quoting, claim review queues, tariff administration), per-role sub-navigation, visual polish, and mobile/responsive layout. Screens must be functional and correctly gated, not finished-looking.
- Translation of the new shell copy is a later concern, but any new backend error code introduced here ships with its matching i18n entry in the same change.

## Technical Decisions

- Staff seed data ships as a Flyway migration (`V{n}__{description}.sql`) alongside the `auth` module's schema. Which migration file, the exact seeded emails, and the hash-generation approach are this story's call — the only fixed constraint is that the stored hashes are produced the same way the registration path produces them.
- No new backend module is created for this epic: only `auth`, `quote`, `pricing`, and `shared` exist, and modules are created only when a real capability needs them.
- Role authorization stays backend-enforced on every protected endpoint via the one shared JWT validation filter; modules read the current user and Role from the security context, never by calling `auth` directly.
- Frontend routing is owned entirely by React Router v8. Exactly one role-guard wrapper component gates every role-restricted route — never per-screen ad hoc role checks. The user's Role for routing purposes comes from the login token.
- Frontend layout for this epic: router setup, root layout, and the role-guard wrapper live under `frontend/src/app`; the static staff screens live under `frontend/src/features/shells/{agent,liquidator,administrator}`; the client shell's real content is Epic 1's `features/quote`.
- All backend calls go through the single typed `fetch`-based API client module — no data-fetching library this milestone.
- Errors keep the uniform `{timestamp, status, code, message, fieldErrors}` envelope produced by the one centralized handler in `shared`; `code` is a stable, language-independent `MODULE_REASON` key and is the only thing the frontend maps to display text.
- The backend emits no localized prose; i18n is 100% frontend-owned with keys namespaced per feature (`auth.*`, `quote.*`, `shells.*`), so the placeholder screen labels are frontend copy from the start.
- Conventions carried from Epic 1: `UUID` ids, `Instant` timestamps stored UTC, `snake_case` plural tables, REST under `/api/v1`, and the frontend API base URL always resolved from `VITE_API_URL`, never hardcoded.
- Stack pins: Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 18, Flyway, React 19, TypeScript 6.x, Vite 8, React Router 8, react-i18next.

## UX & Interaction Patterns

The staff journey this epic realizes: a staff user arrives unauthenticated at the login screen, logs in with seeded credentials, and is routed automatically to their own navigation shell — not the client one, not another staff role's. They see a role-labeled placeholder screen and a role-specific navigation menu, which is the visible proof that role separation is real. They cannot navigate into another role's area, and hitting another role's API directly is rejected by the backend rather than merely hidden by the UI. No visual design work is expected: correctness of gating and labeling is the deliverable.

## Cross-Story Dependencies

- Story 2.1 depends on Epic 1's User/Role schema, its password-hashing path, and its login endpoint — the seeded accounts must authenticate through that same unchanged path.
- Story 2.2 depends on Epic 1's login issuing a Role-bearing token, and on Epic 1's quote flow existing as the CLIENT shell's destination.
- Story 2.3 supplies the staff destinations that Story 2.2 routes to, so routing for staff roles is only demonstrable once those screens exist.
- Story 2.4 builds on 2.2's routing and 2.3's screens, and must be implemented as the single shared role-guard wrapper rather than as checks added to individual screens.
- Backend-enforced authorization from Epic 1 remains the real security boundary underneath this epic's frontend guard; the two are independently testable and must not be conflated.
- Epic 3 depends on all four shells and their copy existing here, so it can deliver full Bulgarian/English coverage with no untranslated text.
- Epic 4's clean-machine demo depends on Story 2.1's seeded accounts appearing automatically during the one-command startup's migrations, with no manual database intervention.
