// The one live browser-history router instance. Split out from `App.tsx`
// (Story 7.1) so `api/client.ts`'s session-expiry notification has
// something concrete to subscribe to without `App.tsx` itself needing to
// know about that wiring - `App.tsx` stays a plain render of this router.
//
// `router.tsx` stays the side-effect-free route table it always was; tests
// mount that table under their own `createMemoryRouter` instance and never
// import this file, so this module's `onSessionExpired` registration never
// runs in a component test - those tests that care about the redirect wire
// their own listener against their own memory router (see client.test.ts /
// browserRouter.test.ts for the two halves of that behaviour).
import { createBrowserRouter } from 'react-router';
import { routes } from './router';
import { onSessionExpired } from '../api/sessionExpiry';

export const router = createBrowserRouter(routes);

// Story 7.1 (FR-M3-12): the one place that reacts to a session dying by
// actually navigating - registered once, at startup, not per screen.
onSessionExpired(() => {
  router.navigate('/login', { replace: true });
});
