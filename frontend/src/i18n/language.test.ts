import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  DEFAULT_LANGUAGE,
  SUPPORTED_LANGUAGES,
  getStoredLanguage,
  isLanguage,
  saveLanguage,
} from './language';

// The key `language.ts` writes to. Duplicated here on purpose: asserting
// against the module's own constant would let a rename silently pass, and
// the key is a persistence contract with browsers that already have a value
// stored under it.
const LANGUAGE_STORAGE_KEY = 'motorinsurance.ui.language';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('isLanguage', () => {
  it.each([...SUPPORTED_LANGUAGES])('accepts the supported language %s', (language) => {
    expect(isLanguage(language)).toBe(true);
  });

  it.each([['de'], [''], ['BG'], ['bg-BG'], [null], [undefined], [42], [{}], [['bg']]])(
    'rejects %o',
    (value) => {
      expect(isLanguage(value)).toBe(false);
    },
  );
});

describe('getStoredLanguage', () => {
  it('returns null when nothing has been stored', () => {
    expect(getStoredLanguage()).toBeNull();
  });

  it('returns the stored language when it is one this build supports', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    expect(getStoredLanguage()).toBe('en');
  });

  // The I/O matrix's "corrupt stored value" row: a language this build does
  // not know (hand-edited storage, or one removed since it was written) is
  // treated as absent, never surfaced as-is to i18next.
  it.each([['de'], [''], ['BG'], ['null']])(
    'treats the unrecognized stored value %o as absent',
    (stored) => {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, stored);
      expect(getStoredLanguage()).toBeNull();
    },
  );

  // The "storage unavailable" row: private browsing or disabled site data
  // makes the accessor itself throw. This runs during the first paint, so an
  // uncaught throw would white-screen the app.
  it('returns null instead of throwing when storage access throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError: storage is disabled');
    });

    expect(() => getStoredLanguage()).not.toThrow();
    expect(getStoredLanguage()).toBeNull();
  });
});

describe('saveLanguage', () => {
  it('persists the choice under the storage key', () => {
    saveLanguage('en');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en');
  });

  it('round-trips through getStoredLanguage', () => {
    saveLanguage('en');
    expect(getStoredLanguage()).toBe('en');
  });

  it('swallows a storage failure rather than breaking the language change', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });

    expect(() => saveLanguage('en')).not.toThrow();
  });
});

describe('defaults', () => {
  it('defaults to Bulgarian — the product\'s market language (FR-14)', () => {
    expect(DEFAULT_LANGUAGE).toBe('bg');
  });

  it('supports exactly Bulgarian and English this milestone', () => {
    expect([...SUPPORTED_LANGUAGES]).toEqual(['bg', 'en']);
  });
});
