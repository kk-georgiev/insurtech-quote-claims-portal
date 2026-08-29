import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../app/router';
import { ROLES, STAFF_ROLES, roleHome, type Role, type StaffRole } from '../../app/roleHome';
import { getToken } from '../../api/authToken';
import { seedToken } from '../../test/seedToken';
import bg from '../../i18n/bg.json';

// Story 2.3: the three staff placeholder screens. This suite is driven by a
// table rather than three hand-written cases so the cross-contamination
// matrix is symmetric — every staff screen is checked against *every* other
// role's name, and adding a role extends the matrix for free.
//
// CLIENT is not a *subject* here: `ClientShell` is out of scope for this
// story. It is not a placeholder at all — Story 1.7 gave it the real quote
// flow — so it has no "<Role> workspace" heading or coming-soon line to
// assert. It is still one of the names a staff screen must not display —
// see the contamination test, which iterates the full `ROLES`.
//
// `StaffRole`/`STAFF_ROLES` are Story 2.4's single source of truth for
// "which roles are staff" (`roleHome.ts`) — this suite no longer re-derives
// them locally.

// Stated verbatim — NOT read from the i18n catalog, and not derived from the
// role name. Reading it from `bg.json` would compare the component's own
// source of truth against itself, letting a wrong translation satisfy a
// wrong-but-consistent expectation. That is the reasoning Story 2.3 wrote
// here for the English copy; Story 3.2a changes only the language.
const COPY: Record<StaffRole, { heading: string; line: string }> = {
  AGENT: {
    heading: 'Работно място на агента',
    line: 'Очаквайте скоро — инструментите за агенти не са част от този етап.',
  },
  LIQUIDATOR: {
    heading: 'Работно място на ликвидатора',
    line: 'Очаквайте скоро — инструментите за ликвидатори не са част от този етап.',
  },
  ADMINISTRATOR: {
    heading: 'Работно място на администратора',
    line: 'Очаквайте скоро — инструментите за администратори не са част от този етап.',
  },
};

// The cross-contamination check matches role *names* against rendered text.
// The English names are no longer rendered anywhere, so the previous
// word-boundary regex on `AGENT` would pass vacuously on every screen.
// These are the Bulgarian stems the copy actually uses, deliberately
// un-inflected so they match every case form ("агента", "агенти").
// None is a substring of another.
const ROLE_STEM: Record<Role, string> = {
  CLIENT: 'клиент',
  AGENT: 'агент',
  LIQUIDATOR: 'ликвидатор',
  ADMINISTRATOR: 'администратор',
};

// Anything a user could click, focus, or type into. Broader than
// `getAllByRole('button')` so an anchor, a focusable `tabindex`, or a custom
// `role="button"` sneaking into a shell also trips the test. `tabindex="-1"`
// is excluded deliberately: it is a programmatic focus target, not something
// a user can reach.
const INTERACTIVE_SELECTOR =
  'a[href], button, input, select, textarea, [role="button"], [role="link"], [tabindex]:not([tabindex="-1"])';

// Story 2.4 gates every staff shell behind `RoleGuard`, so exercising these
// screens now requires a matching token first. Uses the shared `seedToken`
// helper (`frontend/src/test/seedToken.ts`) — the same fake-unsigned-JWT
// technique as `router.test.tsx`, since `decodeToken` never verifies the
// signature. `StaffRole` is a subtype of `Role`, so it's accepted directly.
async function renderShellAt(role: StaffRole) {
  seedToken(role);
  const router = createMemoryRouter(routes, { initialEntries: [roleHome(role)] });
  render(<RouterProvider router={router} />);
  const shell = await screen.findByTestId(`${role.toLowerCase()}-shell`);
  return { router, shell };
}

describe('staff placeholder screens', () => {
  it.each(STAFF_ROLES)(
    '%s: renders its own workspace heading and coming-soon line',
    async (role) => {
      const { shell } = await renderShellAt(role);

      // The heading names the *area*, not the viewer — the screen is
      // reachable while logged out (no guard until Story 2.4), so it must
      // not claim an auth state the app has not verified.
      expect(
        await screen.findByRole('heading', { name: COPY[role].heading, level: 2 }),
      ).toBeInTheDocument();
      expect(shell).toHaveTextContent(COPY[role].line);
      // `aria-labelledby` names the `<section>`, which is what promotes it
      // to a `region` landmark assistive tech can navigate to and announce.
      expect(shell).toHaveAccessibleName(COPY[role].heading);
    },
  );

  it.each(STAFF_ROLES)("%s: shows no other role's name anywhere on the screen", async (role) => {
    const { shell } = await renderShellAt(role);
    const text = shell.textContent ?? '';

    expect(text).toContain(COPY[role].heading);
    // Iterates the full `ROLES`, not just the staff ones: "клиент" appearing
    // on a staff screen is the same defect as "агент" appearing on the
    // Liquidator screen. Matched on the un-inflected stem rather than a
    // word-boundary regex, because Bulgarian inflects these names and
    // word-boundary anchoring would sail straight past "агента".
    for (const other of ROLES) {
      if (other === role) continue;
      expect(text.toLowerCase()).not.toContain(ROLE_STEM[other]);
    }
  });

  it.each(STAFF_ROLES)('%s: contains no interactive control', async (role) => {
    const { shell } = await renderShellAt(role);
    expect(shell.querySelectorAll(INTERACTIVE_SELECTOR)).toHaveLength(0);
  });

  it.each(STAFF_ROLES)('%s: does not react to clicks anywhere on the screen', async (role) => {
    // The selector test above cannot see React handlers: React attaches
    // listeners at the root container, so `<p onClick={…}>` leaves no
    // `onclick` attribute and no `tabindex` in the DOM and the selector
    // still matches nothing. Since "static and non-interactive" is this
    // story's headline contract, drive it from the outside instead: click
    // the screen and assert the rendered DOM is byte-identical afterwards.
    // A static screen passes trivially; any handler that mutates state
    // trips it.
    const user = userEvent.setup();
    const { shell } = await renderShellAt(role);
    const before = shell.innerHTML;

    await user.click(shell);
    await user.click(within(shell).getByRole('heading', { level: 2 }));
    const paragraph = shell.querySelector('p');
    expect(paragraph).not.toBeNull();
    await user.click(paragraph as HTMLElement);

    expect(shell.innerHTML).toBe(before);
  });

  it.each(STAFF_ROLES)('%s: redirects an unauthenticated direct visit to /login', async (role) => {
    // Story 2.4's route guard: a logged-out visitor typing the URL directly
    // never sees the screen — they land on `/login` instead. This flips the
    // "no redirect" behaviour Story 2.3 pinned before the guard existed.
    expect(getToken()).toBeNull();

    const router = createMemoryRouter(routes, { initialEntries: [roleHome(role)] });
    render(<RouterProvider router={router} />);

    expect(await screen.findByRole('heading', { name: bg.auth.login.heading })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });
});
