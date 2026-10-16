import { describe, expect, it } from 'vitest';
import { hasPermission } from './permissions';
import { canAccessPath } from './routeAccess';
import { clearSession, loadSession, saveSession } from './session';
import { testUser } from '../test/utils';

describe('permission matrix (mirrors com.lims.common.security.Roles)', () => {
  it.each([
    ['RECEPTIONIST', 'register', true],
    ['RECEPTIONIST', 'accession', false],
    ['RECEPTIONIST', 'enterResults', false],
    ['TECHNICIAN', 'register', false],
    ['TECHNICIAN', 'accession', true],
    ['TECHNICIAN', 'enterResults', true],
    ['TECHNICIAN', 'authorize', false],
    ['PATHOLOGIST', 'enterResults', true],
    ['PATHOLOGIST', 'authorize', true],
    ['PATHOLOGIST', 'admin', false],
    ['ADMIN', 'admin', true],
    ['ADMIN', 'register', true],
  ] as const)('%s → %s = %s', (role, permission, expected) => {
    expect(hasPermission([role], permission)).toBe(expected);
  });

  it('does not let ADMIN alone authorize reports (ISO 15189 §7.4.1.1)', () => {
    expect(hasPermission(['ADMIN'], 'authorize')).toBe(false);
    expect(hasPermission(['ADMIN', 'PATHOLOGIST'], 'authorize')).toBe(true);
  });

  it('accepts Spring ROLE_ prefixed names and denies empty role sets', () => {
    expect(hasPermission(['ROLE_TECHNICIAN'], 'accession')).toBe(true);
    expect(hasPermission([], 'viewClinical')).toBe(false);
    expect(hasPermission(undefined, 'viewClinical')).toBe(false);
  });
});

describe('canAccessPath', () => {
  it('checks the permission of the route prefix', () => {
    expect(canAccessPath('/register', ['TECHNICIAN'])).toBe(false);
    expect(canAccessPath('/results/abc', ['TECHNICIAN'])).toBe(true);
    expect(canAccessPath('/admin/audit', ['PATHOLOGIST'])).toBe(false);
    expect(canAccessPath('/account/password', ['RECEPTIONIST'])).toBe(true);
    expect(canAccessPath('/registered-elsewhere', ['TECHNICIAN'])).toBe(true);
  });
});

describe('session storage', () => {
  const user = testUser(['TECHNICIAN']);

  it('round-trips a valid session', () => {
    const session = { accessToken: 't', expiresAt: Date.now() + 600_000, user };
    saveSession(session);
    expect(loadSession()).toEqual(session);
  });

  it('discards expired sessions', () => {
    saveSession({ accessToken: 't', expiresAt: Date.now() + 1_000, user });
    expect(loadSession()).toBeNull();
    expect(window.sessionStorage.getItem('lims.session')).toBeNull();
  });

  it('discards malformed data', () => {
    window.sessionStorage.setItem('lims.session', '{not json');
    expect(loadSession()).toBeNull();
    window.sessionStorage.setItem('lims.session', JSON.stringify({ accessToken: 't' }));
    expect(loadSession()).toBeNull();
  });

  it('clears the session', () => {
    saveSession({ accessToken: 't', expiresAt: Date.now() + 600_000, user });
    clearSession();
    expect(loadSession()).toBeNull();
  });
});
