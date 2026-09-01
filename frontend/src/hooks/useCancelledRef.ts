import { useEffect, useRef, type RefObject } from 'react';

/**
 * The unmount guard every async screen in this app needs: a ref that reads
 * `true` once the component has unmounted, so a request resolving after the
 * user navigated away does not call `setState` on a dead component.
 *
 * <p>Extracted in Story 8.2 from the four hand-rolled copies that had
 * accumulated in `LoginForm`, `RegisterForm`, `QuoteForm`, `MyQuotes` and
 * `QuoteDetail` (Epic 5 retro item 41; Epic 6 retro item 44 asked
 * explicitly whether the load screens were in scope — they are, and this is
 * the piece they share).
 *
 * The mount effect resets the ref to `false` rather than relying on the
 * initial value: StrictMode's development double-invoke runs the cleanup
 * after the first mount, which would otherwise leave a stale `true` behind
 * and silently swallow every state update for the rest of the component's
 * life.
 *
 * Load screens use this directly. Submitting forms get it via
 * {@link useFormSubmission}, which needs the same guard plus a phase
 * machine — the guard alone is deliberately not given one, because a list
 * that fetches has no submit to protect against.
 */
export function useCancelledRef(): RefObject<boolean> {
  const cancelledRef = useRef(false);

  useEffect(() => {
    cancelledRef.current = false;
    return () => {
      cancelledRef.current = true;
    };
  }, []);

  return cancelledRef;
}
