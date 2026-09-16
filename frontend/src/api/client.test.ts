import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from './client';
import { saveToken, getToken } from './authToken';
import { onSessionExpired } from './sessionExpiry';

// Unit-tests the `authenticated` option added in Story 1.7 - the first
// authenticated call path in this codebase (spec Boundaries & Constraints:
// "apiFetch currently sends no auth header anywhere"). `fetch` itself is
// stubbed here (not `apiFetch`, unlike the component-level mocking
// `QuoteForm.test.tsx`/`LoginForm.test.tsx` do) so the header-attachment
// logic inside `apiFetch` is actually exercised.
function stubFetch(status = 200, body: unknown = {}) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function headersOf(fetchMock: ReturnType<typeof vi.fn>): Record<string, string> {
  const init = fetchMock.mock.calls[0][1] as RequestInit;
  return init.headers as Record<string, string>;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('apiFetch authenticated option', () => {
  it('attaches Authorization: Bearer <token> when authenticated is true and a token is stored', async () => {
    saveToken('a-jwt-token');
    const fetchMock = stubFetch();

    await apiFetch('/api/v1/quotes', { method: 'POST', authenticated: true, body: {} });

    expect(headersOf(fetchMock).Authorization).toBe('Bearer a-jwt-token');
  });

  it('omits the Authorization header when authenticated is true but no token is stored', async () => {
    const fetchMock = stubFetch();

    await apiFetch('/api/v1/quotes', { method: 'POST', authenticated: true, body: {} });

    expect(headersOf(fetchMock).Authorization).toBeUndefined();
  });

  it('omits the Authorization header when authenticated is not set, even if a token is stored (login/register/health unaffected)', async () => {
    saveToken('a-jwt-token');
    const fetchMock = stubFetch();

    await apiFetch('/api/v1/auth/login', { method: 'POST', body: { email: 'a@example.com', password: 'x' } });

    expect(headersOf(fetchMock).Authorization).toBeUndefined();
  });
});

// Story 7.1, FR-M3-12: "a 401 ends the session cleanly" - handled once,
// here, never per screen. `onSessionExpired` is reset in `afterEach` below
// so one test's spy never leaks into the next.
describe('apiFetch session-expiry handling (Story 7.1)', () => {
  afterEach(() => {
    onSessionExpired(() => {});
  });

  it('clears the stored token and notifies session-expiry on a 401 from an authenticated call', async () => {
    saveToken('a-jwt-token');
    const handler = vi.fn();
    onSessionExpired(handler);
    stubFetch(401, { status: 401, code: 'AUTH_UNAUTHENTICATED' });

    await expect(
      apiFetch('/api/v1/quotes', { authenticated: true }),
    ).rejects.toThrow();

    expect(getToken()).toBeNull();
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('does not clear the token or notify on a 401 from a non-authenticated call (e.g. a failed login)', async () => {
    saveToken('a-jwt-token');
    const handler = vi.fn();
    onSessionExpired(handler);
    stubFetch(401, { status: 401, code: 'AUTH_INVALID_CREDENTIALS' });

    await expect(
      apiFetch('/api/v1/auth/login', { method: 'POST', body: { email: 'a@example.com', password: 'x' } }),
    ).rejects.toThrow();

    // A wrong-password 401 on the login endpoint (never sent with
    // `authenticated: true`) is a completely different, expected case with
    // no session to end - it must not clear an unrelated stored session or
    // bounce the visitor away from the login screen they are already on.
    expect(getToken()).toBe('a-jwt-token');
    expect(handler).not.toHaveBeenCalled();
  });

  it('does not clear the token or notify on a non-401 error from an authenticated call', async () => {
    saveToken('a-jwt-token');
    const handler = vi.fn();
    onSessionExpired(handler);
    stubFetch(403, { status: 403, code: 'AUTH_FORBIDDEN' });

    await expect(apiFetch('/api/v1/quotes', { authenticated: true })).rejects.toThrow();

    expect(getToken()).toBe('a-jwt-token');
    expect(handler).not.toHaveBeenCalled();
  });

  it('still throws ApiRequestError with the 401 status after clearing the session, so callers can still branch if they need to', async () => {
    saveToken('a-jwt-token');
    stubFetch(401, { status: 401, code: 'AUTH_UNAUTHENTICATED' });

    await expect(apiFetch('/api/v1/quotes', { authenticated: true })).rejects.toMatchObject({
      status: 401,
      code: 'AUTH_UNAUTHENTICATED',
    });
  });
});

// Story 10.4: the additive `responseType: 'blob'` option `ClaimDetail` uses
// for its authenticated per-attachment image fetch - unmocked `fetch` here
// too, so the real branch (not a mocked `apiFetch`) is what's exercised.
describe('apiFetch responseType option (Story 10.4)', () => {
  function stubBlobFetch(status: number, body: BodyInit, contentType: string) {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(body, { status, headers: { 'Content-Type': contentType } }));
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  afterEach(() => {
    onSessionExpired(() => {});
  });

  it('resolves a Blob, not parsed JSON, on a 2xx response when responseType is "blob"', async () => {
    stubBlobFetch(200, new Blob(['fake-image-bytes'], { type: 'image/jpeg' }), 'image/jpeg');

    const result = await apiFetch<Blob>('/api/v1/claims/c1/attachments/a1', {
      authenticated: true,
      responseType: 'blob',
    });

    // Not `toBeInstanceOf(Blob)`, and not an exact byte count: the test
    // environment's `Response.blob()` is a different realm/implementation
    // than plain Node's, with its own chunking quirks unrelated to this
    // option's own logic. `type`/`size > 0`/`arrayBuffer` are enough to
    // prove this really is blob data, not the JSON envelope this path
    // resolved to before `responseType` existed.
    expect(result.type).toBe('image/jpeg');
    expect(result.size).toBeGreaterThan(0);
    expect(typeof result.arrayBuffer).toBe('function');
  });

  it('still parses the JSON error envelope on a non-2xx response, even when responseType is "blob"', async () => {
    stubBlobFetch(404, JSON.stringify({ status: 404, code: 'ATTACHMENT_NOT_FOUND' }), 'application/json');

    await expect(
      apiFetch('/api/v1/claims/c1/attachments/a1', { authenticated: true, responseType: 'blob' }),
    ).rejects.toMatchObject({ status: 404, code: 'ATTACHMENT_NOT_FOUND' });
  });

  it('still clears the token and notifies session-expiry on a 401, even when responseType is "blob"', async () => {
    saveToken('a-jwt-token');
    const handler = vi.fn();
    onSessionExpired(handler);
    stubBlobFetch(401, JSON.stringify({ status: 401, code: 'AUTH_UNAUTHENTICATED' }), 'application/json');

    await expect(
      apiFetch('/api/v1/claims/c1/attachments/a1', { authenticated: true, responseType: 'blob' }),
    ).rejects.toThrow();

    expect(getToken()).toBeNull();
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('parses JSON as before when responseType is omitted (default unchanged)', async () => {
    const fetchMock = stubFetch(200, { id: 'q1' });

    const result = await apiFetch<{ id: string }>('/api/v1/quotes/q1', { authenticated: true });

    expect(result).toEqual({ id: 'q1' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
