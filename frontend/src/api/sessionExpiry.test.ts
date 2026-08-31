import { afterEach, describe, expect, it, vi } from 'vitest';
import { notifySessionExpired, onSessionExpired } from './sessionExpiry';

describe('sessionExpiry', () => {
  afterEach(() => {
    // Reset the module's single listener slot so one test's registration
    // never leaks into the next.
    onSessionExpired(() => {});
  });

  it('calls the registered handler when notified', () => {
    const handler = vi.fn();
    onSessionExpired(handler);

    notifySessionExpired();

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('does not throw when notified with no handler registered', () => {
    // Simulate the "nothing has subscribed yet" state a unit test that
    // exercises client.ts directly, without importing app/browserRouter.ts,
    // is deliberately allowed to be in.
    onSessionExpired(undefined as unknown as () => void);

    expect(() => notifySessionExpired()).not.toThrow();
  });

  it('a later registration replaces the earlier one rather than stacking', () => {
    const first = vi.fn();
    const second = vi.fn();
    onSessionExpired(first);
    onSessionExpired(second);

    notifySessionExpired();

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);
  });
});
