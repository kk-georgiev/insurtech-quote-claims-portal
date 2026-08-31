// A minimal pub-sub for exactly one cross-cutting event (Story 7.1,
// FR-M3-12): an authenticated call came back 401, meaning the session the
// client thought it had is no longer valid server-side. `client.ts`
// publishes; `app/browserRouter.ts` subscribes once at app startup to
// actually navigate to `/login`.
//
// Kept as its own module, in `api/` rather than `app/`, specifically so
// `client.ts` never has to import react-router or the route table to
// react to this - that would invert this frontend's layering (`app`
// depends on `api`, never the reverse, same direction AD-10 already
// assumes) and, worse, create a real import cycle: `router.tsx` renders
// screens that themselves call `apiFetch` from `client.ts`. Routing
// components subscribe to this instead of `client.ts` reaching up to them.

type SessionExpiredListener = () => void;

let listener: SessionExpiredListener | null = null;

/**
 * Registers the handler that reacts to a session dying. Call once, at app
 * startup (`app/browserRouter.ts`) — a second call replaces the first
 * rather than stacking listeners, since exactly one "go to /login" handler
 * ever needs to exist for the one real router instance.
 */
export function onSessionExpired(handler: SessionExpiredListener): void {
  listener = handler;
}

/**
 * Publishes the event. A no-op if nothing has subscribed yet — e.g. a unit
 * test that exercises `client.ts` directly without importing `App`/
 * `browserRouter.ts`, which is deliberately allowed rather than required.
 */
export function notifySessionExpired(): void {
  listener?.();
}
