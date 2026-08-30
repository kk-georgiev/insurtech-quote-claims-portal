# Review — Version & Currency Check

**Target:** `architecture-milestone-2-2026-08-30/ARCHITECTURE-SPINE.md`
**Reviewer date:** 2026-08-30
**Scope:** Independently verify (web search + repo cross-check) every version number and technology claim in the "Stack" table and AD-1/AD-2 sections, and check for known incompatibilities — specifically `@tailwindcss/vite` vs. the project's actual Vite 8.

## Method

- Web-searched npm registry / GitHub for each package's current latest version and release date as of 2026-08-30.
- Cross-checked the spine's implicit dependency (Vite 8) against `frontend/package.json` in the actual repo, which pins `"vite": "^8.2.2"`. The parent architecture spine (`architecture-motor-insurance-quote-claims-portal-2026-08-23/ARCHITECTURE-SPINE.md`, line 141) also states `Vite | 8`, confirming this is a real, committed constraint, not a hypothetical.
- Fetched the `@tailwindcss/vite@4.3.3` npm registry entry directly to read its `peerDependencies` field rather than relying on a single search summary, since initial search results were ambiguous/contradictory (see Findings).

## Findings

### 1. Tailwind CSS v4.3.3 — CONFIRMED, accurate

npm shows `tailwindcss` latest is `4.3.3`, last published roughly a month before the review date (mid-July 2026). No newer version (e.g. 4.3.4 or 4.4.x) exists as of 2026-08-30. The spine's "verified current 2026-08-30" claim holds up.

Sources: https://www.npmjs.com/package/tailwindcss , https://github.com/tailwindlabs/tailwindcss/releases/tag/v4.3.3

### 2. @tailwindcss/vite vs. Vite 8 — CONFIRMED compatible, but the spine underspecifies a real prior risk

This was the one claim worth digging into, because early search results were contradictory: one summary said Vite 8 support "has been added," another said a PR (tailwindlabs/tailwindcss#19790, merged 2026-03-12) added `^8.0.0` to the peer range but "was not in the latest stable tag as of March 13, 2026" (i.e., only in `insiders`, not a real release, for some period).

Direct inspection of the published `@tailwindcss/vite@4.3.3` package.json (via the npm registry API) resolves this: **`peerDependencies.vite` = `"^5.2.0 || ^6 || ^7 || ^8"`**. The Vite 8 support that landed in March 2026 is now in the current stable release (4.3.3), five months later. This matches the project's actual `vite: ^8.2.2` in `frontend/package.json`.

**Verdict: no incompatibility today.** But the spine states "current, paired with Tailwind v4" for `@tailwindcss/vite` without citing a version number or acknowledging that Vite 8 support was, for a period (roughly March–sometime after that), only available via the `insiders` tag rather than a stable release. That's a reasonable simplification for a "final" spine dated after the fix shipped, but the spine gives no evidence it actually checked the peer-dependency range — the version note reads as inferred ("paired with Tailwind v4") rather than verified against Vite 8 specifically, which is the one place this stack genuinely could have broken. Recommend the spine cite the concrete peer range (`^5.2.0 || ^6 || ^7 || ^8`) or at least name a `@tailwindcss/vite` version, the way it does for the other three packages.

Sources: https://github.com/tailwindlabs/tailwindcss/issues/19789 , https://github.com/tailwindlabs/tailwindcss/pull/19790 , npm registry JSON for `@tailwindcss/vite` (peerDependencies field read directly)

### 3. class-variance-authority (cva) 0.7.1 — CONFIRMED, accurate, but flag "current" framing

npm registry confirms `0.7.1` is genuinely the latest dist-tag, published 2024-11-26 — no newer version exists, including no canary/prerelease that superseded it. So "0.7.1" is correct and it's still current in the sense of "nothing newer exists." Worth flagging for the architect only as a heads-up, not a defect: this is a ~2-year-old release with no further development, which is normal for a small, feature-complete utility, but the spine's "[ADOPTED]" framing should note this is a mature/dormant package, not an actively-maintained one, in case that matters for future React 19-related peer-dep churn.

Source: npm registry JSON for `class-variance-authority`

### 4. tailwind-merge 3.6.0 — CONFIRMED, accurate, and compatibility explicitly checked

npm registry confirms `3.6.0` is the latest version. Independent search also surfaced a compatibility note: tailwind-merge's 3.x line "supports Tailwind v4.0 up to v4.3" (users on Tailwind v3 need `tailwind-merge` v2.x instead). Since the spine pins Tailwind at v4.3.3, this is inside the supported range — confirmed compatible, not just independently current.

Source: generalistprogrammer.com tailwind-merge guide (cross-referenced against npm registry version data)

### 5. clsx — spine correctly does NOT pin a version, and that's the right call

Latest `clsx` is `2.1.1`, published 2024-04-23 — over two years stale with no newer release. The spine lists it only as "current, paired with tailwind-merge in the `cn()` helper" without a specific version number, unlike the other three packages. That's actually appropriate here (there's nothing to verify beyond "does it still exist and get used this way" — yes), but note it for consistency: if the architect's intent was "every version number is independently verified," `clsx` is the one library in the table without a citable pinned version, so its "verified" status rests on "still the standard, no successor" rather than a version check. Not a defect, just worth being explicit about in the spine's own language.

### 6. Inter (Google Fonts) — reasonable, not independently falsifiable in the same way

"Current — variable font" is accurate; Inter is still an actively maintained, standard Google Fonts variable family with no indication of deprecation or replacement. There's no meaningful "version number" to verify here the way there is for npm packages, so the spine's looser treatment is appropriate.

## Overall Verdict

**PASS, with one documentation gap.**

All five version-pinned claims (Tailwind CSS 4.3.3, @tailwindcss/vite, cva 0.7.1, tailwind-merge 3.6.0, clsx) check out against live npm registry data as of 2026-08-30, and none conflict with each other or with the project's actual, already-committed dependency on Vite 8 (confirmed against both the parent architecture spine and `frontend/package.json`). The one gap: **AD-1's `@tailwindcss/vite` compatibility claim is asserted ("current, paired with Tailwind v4") rather than evidenced with a peer-dependency range**, and this happens to be the single spot in the stack where a real, time-boxed incompatibility existed earlier in 2026 (Vite 8 support was insiders-only for a period after the 2026-03-12 merge before landing in a stable release). Recommend the spine be updated to cite the concrete peer range `vite: "^5.2.0 || ^6 || ^7 || ^8"` so a future reader doesn't have to re-derive that this was actually checked against Vite 8 specifically, not just "Tailwind v4" generically.
