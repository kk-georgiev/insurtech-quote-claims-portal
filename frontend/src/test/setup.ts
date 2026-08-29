// Global test setup (Story 2.2 — first frontend test toolchain).
// - `@testing-library/jest-dom/vitest` registers the DOM matchers
//   (`toBeInTheDocument`, `toHaveTextContent`, …) and augments Vitest's
//   `expect` types.
// - RTL does not auto-cleanup unless Vitest globals are enabled (they are
//   not here — tests import `describe`/`it`/`expect` explicitly), so the
//   render cleanup is wired manually.
// - Story 3.1: the i18n instance is imported for its side effect so every
//   component suite renders against an initialised i18next. It is module-level
//   state shared by the whole run, so unlike `localStorage` it is not cleared
//   by a fresh render — reset it explicitly, or a suite that switches to
//   English leaks that choice into every test that follows it.
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import i18n from '../i18n';
import { DEFAULT_LANGUAGE } from '../i18n/language';

afterEach(async () => {
  cleanup();
  localStorage.clear();
  await i18n.changeLanguage(DEFAULT_LANGUAGE);
  // Reset on its own terms rather than relying on the `languageChanged`
  // listener in `i18n/index.ts` to have done it as a side effect - a test
  // that sets `lang` without going through i18next would otherwise leak it.
  document.documentElement.lang = DEFAULT_LANGUAGE;
});
