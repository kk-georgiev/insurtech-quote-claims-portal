import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from './client';
import { saveToken } from './authToken';

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
