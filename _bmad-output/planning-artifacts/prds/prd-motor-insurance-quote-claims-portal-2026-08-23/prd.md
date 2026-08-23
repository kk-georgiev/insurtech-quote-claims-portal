---
title: Motor Insurance Quote & Claims Portal — Milestone 1: Full-Stack Skeleton
created: 2026-08-23
updated: 2026-08-23
status: final
---

# PRD: Motor Insurance Quote & Claims Portal — Milestone 1: Full-Stack Skeleton

## 0. Document Purpose

This PRD scopes **Milestone 1** only — not the full training assignment. It is written for the two-person team and their Sirma Academy mentor, and feeds the next BMAD steps directly (`bmad-architecture`, `bmad-create-epics-and-stories`). It builds on existing project inputs rather than duplicating them: `assignment.md` (original brief), `docs/motor_insurance_portal_business_analysis.md` (full business analysis — the source of truth for the eventual full product), `docs/uml_diagrams.md`, and `docs/questions.md`. Where this milestone's scope narrows or overrides the business analysis, that is called out explicitly. Technology choices and other implementation-level decisions are recorded in `addendum.md` alongside this PRD, not repeated here.

## 1. Vision

The full Motor Insurance Quote & Claims Portal (per the business analysis) is a multi-month training deliverable. Milestone 1 is the first checkpoint: a **runnable, full-stack skeleton** that proves the team's chosen architecture end-to-end and gives the mentor something concrete to react to — before the team invests in the harder domain modules (Policy, Claims, Notifications).

Concretely, Milestone 1 answers one question for the mentor meeting: *"Does this team's chosen stack, auth model, role structure, and deployment story actually work, top to bottom?"* — demonstrated by one real, working vertical slice (Quote calculation) sitting inside a real auth and role system, all launchable with one command.

This is deliberately a skeleton, not a feature-complete increment. Visual design is out of scope; the goal is structural proof, not polish.

## 2. Target User

### 2.1 Jobs To Be Done

- As the **project team**, we need a working full-stack foundation we can safely build every later module on top of, so that Policy/Claims work later isn't blocked on unresolved auth/deployment questions.
- As the **mentor**, I need to see the team's architectural decisions (auth, roles, deployment) actually running, not just described in documents, so I can give grounded feedback before more is built on top.
- As a **portal client** (within this milestone's limited slice), I want to register, log in, and get an instant, transparent insurance premium estimate, so I can see if this kind of coverage is affordable before committing further.

### 2.2 Non-Users (v1)

- Portal clients seeking actual policy issuance or claims filing — not in this milestone (see §5 Non-Goals).
- Agents, Liquidators, and Administrators performing real staff workflows — this milestone only proves they can authenticate and land in the right place; their actual workflows are future milestones.

### 2.3 Key User Journeys

- **UJ-1. Elena gets an instant premium estimate.**
  - **Persona + context:** Elena, a first-time car buyer, wants a rough sense of insurance cost before she finalizes her purchase.
  - **Entry state:** Unauthenticated, lands on the portal's public landing/login screen.
  - **Path:** Registers with email + password (defaults to CLIENT role) → logs in → opens "New Quote" → enters driver age, driving experience, region, vehicle power, and bonus-malus level → submits.
  - **Climax:** The system returns a total premium with a full breakdown (base premium × each factor), within seconds.
  - **Resolution:** Elena can view the same quote again later by its ID. She is not yet able to accept it into a policy — that is a later milestone.
  - **Edge case:** Elena enters a driving-experience value inconsistent with her stated age (e.g., more years of experience than are physically possible) — the system rejects the submission with a specific, field-level validation message rather than a generic error.

- **UJ-2. A staff member confirms their role lands them in the right place.**
  - **Persona + context:** Toma, an Agent (equally representative of Liquidator or Administrator for this milestone — the journey is structurally identical, only the destination screen differs), has been given a seeded demo account.
  - **Entry state:** Unauthenticated, on the login screen.
  - **Path:** Logs in with the seeded credentials → is routed automatically to the Agent navigation shell (not the Client one, not another staff role's).
  - **Climax:** Toma sees an Agent-labeled placeholder screen and an Agent-specific navigation menu — proof the role separation is real, not cosmetic.
  - **Resolution:** Toma cannot navigate into Client, Liquidator, or Administrator areas; attempting to hit another role's API directly (e.g., via browser URL or a raw HTTP call) is rejected by the backend, not just hidden by the frontend.

- **UJ-3. The team demos the skeleton to the mentor from a clean machine.**
  - **Persona + context:** Konstantin (or a teammate) is setting up the project on a machine that has never run it before, shortly before the mentor meeting.
  - **Entry state:** Fresh clone of the repository, Docker installed, nothing else running.
  - **Path:** Runs a single documented command → Postgres, backend, and frontend all start and become reachable.
  - **Climax:** The app is usable end-to-end (UJ-1, UJ-2) without any manual database setup, seeding step, or environment troubleshooting.
  - **Resolution:** The team demos live instead of showing screenshots or a recording.

## 3. Glossary

- **Quote** — A single premium calculation request and result: the driver/vehicle inputs, the tariff factors applied, the resulting premium, and a validity window. Persisted once created; immutable after creation (a new quote is created for a re-calculation, not an edit).
- **Premium** — The total price for the Quote's coverage period, in EUR, expressed with exact decimal precision.
- **Price Breakdown** — The base premium and each individual tariff factor (age, experience, region, power, bonus-malus) shown alongside the total Premium, so the calculation is auditable by the client.
- **Tariff Factor** — A multiplier applied to the base premium for one rating dimension (e.g., driver age). Milestone 1 uses one fixed, explicitly-temporary set of factors (see `addendum.md`); factor versioning is out of scope (§5).
- **Role** — One of CLIENT, AGENT, LIQUIDATOR, ADMINISTRATOR (per the business analysis §4 permission matrix). A User has exactly one Role in this milestone.
- **Navigation Shell** — The role-specific set of screens and menu a User sees after login. Distinct per Role; enforced structurally (routing + backend authorization), not just visually.
- **Seeded Account** — A demo User provisioned via database migration rather than through self-registration, used for the three staff roles.
- **Skeleton** — This milestone's deliverable: a structurally complete but visually unpolished full-stack foundation.
- **Display Language** — The language the frontend renders in: Bulgarian (default) or English. A per-session frontend setting, not a per-User stored preference in this milestone.

## 4. Features

### 4.1 Authentication & Authorization

**Description:** Every screen in the portal sits behind real authentication. CLIENT accounts are self-service; AGENT/LIQUIDATOR/ADMINISTRATOR accounts are staff-provisioned, matching how a real insurer would operate — self-registration into a staff role would be a privilege-escalation bug, not a convenience. Role checks are enforced on the backend for every protected endpoint; the frontend's routing behavior is a UX convenience, never the security boundary. Realizes UJ-1, UJ-2.

**Functional Requirements:**

#### FR-1: Client self-registration

A prospective client can register with an email and password. Realizes UJ-1.

**Consequences (testable):**
- A successful registration creates a User with Role = CLIENT.
- Duplicate email registration is rejected with a specific, non-generic error.
- Passwords are never stored or logged in plain text.

**Out of Scope:**
- Email verification, password-strength UI meter, "forgot password" flow.

#### FR-2: Login for any account (self-registered or seeded)

Any User — client-registered or staff-seeded — can log in with email and password and receive a token that identifies their Role. Realizes UJ-1, UJ-2.

**Consequences (testable):**
- A successful login returns a token containing (at minimum) the user's identity and Role.
- An incorrect password or unknown email is rejected with a generic "invalid credentials" message (not "email not found" — avoids user enumeration).

#### FR-3: Backend-enforced role authorization

Every protected API endpoint checks the caller's Role from their token before executing, independent of any frontend behavior. Realizes UJ-2.

**Consequences (testable):**
- A request to a Role-restricted endpoint from a User with the wrong Role returns HTTP 403, not a partial or filtered response.
- A request with no token, or an invalid/expired token, returns HTTP 401.

#### FR-4: Seeded staff accounts

One demo account per staff Role (AGENT, LIQUIDATOR, ADMINISTRATOR) exists after a fresh database migration, with credentials documented for the team/mentor. Realizes UJ-2, UJ-3.

**Consequences (testable):**
- After running migrations on an empty database, exactly one User per staff Role exists and can log in (FR-2) without any manual step.

**Feature-specific NFRs:**
- Passwords (self-registered and seeded) are hashed, never stored in plain text, including in the seed migration itself.

### 4.2 Role-Based Navigation Shell

**Description:** After login, a User is dropped into the Navigation Shell for their Role. For this milestone, only the CLIENT shell has real functionality (the Quote flow, §4.3); AGENT/LIQUIDATOR/ADMINISTRATOR shells are placeholder screens that exist to prove the structural separation, not to deliver staff functionality yet. Realizes UJ-2. [ASSUMPTION: each non-Client shell needs exactly one placeholder screen for this milestone — no additional sub-navigation within a role until that role's real functionality is scoped in a later milestone.]

**Functional Requirements:**

#### FR-5: Role-based post-login routing

Immediately after login, the User is routed to the Navigation Shell matching their Role, with no manual role selection. Realizes UJ-2.

#### FR-6: Placeholder screen per role

Each of the four roles has at least one reachable, role-labeled screen (e.g., "Agent workspace — coming soon"). Realizes UJ-2, UJ-3.

**Out of Scope:**
- Any real staff functionality (agent-assisted quoting, claim review queues, tariff administration) — these are future milestones per the business analysis.

#### FR-7: Frontend route guards

The frontend refuses to render another role's screens even if the User manually navigates to that URL, redirecting instead to their own shell. Realizes UJ-2.

**Notes:** This is a UX guard, not the security boundary — FR-3 is. Stated separately because both are independently testable and a reviewer should not conflate them.

### 4.3 Quote Engine (Vertical Slice)

**Description:** The one piece of real domain functionality in this milestone, carried over in spirit from the team's earlier prototype: a client enters driver and vehicle parameters and receives an immediate, transparent premium calculation. The pricing formula itself is explicitly a placeholder (§5, and detailed in `addendum.md`) — what this milestone actually proves is the vertical slice mechanics (input → calculation → persistence → retrieval → transparent breakdown), which the eventual real tariff engine will slot into unchanged. Realizes UJ-1.

**Functional Requirements:**

#### FR-8: Quote calculation

An authenticated CLIENT can submit driver age, driving experience, region, vehicle power, and bonus-malus level, and receive a calculated Premium. Realizes UJ-1.

**Consequences (testable):**
- Out-of-range or logically inconsistent inputs (e.g., experience years exceeding what the stated age allows) are rejected with field-level validation errors, not a generic failure.
- The Premium is always a positive, exactly-precise decimal amount — never a value with floating-point rounding artifacts.

#### FR-9: Transparent price breakdown

The Quote response includes the base premium and every individual Tariff Factor applied, not just the total. Realizes UJ-1.

#### FR-10: Quote persistence

Every calculated Quote is saved with its inputs, applied factors, resulting Premium, and creation time. Realizes UJ-1.

#### FR-11: Quote retrieval

A CLIENT can retrieve a Quote they previously created by its ID. Realizes UJ-1.

**Out of Scope:**
- Accepting a Quote into a Policy (later milestone, per business analysis §7).
- Listing/browsing all of a client's past quotes (single-ID retrieval only, for this milestone).

### 4.4 Local & Docker-Based Deployment

**Description:** The whole stack must be runnable two ways: the fast local dev loop the team uses day-to-day, and a single Docker Compose command that works from a completely clean checkout — the latter exists specifically so the mentor demo (UJ-3) never depends on anyone's personal machine state. Realizes UJ-3.

**Functional Requirements:**

#### FR-12: One-command full-stack startup

From a clean checkout with only Docker installed, one documented command brings up the database, backend, and frontend, fully wired to each other. Realizes UJ-3.

**Consequences (testable):**
- No manual database creation, migration step, or config edit is required beyond what the one command (plus documented prerequisite files, e.g. copying `.env.example`) performs.

#### FR-13: Local dev workflow preserved

A developer can still run backend and frontend directly (not containerized) against a containerized database, for fast iteration during active development.

**Cross-Cutting NFRs**

- **Money precision:** Every monetary value (Premium, base premium, factors) is represented and calculated with exact decimal precision end-to-end (API, persistence, and any arithmetic) — floating-point representations are never used for money, anywhere in the stack.
- **Security baseline:** No password, token secret, or credential appears in source control, logs, or committed configuration — only in `.env`-style files excluded from version control, with an `.env.example` documenting required variables without real values.
- **Data integrity:** Validation rules stated in this PRD (FR-1, FR-8) are enforced at both the API layer and the database layer (constraints), so invalid data cannot enter the system through any path, including direct database access during development.
- **Startup reliability:** [ASSUMPTION] The full Docker stack (FR-12) should become usable within a couple of minutes on a typical development laptop on first run (image pulls aside) and near-instantly on subsequent runs — exact target is a call for the architecture phase, not fixed here.

### 4.5 Internationalization (Bulgarian / English)

**Description:** Every screen and user-facing message introduced by this milestone (§4.1–4.3, including the placeholder screens in §4.2) renders in either Bulgarian or English, switchable by the user. Bulgarian is the default for a first-time visitor — it is the real market language for this product — with English available as a toggle (useful for the mentor and any non-Bulgarian-speaking reviewer). This is a frontend-only concern for Milestone 1: the backend stays language-agnostic and is not touched by this feature. Applies across UJ-1, UJ-2, UJ-3.

**Functional Requirements:**

#### FR-14: Language toggle

A user, authenticated or not, can switch the Display Language between Bulgarian and English at any time. Realizes UJ-1, UJ-2.

**Consequences (testable):**
- A first-time, unauthenticated visitor sees the product in Bulgarian by default.
- Every screen and message introduced in this milestone — login, registration, quote form and result/breakdown, validation errors, navigation labels, all four role placeholder screens — renders in the selected Display Language, with no untranslated fallback text visible.
- The Display Language selection persists across page reloads within the same browser session. [ASSUMPTION: client-side persistence only (e.g., local storage) — no server-side, per-account stored language preference in this milestone; that is a plausible later enhancement, not required here.]

**Out of Scope:**
- Any language beyond Bulgarian and English.
- Server-side / per-account persisted language preference (see assumption above).

#### FR-15: Backend stays language-agnostic

Backend API responses — including validation and error responses — use stable, language-independent codes/keys rather than embedding human-readable prose that gets shown to the user unmapped. Realizes FR-14 (makes it possible without backend changes).

**Consequences (testable):**
- Switching the Display Language never requires a different backend request or response shape — the same API response renders differently only because the frontend maps it to the selected language.

**Feature-specific NFRs:**
- Adding a new screen or message later must not require backend changes purely to support translation — translation stays a frontend-owned concern end to end.

## 5. Non-Goals (Explicit)

- **Policy issuance** is not part of this milestone. A Quote is the end of the line for now (business analysis §7 is future work).
- **Claims / FNOL** are not part of this milestone (business analysis §8 is future work).
- **Notifications** (in-app or otherwise) are not part of this milestone (business analysis §9).
- **Tariff versioning and admin management** (editable tariff tables, DRAFT/ACTIVE/RETIRED lifecycle) are not part of this milestone — Milestone 1 uses one fixed, hardcoded demo tariff (business analysis §6.4–6.5 describe the real target design for later).
- **Visual design and UI polish** are explicitly out of scope. Screens must be functional and correctly role-gated; they do not need to look finished.
- **Production-grade auth hardening** (refresh-token rotation, rate limiting on login, account lockout, audit logging of auth events) is deferred — this milestone's auth must be *correct*, not yet *hardened*.
- **Mobile / responsive layout** is not a requirement for this milestone.
- **Agent-assisted quoting** ("quote on behalf of a client," per business analysis §12.2) is not part of this milestone — the Agent shell is a placeholder only (FR-6).
- **Languages beyond Bulgarian and English** are not supported.
- **Server-side / per-account persisted language preference** is not part of this milestone (FR-14 assumption) — the Display Language is a client-side, per-session setting only.
- **Backend-side localization** (Accept-Language handling, server-rendered message bundles) is explicitly not built — translation is a frontend-only concern (FR-15).

## 6. MVP Scope

### 6.1 In Scope

- CLIENT self-registration and login for all roles (§4.1).
- Backend-enforced role authorization on every protected endpoint (§4.1).
- Seeded AGENT/LIQUIDATOR/ADMINISTRATOR demo accounts (§4.1).
- Role-based navigation shell for all four roles, with real content only for CLIENT (§4.2).
- End-to-end Quote calculation, breakdown, persistence, and retrieval for CLIENT (§4.3).
- One-command full Docker Compose startup, alongside a preserved local dev workflow (§4.4).
- Bulgarian (default) / English language toggle across every screen and message introduced in this milestone, frontend-only (§4.5).

### 6.2 Out of Scope for MVP

- Everything listed in §5 Non-Goals.
- Any backend module beyond `auth` and `quote`/`pricing` being functionally complete — `customer`, `vehicle`, `policy`, `claim`, `notification`, `tariff` modules may exist as empty scaffolding (per the modular-monolith package structure the team already committed to) but carry no business logic yet. [NOTE FOR PM: confirm with the architecture phase whether empty module packages should even be scaffolded now or only created when their first real feature lands — either is defensible.]

## 7. Success Metrics

**Primary**
- **SM-1**: A teammate can clone the repository on a machine that has never run the project, run one documented command, and reach a fully working app — measured by a dry run before the mentor meeting. Validates FR-12, FR-13.
- **SM-2**: UJ-1 (register → log in → request quote → see breakdown) completes live in front of the mentor without any manual database intervention. Validates FR-1, FR-2, FR-8, FR-9, FR-10.

**Secondary**
- **SM-3**: All four roles (one self-registered CLIENT, three seeded staff accounts) can log in and each lands on its own distinct, correctly-labeled screen, and a direct API call with the wrong role's token is rejected. Validates FR-2, FR-3, FR-4, FR-5, FR-6.
- **SM-4**: Every screen and message introduced in this milestone renders correctly in both Bulgarian and English, with no untranslated key or English-fallback artifact visible in the Bulgarian (default) pass. Validates FR-14, FR-15.

**Counter-metrics (do not optimize)**
- **SM-C1**: Time spent on visual/UI polish. Explicitly a non-goal (§5) — time here is time not spent proving the architecture, which is this milestone's actual purpose. Counterbalances SM-2.

## 8. Open Questions

1. The team's earlier prototype branch (`feat/quote-engine-v1`) reportedly has known issues a teammate flagged but has not yet detailed. Before the architecture phase finalizes the Quote Engine's internal design, it would be worth getting those specifics — this milestone rebuilds the vertical slice from scratch, but repeating a known mistake unknowingly would be wasteful. [NOTE FOR PM]
2. Token lifetime/refresh strategy for the JWT (FR-2) is deliberately left to the architecture phase — this PRD only requires that a Role-bearing token exists and is checked (FR-2, FR-3).
3. Whether the non-Client placeholder screens (FR-6) need any interactive element at all for the demo to land well, or whether a static labeled page is sufficient — a UX/architecture call, not a product one.

## 9. Assumptions Index

- §4.2 — Each non-Client Navigation Shell needs exactly one placeholder screen for this milestone; no sub-navigation within a role yet.
- §4.4 (Cross-Cutting NFRs) — Full Docker stack startup should land within a couple of minutes on a typical dev laptop on first run; exact target deferred to architecture.
- §4.5 (FR-14) — Display Language persistence is client-side/per-session only for this milestone; no server-side per-account preference.
