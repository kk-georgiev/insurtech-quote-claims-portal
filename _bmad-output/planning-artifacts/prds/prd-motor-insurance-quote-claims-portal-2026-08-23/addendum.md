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

## Quote Engine — Milestone 1 tariff (zone/engine-cc based)

**Supersedes the placeholder formula, 2026-08-26.** The multiplicative age/experience/region/power/bonus-malus formula below was always a stand-in pending a real tariff (see original text preserved in git history at commit `24fe2bd`). A teammate has since produced a full GO (Гражданска отговорност — mandatory motor third-party liability) tariff from two source spreadsheets (`GO_tarifa_2585g.xlsx` for ages 25–85, `GO_tarifa_1824g.xlsx` for ages 18–24), cross-checked during this review against Bulgaria's 28 registration oblasti. This is the formula Story 1.5 implements; the old formula is kept below for history only and must not be coded.

```
one_time_premium   = base_premium(zone, engine_cc) + age_surcharge
total_premium      = one_time_premium + installment_fee(installments)
installment_amount = total_premium ÷ installments, rounded HALF_UP to 2 decimals
```

`installment_amount` is a nominal, per-installment display figure for the quote stage — Milestone 1 has no Policy/invoice entity yet to bill against, so exact remainder allocation across a real payment schedule (e.g. "last installment absorbs the odd cent") is out of scope here and deferred to whichever future story introduces actual invoicing. A quote showing `installments × installment_amount` off by at most one cent from `total_premium` is acceptable at this stage.

- Inputs: `driver_age` (integer, 18+), `region_code` (vehicle plate prefix — see mapping below), `engine_cc` (integer, cm³, 800+), `installments` (`1`, `2`, or `4`).
- Driving experience is **not** a rating factor in this model — explicit team simplification, replacing the old experience factor entirely. Drop any experience-based validation for this story.
- All monetary values: `BigDecimal`/`NUMERIC`, rounded `HALF_UP` to 2 decimals (AD-5, NFR-1) — no floating point.

**Age surcharge**

| Driver age | Surcharge |
|---|---:|
| 18–24 | +36.00 € |
| 25–85 | +0.00 € |
| 86+ | +10.00 € |

**Installment fee**

| Installments | Fee | Amount per installment |
|---|---:|---|
| 1 | +0.00 € | n/a — single payment |
| 2 | +2.00 € | total_premium ÷ 2 |
| 4 | +4.00 € | total_premium ÷ 4 |

**Base premium by zone × engine cc** (25–85y baseline, EUR, one-time payment; ranges inclusive both ends, non-overlapping; below 800 cm³ has no row — reject as invalid input)

| Zone | 800–1300 cm³ | 1301–2100 cm³ | 2101–2500 cm³ | 2501+ cm³ |
|---|---:|---:|---:|---:|
| Zone 1 | 131.91 | 141.12 | 144.18 | 166.17 |
| Zone 2 | 140.91 | 153.90 | 171.78 | 182.02 |
| Zone 3 | 128.85 | 135.49 | 143.67 | 166.17 |
| Zone 4 | 126.80 | 134.50 | 142.60 | 166.17 |
| Zone 5 | 140.09 | 148.79 | 156.97 | 169.24 |

**Region code → zone** (all 28 Bulgarian registration oblasti, verified against public plate-code references; codes are the Latin-lookalike form of the Cyrillic plate letters)

| Zone | Codes |
|---|---|
| Zone 1 | KH (Kyustendil), PK (Pernik), T (Targovishte), TX (Dobrich), BH (Vidin), CC (Silistra), K (Kardzhali), EB (Gabrovo), CH (Sliven), P (Ruse), PA (Pazardzhik), PP (Razgrad), CM (Smolyan) |
| Zone 2 | C (Sofia-city), PB (Plovdiv), CA, CB (Sofia-city overflow codes) |
| Zone 3 | E (Blagoevgrad), H (Shumen), BT (Veliko Tarnovo), BP (Vratsa), M (Montana), EH (Pleven) |
| Zone 4 | A (Burgas), OB (Lovech), X (Haskovo), CT (Stara Zagora), CO (Sofia-oblast/province), Y (Yambol) |
| Zone 5 | B (Varna) |

**Note on the zone groupings themselves:** the region-code-to-oblast mapping above (which code belongs to which of Bulgaria's 28 oblasti) was independently verified against public plate-code references. Which oblasti get *bucketed into the same pricing zone* (e.g. Plovdiv sharing Zone 2 with Sofia-city) was taken as given from the source spreadsheet and not independently re-derived — that grouping is the insurer's own actuarial risk-tiering choice, not something verifiable against a public reference the way an oblast's plate code is (review-loop finding, Story 1.5).

**Unresolved before seeding this table — confirm with the teammate who produced it, do not guess:**
- `BA` was listed in the source sheet as a Sofia-city sub-code (Zone 2). Cross-checking public sources shows `BA` is Bulgaria's special code for *military* vehicles, not a civilian regional code — excluded from the mapping above pending confirmation. Do not seed it as Zone 2 civilian data without resolving this.
- `CP` and `XX` were listed as further Sofia-city overflow codes in the source sheet but weren't independently verifiable against public sources during this review — kept out of the table above until confirmed. Low risk either way: it only affects quotes for those two specific plate prefixes, which fail closed as "unknown region" (a clear error) rather than silently mispricing.

Either way, the design constraint from the PRD stands: this formula must be swappable without touching the vertical-slice mechanics (persistence, API shape, breakdown response) around it.
- Money handling: exact-decimal arithmetic throughout (e.g., Java `BigDecimal`; Postgres `NUMERIC`) — never floating-point — per PRD Cross-Cutting NFRs and business analysis §15.

<details>
<summary>Superseded — original Milestone 1 placeholder formula (kept for history, do not implement)</summary>

Reused, not reinvented — this is the same formula the team's earlier prototype (`docs/quote_pricing_v1.md` on `feat/quote-engine-v1`) already worked out and documented as an explicit placeholder:

```
premium = base_premium × age_factor × experience_factor × region_factor × power_factor × bonus_malus_factor
```

- Base premium: 180.00 EUR (placeholder value).
- Final premium rounded to 2 decimals, bounded between 120.00 EUR and 1500.00 EUR.
- Inputs: driver age 18–100; driving experience 0–82 years, not greater than age minus 17; region `SOFIA`/`LARGE_CITY`/`OTHER`; vehicle power 20–500 kW; bonus-malus `BONUS_20`/`BONUS_10`/`NEUTRAL`/`MALUS_25`/`MALUS_50`.

**Age factor**: under 25 → 1.350, 25–29 → 1.150, 30–69 → 1.000, 70+ → 1.250
**Driving experience factor**: under 2y → 1.300, 2–4y → 1.100, 5+y → 1.000
**Region factor**: Sofia → 1.200, other large city → 1.100, other region → 1.000
**Vehicle power factor**: up to 74kW → 0.900, 75–110kW → 1.000, 111–150kW → 1.150, above 150kW → 1.350
**Bonus-malus factor**: Bonus 20% → 0.800, Bonus 10% → 0.900, Neutral → 1.000, Malus 25% → 1.250, Malus 50% → 1.500

</details>

## Docker

- Milestone 1 needs Dockerfiles for **both** backend and frontend (not just Postgres, which is what the team's earlier prototype had) — full-stack `docker compose up` from a clean checkout is a hard requirement (PRD FR-12), per explicit mentor recommendation.
- Local dev workflow (`mvn spring-boot:run` / `npm run dev` against a Dockerized Postgres only) must remain available alongside the full-stack Compose profile (PRD FR-13) — architecture phase should decide the cleanest way to support both (e.g., Compose profiles/overrides) without duplicating config.

## Internationalization

- Frontend-only, per PRD §4.5 (FR-14, FR-15) — backend untouched, stable error codes only, no Accept-Language handling.
- Bulgarian default, English toggle, client-side persistence only (no server-side per-account preference in Milestone 1).
- Library/mechanism choice (e.g., `react-i18next`, `next-intl`-style catalogs, or a simpler hand-rolled key→string map given the small screen count in this milestone) is an architecture-phase decision, not fixed here — the PRD only fixes the *capability*, not the *how*.

## Known unknown

- A teammate (not present in this PRD conversation) reportedly flagged issues in the earlier prototype branch `feat/quote-engine-v1` / `chore/initial-project-foundation`. Specifics are not yet known. The team deliberately chose a greenfield rebuild from `main` rather than branching off that prototype (decision made outside this PRD run), partly to avoid inheriting whatever those issues are — but since the *formula* and general *shape* are still being carried forward in spirit, it would be worth surfacing those specifics before or during architecture, in case they're relevant to the parts being reused.

  **Resolved 2026-08-26:** superseded rather than answered — a different teammate independently produced a real GO tariff (zone/engine-cc based, see the Quote Engine section above), which replaces the placeholder formula this concern was about. The original prototype branch (`feat/quote-engine-v1`) remains unused and is planned for deletion once its author's outstanding work is reconciled separately.
