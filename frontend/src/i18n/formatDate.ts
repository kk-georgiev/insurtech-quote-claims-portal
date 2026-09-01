// The one date-formatting helper this frontend uses (Story 6.3, UX
// EXPERIENCE.md "Bilingual Behaviour": dates render in the active
// language's convention, never one hardcoded format). `Intl.DateTimeFormat`
// needs no catalog entries of its own - the browser already knows how a
// date reads in `bg`/`en`.

/**
 * Formats an ISO date or date-time string (a backend `LocalDate` like
 * `"2026-09-14"`, or an `Instant` like `"2026-09-14T12:00:00Z"`) as a
 * long, human-readable date in `language`'s convention.
 *
 * Pinned to UTC (Epic 6 retro item 46): a bare `LocalDate` string parses as
 * UTC midnight, and `Intl.DateTimeFormat` without an explicit `timeZone`
 * renders in the viewer's local zone - shifting the displayed date by a day
 * for anyone at a negative UTC offset. The backend's own dates are already
 * resolved in the business zone (`Europe/Sofia`) before they're ever
 * serialized, so this formatter's job is only to render the string it was
 * given, not to re-interpret it in the viewer's zone.
 */
export function formatDate(isoDate: string, language: string): string {
  return new Intl.DateTimeFormat(language, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(isoDate));
}
