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

/** One field-level validation failure, mirrors the backend's `ApiError.FieldError` (AD-7). */
export interface ApiFieldError {
  field: string;
  message: string;
}

export class ApiRequestError extends Error {
  readonly status?: number;
  /**
   * The backend's stable `ApiError.code` (AD-7), when the failure was a
   * shaped API error. This - not `message`, which is dev/log-facing only
   * and must never be shown to an end user (AD-7/AD-8) - is what callers
   * should branch on to pick user-facing copy.
   */
  readonly code?: string;
  readonly fieldErrors?: ApiFieldError[];

  constructor(message: string, status?: number, code?: string, fieldErrors?: ApiFieldError[]) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
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
    // Backend errors are shaped as the AD-7 envelope ({status, code, message,
    // fieldErrors}); parse it out so callers can branch on `code`/`fieldErrors`
    // instead of guessing from the HTTP status alone. Deliberately does NOT
    // surface the envelope's `message` here - it's dev/log-facing only (AD-7).
    let code: string | undefined;
    let fieldErrors: ApiFieldError[] | undefined;
    try {
      const errorBody = (await response.json()) as Partial<{ code: string; fieldErrors: ApiFieldError[] }>;
      code = errorBody?.code;
      fieldErrors = errorBody?.fieldErrors;
    } catch {
      // Non-JSON error body (e.g. a plain-text 404) - fall back below.
    }
    throw new ApiRequestError(
      `Request to ${path} failed with status ${response.status}`,
      response.status,
      code,
      fieldErrors,
    );
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
