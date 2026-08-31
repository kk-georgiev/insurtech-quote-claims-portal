import { describe, expect, it } from 'vitest';

// Proves the real wiring, not just its two halves in isolation:
// `client.test.ts` proves a 401 publishes the session-expiry event;
// `sessionExpiry.test.ts` proves the pub-sub itself calls whatever is
// registered. This test proves the one thing neither of those can: that
// importing the app's actual router module registers a listener which,
// when the event fires, really does navigate the real router to `/login`
// (Story 7.1, FR-M3-12) - exercised against `createBrowserRouter` itself
// rather than the `createMemoryRouter` every component suite uses, since
// `browserRouter.ts` is the one module that instantiates the real one.
//
// Deliberately its own file, one test: `createBrowserRouter` reads and
// writes the real `window.history`, which nothing in this suite resets
// between tests the way `createMemoryRouter`-based suites reset by simply
// mounting a fresh router - a second test here would inherit whatever
// location the first one navigated to.
describe('browserRouter (Story 7.1)', () => {
  it('navigates the real router to /login when a session-expiry notification fires', async () => {
    const { router } = await import('./browserRouter');
    const { notifySessionExpired } = await import('../api/sessionExpiry');

    notifySessionExpired();
    // `router.navigate()` resolves asynchronously even for a loader-less
    // route; give its internal state update a tick to land before asserting.
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(router.state.location.pathname).toBe('/login');
  });
});
