// The frontend's single source of truth for "which display languages exist"
// and "where the visitor's choice is stored" (Story 3.1). Deliberately mirrors
// the closed-set shape `app/roleHome.ts` uses for roles - a `const` tuple, a
// union type derived from it, and one runtime guard - so an unrecognized value
// has exactly one meaning everywhere it can turn up.
//
// Persistence is client-side only (AD-8): there is no server-side or
// per-account language preference this milestone, and no API call changes
// because of the selection.

const LANGUAGE_STORAGE_KEY = 'motorinsurance.ui.language';

export const SUPPORTED_LANGUAGES = ['bg', 'en'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

/**
 * Bulgarian is the product's real market language, so it is both the
 * no-preference default and i18next's `fallbackLng` (FR-14) - a Bulgarian
 * visitor must never be shown an English fallback string.
 */
export const DEFAULT_LANGUAGE: Language = 'bg';

/**
 * Runtime type guard for a value read back out of storage, whose type is
 * `string | null` - a read is not a validation.
 */
export function isLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}

/**
 * The stored choice, or `null` when there is nothing usable to restore:
 * never set, cleared, or a value this build does not recognize (a language
 * removed since it was written, or storage tampered with by hand).
 *
 * Unlike `api/authToken.ts`'s `getToken`, both this and {@link saveLanguage}
 * guard against `localStorage` itself throwing. That module is only reached
 * after a successful login; this one runs during the first paint, where an
 * uncaught throw (Safari private browsing, site data disabled) would white-
 * screen the app instead of quietly degrading to Bulgarian.
 */
export function getStoredLanguage(): Language | null {
  let stored: string | null;
  try {
    stored = localStorage.getItem(LANGUAGE_STORAGE_KEY);
  } catch {
    return null;
  }

  return isLanguage(stored) ? stored : null;
}

/**
 * Persists the choice for the next page load. A storage failure is silent by
 * design: the language has already changed for this session, and only its
 * persistence is lost - not a failure worth interrupting the user for.
 */
export function saveLanguage(language: Language): void {
  try {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  } catch {
    // Storage unavailable - see the doc comment above.
  }
}
