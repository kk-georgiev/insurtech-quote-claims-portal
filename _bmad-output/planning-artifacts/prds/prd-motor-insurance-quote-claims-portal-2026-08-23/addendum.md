# Addendum — Motor Insurance Quote & Claims Portal, Milestone 1

Technical-how content that doesn't belong in `prd.md` (capabilities, not implementation), preserved here for the architecture phase (`bmad-architecture`).

## Stack (already decided by the team, not open for re-litigation in architecture)

- Backend: Java 21, Spring Boot, Maven, PostgreSQL, Flyway for migrations.
- Frontend: React + TypeScript, Vite.
- Repo shape: monorepo — `backend/`, `frontend/`, `docs/` — plus root-level CI/Docker tooling (`.github/workflows/`, `docker-compose.yml`, `.env.example`). Application code stays strictly inside `backend/`/`frontend/`; only cross-cutting tooling lives at root.
- Backend architecture: modular monolith, package-per-module (`auth`, `customer`, `vehicle`, `pricing`, `quote`, `policy`, `claim`, `notification`, `tariff`, `shared`), each module internally layered `api` / `application` / `domain` / `persistence` (or `infrastructure`) — matches business analysis §10.1.
- REST API versioned under `/api/v1`.

## Auth mechanism

- JWT (stateless), confirmed by the mentor — overrides the business analysis's session-cookie recommendation (§10.2) for this project.
- Rationale on record: mentor approval is the deciding factor; no further debate needed at architecture time. Token lifetime/refresh strategy is still open (PRD §8, Open Question 2) — architecture phase should propose a lifetime and state whether refresh tokens are in scope for Milestone 1 or deferred.
- Passwords: BCrypt (or equivalent adaptive hash) — matches business analysis §15 security NFRs.

## Quote Engine — Milestone 1 demo formula

Reused, not reinvented — this is the same formula the team's earlier prototype (`docs/quote_pricing_v1.md` on `feat/quote-engine-v1`) already worked out and documented as an explicit placeholder:

```
premium = base_premium × age_factor × experience_factor × region_factor × power_factor × bonus_malus_factor
```

- Base premium: 180.00 EUR (placeholder value).
- Final premium rounded to 2 decimals, bounded between 120.00 EUR and 1500.00 EUR.
- Inputs: driver age 18–100; driving experience 0–82 years, not greater than age minus 17; region `SOFIA`/`LARGE_CITY`/`OTHER`; vehicle power 20–500 kW; bonus-malus `BONUS_20`/`BONUS_10`/`NEUTRAL`/`MALUS_25`/`MALUS_50`.
- **Note:** `docs/quote_pricing_v1.md` (the source of these tables) exists only on the team's earlier prototype branch (`feat/quote-engine-v1`), not on `main` or this greenfield rebuild's branch — the team deliberately branched fresh rather than off that branch (see "Known unknown" below). These tables are reproduced here in full so Milestone 1 has no undocumented dependency on a branch not in use.

**Age factor**

| Rule | Factor |
|---|---:|
| under 25 | 1.350 |
| 25–29 | 1.150 |
| 30–69 | 1.000 |
| 70+ | 1.250 |

**Driving experience factor**

| Rule | Factor |
|---|---:|
| under 2 years | 1.300 |
| 2–4 years | 1.100 |
| 5+ years | 1.000 |

**Region factor**

| Value | Factor |
|---|---:|
| Sofia | 1.200 |
| Other large city | 1.100 |
| Other region | 1.000 |

**Vehicle power factor**

| Rule | Factor |
|---|---:|
| up to 74 kW | 0.900 |
| 75–110 kW | 1.000 |
| 111–150 kW | 1.150 |
| above 150 kW | 1.350 |

**Bonus-malus factor**

| Value | Factor |
|---|---:|
| Bonus 20% | 0.800 |
| Bonus 10% | 0.900 |
| Neutral | 1.000 |
| Malus 25% | 1.250 |
| Malus 50% | 1.500 |

Either way, the design constraint from the PRD stands: this formula must be swappable without touching the vertical-slice mechanics (persistence, API shape, breakdown response) around it.
- Money handling: exact-decimal arithmetic throughout (e.g., Java `BigDecimal`; Postgres `NUMERIC`) — never floating-point — per PRD Cross-Cutting NFRs and business analysis §15.

## Docker

- Milestone 1 needs Dockerfiles for **both** backend and frontend (not just Postgres, which is what the team's earlier prototype had) — full-stack `docker compose up` from a clean checkout is a hard requirement (PRD FR-12), per explicit mentor recommendation.
- Local dev workflow (`mvn spring-boot:run` / `npm run dev` against a Dockerized Postgres only) must remain available alongside the full-stack Compose profile (PRD FR-13) — architecture phase should decide the cleanest way to support both (e.g., Compose profiles/overrides) without duplicating config.

## Internationalization

- Frontend-only, per PRD §4.5 (FR-14, FR-15) — backend untouched, stable error codes only, no Accept-Language handling.
- Bulgarian default, English toggle, client-side persistence only (no server-side per-account preference in Milestone 1).
- Library/mechanism choice (e.g., `react-i18next`, `next-intl`-style catalogs, or a simpler hand-rolled key→string map given the small screen count in this milestone) is an architecture-phase decision, not fixed here — the PRD only fixes the *capability*, not the *how*.

## Known unknown

- A teammate (not present in this PRD conversation) reportedly flagged issues in the earlier prototype branch `feat/quote-engine-v1` / `chore/initial-project-foundation`. Specifics are not yet known. The team deliberately chose a greenfield rebuild from `main` rather than branching off that prototype (decision made outside this PRD run), partly to avoid inheriting whatever those issues are — but since the *formula* and general *shape* are still being carried forward in spirit, it would be worth surfacing those specifics before or during architecture, in case they're relevant to the parts being reused.
