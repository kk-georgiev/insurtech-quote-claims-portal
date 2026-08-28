import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { routes } from '../../app/router';
import { ROLES, roleHome, type Role } from '../../app/roleHome';
import { getToken } from '../../api/authToken';

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
type StaffRole = Exclude<Role, 'CLIENT'>;

const STAFF_ROLES = ROLES.filter((role): role is StaffRole => role !== 'CLIENT');

// Stated verbatim, mirroring the spec's Design Notes copy table — not
// derived from the role name. Deriving it would let a wrong-but-consistent
// rendering satisfy a wrong-but-consistent expectation.
const COPY: Record<StaffRole, { heading: string; line: string }> = {
  AGENT: {
    heading: 'Agent workspace',
    line: 'Coming soon — Agent tools are not part of this milestone.',
  },
  LIQUIDATOR: {
    heading: 'Liquidator workspace',
    line: 'Coming soon — Liquidator tools are not part of this milestone.',
  },
  ADMINISTRATOR: {
    heading: 'Administrator workspace',
    line: 'Coming soon — Administrator tools are not part of this milestone.',
  },
};

// Anything a user could click, focus, or type into. Broader than
// `getAllByRole('button')` so an anchor, a focusable `tabindex`, or a custom
// `role="button"` sneaking into a shell also trips the test. `tabindex="-1"`
// is excluded deliberately: it is a programmatic focus target, not something
// a user can reach.
const INTERACTIVE_SELECTOR =
  'a[href], button, input, select, textarea, [role="button"], [role="link"], [tabindex]:not([tabindex="-1"])';

async function renderShellAt(role: StaffRole) {
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
    // Iterates the full `ROLES`, not just the staff ones: "Client" appearing
    // on a staff screen is the same defect as "Agent" appearing on the
    // Liquidator screen. Word-boundary matched so a future overlapping name
    // (ADMIN vs ADMINISTRATOR) neither false-passes nor false-fails.
    for (const other of ROLES) {
      if (other === role) continue;
      expect(text).not.toMatch(new RegExp(`\\b${other}\\b`, 'i'));
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

  it.each(STAFF_ROLES)('%s: renders for an unauthenticated direct visit, with no redirect', async (role) => {
    // Story 2.4 adds the route guard. Until then a logged-out visitor typing
    // the URL sees the screen and stays on it — this pins that behaviour so
    // the guard story has to flip it deliberately.
    expect(getToken()).toBeNull();

    const { router } = await renderShellAt(role);

    expect(router.state.location.pathname).toBe(roleHome(role));
  });
});
