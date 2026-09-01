import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from './router';
import { QuoteResult } from '../features/quote/QuoteResult';
import type { QuoteResponse } from '../features/quote/QuoteForm';
import { seedToken } from '../test/seedToken';
import { getStoredLanguage } from '../i18n/language';
import type { Role } from './roleHome';
import { apiFetch } from '../api/client';
import bg from '../i18n/bg.json';
import en from '../i18n/en.json';

const SAMPLE_QUOTE_FOR_SWEEP: QuoteResponse = {
  id: '11111111-1111-1111-1111-111111111111',
  createdAt: '2026-08-28T12:00:00Z',
  driverAge: 30,
  regionCode: 'KH',
  engineCc: 1500,
  zoneId: 1,
  zoneName: 'Zone 1',
  basePremium: 141.12,
  ageSurcharge: 0,
  bonusMalusClass: 'NEUTRAL',
  bonusMalusFactor: 1,
  oneTimePremium: 141.12,
  installments: 2,
  installmentFee: 2,
  totalPremium: 143.12,
  installmentAmount: 71.56,
  currency: 'EUR',
  validUntil: '2026-12-31',
  status: 'CALCULATED',
  acceptedAt: null,
  policyId: null,
};

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
// Path-aware (Story 6.3): `/quotes` and `/quotes/:id` also call `apiFetch`,
// distinct from `/health`'s `{status}` shape. The list resolves empty so
// the "no English copy" sweep below exercises the *empty* state's own
// strings, which is the only `/quotes` state this file needs.
beforeEach(() => {
  mockedApiFetch.mockImplementation((path: unknown) => {
    if (path === '/api/v1/quotes') return Promise.resolve([]);
    if (typeof path === 'string' && path.startsWith('/api/v1/quotes/')) {
      return Promise.resolve(SAMPLE_QUOTE_FOR_SWEEP);
    }
    return Promise.resolve({ status: 'UP' });
  });
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
    expect(document.title).toBe(bg.app.title);
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
    // The browser tab follows too - it has no owning component, so the sync
    // lives on the i18next instance alongside the `<html lang>` one.
    expect(document.title).toBe(en.app.title);
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

    await user.type(screen.getByLabelText(bg.auth.login.email), 'someone@example.com');
    await user.click(languageButton('en'));

    await screen.findByRole('heading', { name: en.app.title });
    // Same route - no reload, no navigation.
    expect(router.state.location.pathname).toBe('/login');
    // The field is queried by its *English* label now, and still holds what
    // was typed under the Bulgarian one: same DOM node, re-labelled in place.
    // Since Story 3.2a the form itself is translated too, so this doubles as
    // proof that the whole tree re-renders, not just the header.
    expect(screen.getByLabelText(en.auth.login.email)).toHaveValue('someone@example.com');
    expect(screen.getByRole('heading', { name: en.auth.login.heading })).toBeInTheDocument();
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
    // Shows the tab title follows the *restored* language. Like the
    // `<html lang>` assertion this test deliberately omits, it is satisfied
    // by whichever module's listener fires, so it evidences the restore, not
    // the fresh module's own registration.
    expect(document.title).toBe(en.app.title);
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

    expect(await screen.findByRole('heading', { name: bg.auth.login.heading })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.getByTestId('language-toggle')).toBeInTheDocument();
  });
});

// Story 3.2a's headline AC: with Bulgarian active, no English static copy
// survives on any screen. Rendering each route and scanning for the exact
// English strings catches a component that was missed entirely, which a
// per-key assertion elsewhere would not.
describe('Bulgarian pass — no English static copy left on any screen', () => {
  // Each row carries a `mountedTestId` and awaits *that*, never the language
  // toggle. The toggle lives in RootLayout above `<Outlet />`, so it renders
  // on every route including the `/login` a RoleGuard bounce lands on —
  // awaiting it would let all four guarded rows pass green while scanning the
  // login screen instead of the shell they name. Same vacuous-pass shape as
  // the role-name bug this story fixed in `shells.test.tsx`.
  // `mountedHeading` is the Bulgarian `<h2>` unique to the screen under test.
  // Awaiting it proves the intended screen actually rendered; a guard bounce
  // to /login would fail the await instead of quietly scanning the wrong page.
  const SCREENS: Array<[string, Role | null, string, string[]]> = [
    ['/login', null, bg.auth.login.heading,
      [en.auth.login.heading, en.auth.login.email, en.auth.login.password, en.auth.login.submit]],
    ['/register', null, bg.auth.register.heading,
      [en.auth.register.heading, en.auth.register.email, en.auth.register.password,
       en.auth.register.submit]],
    ['/health', null, bg.app.health.heading, [en.app.health.heading, en.app.health.reachable]],
    ['/', 'CLIENT', bg.shells.client.heading,
      [en.shells.client.heading, en.quote.form.heading, en.quote.form.driverAge,
       en.quote.form.regionCode, en.quote.form.engineCc, en.quote.form.installments,
       en.quote.form.submit]],
    ['/agent', 'AGENT', bg.shells.agent.heading,
      [en.shells.agent.heading, en.shells.agent.comingSoon]],
    ['/liquidator', 'LIQUIDATOR', bg.shells.liquidator.heading,
      [en.shells.liquidator.heading, en.shells.liquidator.comingSoon]],
    ['/administrator', 'ADMINISTRATOR', bg.shells.administrator.heading,
      [en.shells.administrator.heading, en.shells.administrator.comingSoon]],
    ['/quotes', 'CLIENT', bg.quotes.list.heading,
      [en.quotes.list.heading, en.quotes.list.empty.title, en.quotes.list.empty.cta]],
    ['/quotes/11111111-1111-1111-1111-111111111111', 'CLIENT', bg.quotes.detail.heading,
      [en.quotes.detail.heading, en.quote.result.heading, en.quote.result.totalPremium]],
  ];

  it.each(SCREENS)(
    '%s shows no English copy',
    async (path, role, mountedHeading, englishStrings) => {
      if (role) seedToken(role);
      renderAt(path);
      expect(await screen.findByRole('heading', { name: mountedHeading })).toBeInTheDocument();

      const text = document.body.textContent ?? '';
      for (const english of englishStrings) {
        expect(text, `"${english}" is still rendered on ${path}`).not.toContain(english);
      }
    },
  );

  // `QuoteResult` only mounts after a successful submit, so the route-level
  // scan above never reaches it — yet "quote form *and breakdown*" is in the
  // AC verbatim. It is a pure presentational component, so render it directly
  // rather than driving a whole submit just to see its labels.
  it('the quote breakdown shows no English copy', () => {
    render(
      <QuoteResult
        quote={{
          zoneId: 3, basePremium: 100, ageSurcharge: 10,
          bonusMalusClass: 'NEUTRAL', bonusMalusFactor: 1,
          oneTimePremium: 110, installments: 2, installmentFee: 5, totalPremium: 115,
          installmentAmount: 57.5, currency: 'BGN',
        }}
      />,
    );

    const region = screen.getByTestId('quote-result');
    const text = region.textContent ?? '';
    for (const english of Object.values(en.quote.result)) {
      // `zoneName` ("Zone 3") is backend data still rendered raw — Story 3.2b
      // replaces it with a zoneId-keyed label. Skip the one key that collides.
      if (english === en.quote.result.zone) continue;
      expect(text, `"${english}" is still rendered in the breakdown`).not.toContain(english);
    }
    expect(region).toHaveAccessibleName(bg.quote.result.label);
    expect(screen.getByRole('heading', { name: bg.quote.result.heading })).toBeInTheDocument();
  });

  // Behind `phase === 'success'`, so the route-level scan never reaches it.
  it('the registration success screen shows no English copy', async () => {
    const user = userEvent.setup();
    renderAt('/register');

    await user.type(screen.getByLabelText(bg.auth.register.email), 'a@example.com');
    await user.type(screen.getByLabelText(bg.auth.register.password), 'Password123');
    await user.click(screen.getByRole('button', { name: bg.auth.register.submit }));

    expect(await screen.findByTestId('register-success')).toBeInTheDocument();
    const text = document.body.textContent ?? '';
    expect(text).not.toContain(en.auth.register.success);
    expect(text).not.toContain(en.auth.register.successBody);
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

  // The key-set check above doesn't catch a mistyped or renamed {{placeholder}}
  // within an otherwise-matching key - a translator fixing bg.json's wording
  // could rename {{zoneId}} to {{zoneID}} and both prior tests would still
  // pass, then i18next would render the literal "{{zoneID}}" to a real user
  // (Epic 3 retro action item, adversarial finding).
  it('uses the same interpolation placeholders in both catalogs for every key', () => {
    const placeholders = (value: string): string[] =>
      [...value.matchAll(/\{\{(\w+)\}\}/g)].map((match) => match[1]).sort();

    const leafEntries = (value: unknown, prefix = ''): Array<[string, string]> =>
      typeof value === 'object' && value !== null
        ? Object.entries(value).flatMap(([key, child]) =>
            leafEntries(child, prefix ? `${prefix}.${key}` : key),
          )
        : [[prefix, String(value)]];

    const englishPlaceholders = new Map(leafEntries(en).map(([key, value]) => [key, placeholders(value)]));

    for (const [key, bulgarian] of leafEntries(bg)) {
      expect(placeholders(bulgarian), `${key} placeholder mismatch`).toEqual(englishPlaceholders.get(key));
    }
  });

  // The two language *option* labels are deliberately identical across
  // catalogs: each option is named in its own language so a visitor who
  // cannot read the current one can still find theirs.
  const IDENTICAL_BY_DESIGN = ['app.language.bg', 'app.language.en'];

  // Walks every leaf rather than a hand-written list. Story 3.2a took the
  // catalogs from 7 keys to ~50; an enumerated list would have silently
  // stopped covering the new ones.
  it('leaves no Bulgarian value empty or accidentally left in English', () => {
    const leaves = (value: unknown, prefix = ''): Array<[string, string]> =>
      typeof value === 'object' && value !== null
        ? Object.entries(value).flatMap(([key, child]) =>
            leaves(child, prefix ? `${prefix}.${key}` : key),
          )
        : [[prefix, String(value)]];

    const english = new Map(leaves(en));

    for (const [key, bulgarian] of leaves(bg)) {
      expect(bulgarian.trim(), `${key} is empty`).not.toBe('');
      if (IDENTICAL_BY_DESIGN.includes(key)) {
        expect(bulgarian, `${key} should match en`).toBe(english.get(key));
      } else {
        expect(bulgarian, `${key} is still the English string`).not.toBe(english.get(key));
      }
    }
  });

  // Guards the exception list itself: if someone deletes `app.language.bg`,
  // the loop above would still pass while the allowlist quietly rots.
  it('keeps every identical-by-design key present in both catalogs', () => {
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
