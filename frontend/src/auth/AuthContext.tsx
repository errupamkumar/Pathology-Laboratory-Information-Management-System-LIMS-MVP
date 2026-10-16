import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { configureHttp } from '../lib/http';
import { api } from '../services/api';
import type { UserProfile } from '../types';
import { hasPermission, Permission } from './permissions';
import { clearSession, loadSession, msUntilExpiry, saveSession, Session } from './session';

export type LogoutReason = 'manual' | 'expired';

interface AuthContextValue {
  user: UserProfile | null;
  isAuthenticated: boolean;
  /** Token for transports that cannot use the HTTP client (the print-agent WebSocket). */
  accessToken: string | null;
  /** Why the previous session ended, shown once on the sign-in page. */
  lastLogoutReason: LogoutReason | null;
  login: (username: string, password: string) => Promise<UserProfile>;
  logout: (reason?: LogoutReason) => void;
  can: (permission: Permission) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

// setTimeout overflows above ~24.8 days; tokens are far shorter but clamp defensively
const MAX_TIMER_MS = 2_147_483_647;

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [session, setSession] = useState<Session | null>(() => loadSession());
  const [lastLogoutReason, setLastLogoutReason] = useState<LogoutReason | null>(null);

  const sessionRef = useRef(session);
  sessionRef.current = session;

  const logout = useCallback((reason: LogoutReason = 'manual') => {
    // Parallel requests can each hit 401; only the first one ends the session and sets the reason
    if (!sessionRef.current) return;
    sessionRef.current = null;
    clearSession();
    api.clearCatalogCache();
    setLastLogoutReason(reason);
    setSession(null);
  }, []);

  const logoutRef = useRef(logout);
  logoutRef.current = logout;

  // Configure synchronously during the first render: child effects (which fire API calls) run
  // before this component's own effects would.
  const httpConfigured = useRef(false);
  if (!httpConfigured.current) {
    configureHttp({
      getToken: () => sessionRef.current?.accessToken ?? null,
      onUnauthorized: () => logoutRef.current('expired'),
    });
    httpConfigured.current = true;
  }

  // Sign out exactly when the token expires instead of waiting for the next 401
  useEffect(() => {
    if (!session) return;
    const timer = window.setTimeout(() => logout('expired'), Math.min(msUntilExpiry(session), MAX_TIMER_MS));
    return () => window.clearTimeout(timer);
  }, [session, logout]);

  // Refresh the profile of a restored session so role changes made by an admin apply on reload
  const restoredToken = useRef(session?.accessToken);
  useEffect(() => {
    const token = restoredToken.current;
    if (!token) return;
    api
      .me()
      .then(user => {
        setSession(current => {
          if (!current || current.accessToken !== token) return current;
          const next = { ...current, user };
          saveSession(next);
          return next;
        });
      })
      .catch(() => {
        // A 401 already triggered logout through the HTTP hook; other failures keep the cached profile
      });
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const response = await api.login(username, password);
    const next: Session = {
      accessToken: response.accessToken,
      expiresAt: Date.now() + response.expiresIn * 1000,
      user: response.user,
    };
    saveSession(next);
    setLastLogoutReason(null);
    setSession(next);
    return response.user;
  }, []);

  const can = useCallback((permission: Permission) => hasPermission(session?.user.roles, permission), [session]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      isAuthenticated: session !== null,
      accessToken: session?.accessToken ?? null,
      lastLogoutReason,
      login,
      logout,
      can,
    }),
    [session, lastLogoutReason, login, logout, can],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
