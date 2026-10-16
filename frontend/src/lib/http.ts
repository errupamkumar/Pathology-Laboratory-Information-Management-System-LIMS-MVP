import { env } from '../config/env';
import { ApiError, ProblemDetail } from './apiError';

type QueryValue = string | number | boolean | null | undefined;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, QueryValue>;
  signal?: AbortSignal;
  /** Send the bearer token (default). Public endpoints such as sign-in pass `false`. */
  auth?: boolean;
  responseType?: 'json' | 'blob';
}

interface HttpHooks {
  getToken: () => string | null;
  /** Called when a request that carried a token is rejected with 401 (expired or revoked session). */
  onUnauthorized: () => void;
}

let hooks: HttpHooks = {
  getToken: () => null,
  onUnauthorized: () => {},
};

/** Wired once by the AuthProvider so every request carries the current session token. */
export function configureHttp(next: Partial<HttpHooks>): void {
  hooks = { ...hooks, ...next };
}

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const params = new URLSearchParams();
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params.append(key, String(value));
      }
    }
  }
  const qs = params.toString();
  return `${env.apiBaseUrl}${path}${qs ? `?${qs}` : ''}`;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, signal, auth = true, responseType = 'json' } = options;

  const headers: Record<string, string> = {
    Accept: responseType === 'blob' ? '*/*' : 'application/json',
  };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  const token = auth ? hooks.getToken() : null;
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch (error) {
    if (isAbortError(error)) {
      throw error;
    }
    throw new ApiError({
      status: 0,
      title: 'Network Error',
      detail: 'Cannot reach the LIMS server. Check your network connection and try again.',
    });
  }

  if (!response.ok) {
    const apiError = await toApiError(response);
    if (response.status === 401 && token) {
      hooks.onUnauthorized();
    }
    throw apiError;
  }

  if (response.status === 204) {
    return undefined as T;
  }
  if (responseType === 'blob') {
    return (await response.blob()) as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function isAbortError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { name?: string }).name === 'AbortError';
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = Number(response.headers.get('Retry-After'));
  let problem: ProblemDetail | null = null;
  try {
    const text = await response.text();
    problem = text ? (JSON.parse(text) as ProblemDetail) : null;
  } catch {
    // Non-JSON error body (e.g. a proxy error page); fall back to status-based messages
  }
  return ApiError.fromProblem(response.status, problem, Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined);
}
