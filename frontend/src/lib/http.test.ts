import { beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../test/utils';
import { ApiError, errorMessage } from './apiError';
import { buildUrl, configureHttp, request } from './http';

/** Resolves with the rejection of a request that is expected to fail. */
async function rejectionOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error('Expected the request to fail');
}

describe('http client', () => {
  const onUnauthorized = vi.fn();

  beforeEach(() => {
    onUnauthorized.mockReset();
    configureHttp({ getToken: () => 'token-123', onUnauthorized });
  });

  it('sends the bearer token and JSON body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    await request('/orders/book', { method: 'POST', body: { a: 1 } });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/orders/book');
    expect(init.headers.Authorization).toBe('Bearer token-123');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.body).toBe('{"a":1}');
  });

  it('omits the token for public endpoints', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}));
    vi.stubGlobal('fetch', fetchMock);

    await request('/auth/login', { method: 'POST', body: {}, auth: false });

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('maps an RFC 7807 validation problem to ApiError with field details', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            title: 'Input Validation Failed',
            status: 400,
            detail: 'Validation failed for request parameters',
            invalidFields: { phone: 'Phone number format is invalid' },
            correlationId: 'abc-123',
          },
          400,
        ),
      ),
    );

    const error = await rejectionOf(request('/orders/book', { method: 'POST', body: {} }));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.title).toBe('Input Validation Failed');
    expect(error.invalidFields).toEqual({ phone: 'Phone number format is invalid' });
    expect(error.correlationId).toBe('abc-123');
    expect(errorMessage(error)).toBe('Phone number format is invalid');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('ends the session when an authenticated request is rejected with 401', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ title: 'Authentication Required' }, 401)));

    await expect(request('/orders')).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('does not treat a failed sign-in as an expired session', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ detail: 'Bad credentials' }, 401)));

    await expect(request('/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toThrow('Bad credentials');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('returns undefined for 204 No Content', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(request('/patients/phone/123')).resolves.toBeUndefined();
  });

  it('reports network failures as status 0', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const error = await rejectionOf(request('/orders'));
    expect(error).toBeInstanceOf(ApiError);
    expect(error.isNetworkError).toBe(true);
  });

  it('surfaces Retry-After on rate limiting', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 429, headers: { 'Retry-After': '30' } })));

    const error = await rejectionOf(request('/auth/login', { auth: false }));
    expect(error.retryAfterSeconds).toBe(30);
    expect(error.message).toContain('30s');
  });

  it('skips empty query parameters', () => {
    expect(buildUrl('/accessioning/samples', { status: undefined, page: 0, size: 25, q: '' })).toBe(
      '/api/v1/accessioning/samples?page=0&size=25',
    );
  });
});
