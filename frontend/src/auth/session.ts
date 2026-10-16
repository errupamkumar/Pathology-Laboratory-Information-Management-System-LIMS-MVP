import type { UserProfile } from '../types';

export interface Session {
  accessToken: string;
  /** Epoch milliseconds after which the token is no longer accepted. */
  expiresAt: number;
  user: UserProfile;
}

const STORAGE_KEY = 'lims.session';
/** Treat the token as expired slightly early so a request never races the server-side expiry. */
const EXPIRY_SKEW_MS = 30_000;

/**
 * Sessions live in sessionStorage: scoped to one tab and discarded when it closes, which suits
 * shared laboratory workstations better than a long-lived localStorage token.
 */
export function loadSession(now: number = Date.now()): Session | null {
  let raw: string | null;
  try {
    raw = window.sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
  if (!raw) return null;

  try {
    const session = JSON.parse(raw) as Partial<Session>;
    if (
      typeof session.accessToken !== 'string' ||
      typeof session.expiresAt !== 'number' ||
      !session.user ||
      !Array.isArray(session.user.roles)
    ) {
      clearSession();
      return null;
    }
    if (session.expiresAt - EXPIRY_SKEW_MS <= now) {
      clearSession();
      return null;
    }
    return session as Session;
  } catch {
    clearSession();
    return null;
  }
}

export function saveSession(session: Session): void {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  } catch {
    // Storage disabled (private mode / policy): the session still works until the tab reloads
  }
}

export function clearSession(): void {
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

export function msUntilExpiry(session: Session, now: number = Date.now()): number {
  return Math.max(0, session.expiresAt - EXPIRY_SKEW_MS - now);
}
