// The one date-formatting helper this frontend uses (Story 6.3, UX
// EXPERIENCE.md "Bilingual Behaviour": dates render in the active
// language's convention, never one hardcoded format). `Intl.DateTimeFormat`
// needs no catalog entries of its own - the browser already knows how a
// date reads in `bg`/`en`.

/**
 * Formats an ISO date or date-time string (a backend `LocalDate` like
 * `"2026-09-14"`, or an `Instant` like `"2026-09-14T12:00:00Z"`) as a
 * long, human-readable date in `language`'s convention.
 */
export function formatDate(isoDate: string, language: string): string {
  return new Intl.DateTimeFormat(language, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(new Date(isoDate));
}
