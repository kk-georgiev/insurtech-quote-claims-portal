// react-i18next setup (AD-8: i18n is 100% frontend-owned; the backend never
// emits localized prose). Side-effect module - importing it initialises the
// one i18next instance the whole app renders against. `main.tsx` imports it
// before the first render; `src/test/setup.ts` imports it for every suite.
//
// Catalogs are bundled JSON, not fetched, so initialisation is synchronous
// and the first paint is already in the right language - no loading state, no
// flash of untranslated keys, and no `Suspense` boundary needed.
//
// Story 3.1 opened the `app.*` namespace (the RootLayout-owned chrome);
// Story 3.2a added `auth.*`, `quote.*`, `shells.*`, and `app.health.*` for
// the screen copy. Story 3.2b adds the backend error-`code` entries.

import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import bg from './bg.json';
import en from './en.json';
import { DEFAULT_LANGUAGE, getStoredLanguage } from './language';

void i18n.use(initReactI18next).init({
  resources: {
    bg: { translation: bg },
    en: { translation: en },
  },
  // The visitor's stored choice wins; a first-time visitor gets Bulgarian.
  lng: getStoredLanguage() ?? DEFAULT_LANGUAGE,
  // Bulgarian, not English: an English fallback artifact must never be what
  // a Bulgarian visitor sees (FR-14).
  fallbackLng: DEFAULT_LANGUAGE,
  interpolation: {
    // React escapes interpolated values already; letting i18next escape too
    // would double-encode anything with an ampersand or quote in it.
    escapeValue: false,
  },
});

/**
 * Keeps the two pieces of document-level state that belong to the *language*
 * rather than to any component in step with it:
 *
 * - `<html lang>`, for screen readers, browser translation prompts, and
 *   correct hyphenation.
 * - `document.title`, which is the browser tab, the bookmark name, and the
 *   history entry. `index.html` ships the Bulgarian default so the tab reads
 *   correctly before React mounts; from then on it follows the selection.
 *
 * Deliberately lives here rather than in `LanguageToggle`: both are
 * properties of the i18next instance's state, not of any one control that
 * happens to change it, so they stay correct no matter what triggers the
 * change - and `document.title` has no owning component at all.
 *
 * The listener is never removed. In the app that is correct - this module is
 * evaluated once, and a Vite HMR edit to it triggers a full page reload
 * rather than a re-execution (nothing in its import chain calls
 * `import.meta.hot.accept`). Only `LanguageToggle.test.tsx`'s reload case
 * re-runs this module against the same i18next singleton, stacking one
 * duplicate listener; both syncs are idempotent, so that is harmless.
 */
function syncDocument(language: string): void {
  document.documentElement.lang = language;
  document.title = i18n.t('app.title');
}

syncDocument(i18n.resolvedLanguage ?? DEFAULT_LANGUAGE);
i18n.on('languageChanged', syncDocument);

export default i18n;
