import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from './router';
import { seedToken } from '../test/seedToken';
import { getStoredLanguage } from '../i18n/language';
import { apiFetch } from '../api/client';
import bg from '../i18n/bg.json';
import en from '../i18n/en.json';

// `/health` mounts `HealthStatus`, whose effect calls `apiFetch` on load.
// Mocked for the same reason `router.test.tsx` mocks it: this suite renders
// the real route table and must never touch the network.
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

// `mockReset: true` clears the implementation before every test, so the
// default has to be re-established here rather than once at module scope.
// Without it `HealthStatus` resolves `undefined` and trips its error
// boundary — this suite is about the toggle, not about health-check states.
beforeEach(() => {
  mockedApiFetch.mockResolvedValue({ status: 'UP' } as { status: string });
});

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

/** The toggle button for one language, found by its own-language label. */
function languageButton(language: 'bg' | 'en') {
  return screen.getByRole('button', { name: bg.app.language[language] });
}

describe('LanguageToggle', () => {
  it('renders Bulgarian by default for a first-time visitor, with <html lang="bg">', async () => {
    renderAt('/login');

    expect(await screen.findByRole('heading', { name: bg.app.title })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: bg.app.nav.register })).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('bg');
  });

  it('switches the chrome to English immediately and updates <html lang>', async () => {
    const user = userEvent.setup();
    renderAt('/login');

    await user.click(languageButton('en'));

    expect(await screen.findByRole('heading', { name: en.app.title })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: en.app.nav.register })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: en.app.nav.login })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: en.app.nav.health })).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('en');
  });

  it('switches back to Bulgarian', async () => {
    const user = userEvent.setup();
    renderAt('/login');

    await user.click(languageButton('en'));
    await screen.findByRole('heading', { name: en.app.title });
    await user.click(languageButton('bg'));

    expect(await screen.findByRole('heading', { name: bg.app.title })).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('bg');
  });

  // The heart of the AC: "switches immediately without losing my place".
  it('keeps the route and in-progress form state across a switch', async () => {
    const user = userEvent.setup();
    const router = renderAt('/login');

    await user.type(screen.getByLabelText('Email'), 'someone@example.com');
    await user.click(languageButton('en'));

    await screen.findByRole('heading', { name: en.app.title });
    // Same route - no reload, no navigation.
    expect(router.state.location.pathname).toBe('/login');
    // Untranslated feature screen still mounted, with what was typed into it.
    expect(screen.getByLabelText('Email')).toHaveValue('someone@example.com');
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument();
  });

  it('persists the selection so a reload can restore it', async () => {
    const user = userEvent.setup();
    renderAt('/login');

    expect(getStoredLanguage()).toBeNull();
    await user.click(languageButton('en'));

    expect(getStoredLanguage()).toBe('en');
  });

  // Re-runs `i18n/index.ts`'s initialisation against the same i18next
  // singleton (`vi.resetModules` clears Vitest's own module cache but cannot
  // re-instantiate an externalized dependency), so what this actually proves
  // is that init reads the stored choice: an init that ignored storage would
  // leave `resolvedLanguage` at the "bg" the global `afterEach` resets it to.
  // Asserting only that `localStorage` holds "en" would not catch that.
  //
  // Deliberately does not assert on `<html lang>` here: `init()` emits
  // `languageChanged` on the shared instance, so the *first* module's
  // listener would satisfy that assertion even if this module never synced.
  // The real coverage for that sync lives in the first test in this file,
  // where `<html lang>` is still jsdom's default.
  it('restores the stored language on first paint after a reload', async () => {
    localStorage.setItem('motorinsurance.ui.language', 'en');

    vi.resetModules();
    const freshI18n = (await import('../i18n')).default;

    expect(freshI18n.resolvedLanguage).toBe('en');
  });

  it('marks the active language as pressed for assistive technology', async () => {
    const user = userEvent.setup();
    renderAt('/login');

    expect(languageButton('bg')).toHaveAttribute('aria-pressed', 'true');
    expect(languageButton('en')).toHaveAttribute('aria-pressed', 'false');

    await user.click(languageButton('en'));

    expect(languageButton('en')).toHaveAttribute('aria-pressed', 'true');
    expect(languageButton('bg')).toHaveAttribute('aria-pressed', 'false');
  });

  // FR-14: switchable "at any time" — authenticated or not. The toggle lives
  // in RootLayout, above every route, so it must survive a RoleGuard redirect
  // as well as sit on the public routes.
  it.each([['/login'], ['/register'], ['/health']])(
    'is available on the public route %s',
    async (path) => {
      renderAt(path);
      expect(await screen.findByTestId('language-toggle')).toBeInTheDocument();
    },
  );

  it.each([
    ['CLIENT', '/'],
    ['AGENT', '/agent'],
    ['LIQUIDATOR', '/liquidator'],
    ['ADMINISTRATOR', '/administrator'],
  ] as const)('is available to a logged-in %s on their guarded route %s', async (role, path) => {
    seedToken(role);
    renderAt(path);

    expect(await screen.findByTestId(`${role.toLowerCase()}-shell`)).toBeInTheDocument();
    expect(screen.getByTestId('language-toggle')).toBeInTheDocument();
  });

  // Presence is not enough: the AC says "present *and* operable" everywhere.
  // The other operability cases run on `/login`; this one proves it inside a
  // guarded shell, where a redirect could plausibly interfere.
  it('is operable, not just present, on a guarded route', async () => {
    const user = userEvent.setup();
    seedToken('AGENT');
    const router = renderAt('/agent');

    await screen.findByTestId('agent-shell');
    await user.click(languageButton('en'));

    expect(await screen.findByRole('heading', { name: en.app.title })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/agent');
    expect(screen.getByTestId('agent-shell')).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('en');
  });

  it('is still available after a RoleGuard bounces an anonymous visitor to /login', async () => {
    const router = renderAt('/agent');

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.getByTestId('language-toggle')).toBeInTheDocument();
  });
});

describe('translation catalogs', () => {
  // AD-8's contract: a key added to one catalog is added to the other in the
  // same change. Story 3.2 adds many more; this keeps them honest.
  it('define identical key sets in Bulgarian and English', () => {
    const keys = (value: unknown, prefix = ''): string[] =>
      typeof value === 'object' && value !== null
        ? Object.entries(value).flatMap(([key, child]) =>
            keys(child, prefix ? `${prefix}.${key}` : key),
          )
        : [prefix];

    expect(keys(bg).sort()).toEqual(keys(en).sort());
  });

  it('leaves no Bulgarian value empty or accidentally left in English', () => {
    const translated = [
      [bg.app.title, en.app.title],
      [bg.app.nav.register, en.app.nav.register],
      [bg.app.nav.login, en.app.nav.login],
      [bg.app.nav.health, en.app.nav.health],
      [bg.app.language.label, en.app.language.label],
    ];

    for (const [bulgarian, english] of translated) {
      // The "empty" half of this test's name — an unset value would sail
      // past the inequality check below.
      expect(bulgarian.trim()).not.toBe('');
      expect(bulgarian).not.toBe(english);
    }

    // The two language *option* labels are the deliberate exception: each is
    // named in its own language, so they are identical across catalogs.
    expect(bg.app.language.bg).toBe(en.app.language.bg);
    expect(bg.app.language.en).toBe(en.app.language.en);
  });

  // FR-14: "no English-fallback artifact" — the fallback direction must
  // resolve to Bulgarian. A key missing from `en` falls back to the
  // Bulgarian string, never the raw key and never an English default.
  it('falls back to Bulgarian, not English, for a key missing from a catalog', async () => {
    const i18n = (await import('../i18n')).default;
    i18n.addResource('bg', 'translation', 'app.fallbackProbe', 'само на български');

    await i18n.changeLanguage('en');

    expect(i18n.t('app.fallbackProbe')).toBe('само на български');
  });
});
