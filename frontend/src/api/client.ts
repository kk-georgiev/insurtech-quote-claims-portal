// Thin typed fetch wrapper (AD-10: no data-fetching library this milestone -
// too few screens to justify React Query or similar). Every backend call
// goes through this module; the backend origin always comes from
// VITE_API_URL and is never hardcoded (AD-9/AD-10).

const API_URL = import.meta.env.VITE_API_URL;

if (!API_URL) {
  // Fail loudly during dev rather than silently calling a relative/undefined
  // URL. Copy .env.example to .env at the repo root to fix.
  console.error(
    'VITE_API_URL is not set. Copy .env.example to .env at the repo root and set VITE_API_URL there.',
  );
}

export class ApiRequestError extends Error {
  readonly status?: number;

  constructor(message: string, status?: number) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
  }
}

export interface ApiFetchOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

/**
 * Calls `${VITE_API_URL}${path}` and parses the JSON response.
 * Throws {@link ApiRequestError} on network failure or a non-2xx response.
 */
export async function apiFetch<TResponse>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<TResponse> {
  const { body, headers, ...rest } = options;

  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      ...rest,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiRequestError(`Network error calling ${path}`);
  }

  if (!response.ok) {
    throw new ApiRequestError(`Request to ${path} failed with status ${response.status}`, response.status);
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  try {
    return (await response.json()) as TResponse;
  } catch {
    throw new ApiRequestError(`Response from ${path} was not valid JSON`, response.status);
  }
}
