import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AppProviders } from '../App';
import { AppRoutes, ROUTER_FUTURE } from '../AppRoutes';
import type { UserProfile } from '../types';

export function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

export const EMPTY_PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true };

export function testUser(roles: string[], overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    id: '00000000-0000-0000-0000-000000000001',
    username: 'tester',
    fullName: 'Test User',
    email: 'tester@apexpathlab.com',
    roles,
    lastLoginAt: null,
    ...overrides,
  };
}

/** Restores a signed-in session the way the AuthProvider persists it. */
export function seedSession(roles: string[], expiresInMs = 60 * 60 * 1000): UserProfile {
  const user = testUser(roles);
  window.sessionStorage.setItem(
    'lims.session',
    JSON.stringify({ accessToken: 'test-token', expiresAt: Date.now() + expiresInMs, user }),
  );
  return user;
}

type Handler = (url: URL, init: RequestInit) => Response | Promise<Response>;

/**
 * Routes fetch calls by path substring; unmatched GETs get an empty page so screens can render.
 * Returns the mock for call assertions.
 */
export function mockFetch(routes: Record<string, Handler> = {}) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost:3000');
    for (const [fragment, handler] of Object.entries(routes)) {
      if (url.pathname.includes(fragment)) return handler(url, init);
    }
    return jsonResponse(EMPTY_PAGE);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]} future={ROUTER_FUTURE}>
      <AppProviders>
        <AppRoutes />
      </AppProviders>
    </MemoryRouter>,
  );
}
