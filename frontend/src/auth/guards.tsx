import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { ForbiddenPage } from '../pages/ForbiddenPage';
import { useAuth } from './AuthContext';
import type { Permission } from './permissions';

/** Sends anonymous users to sign-in, remembering where they were headed. */
export const RequireAuth: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, lastLogoutReason } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    // After a deliberate sign-out the next person at the workstation starts fresh
    const state = lastLogoutReason === 'manual' ? undefined : { from: location };
    return <Navigate to="/login" replace state={state} />;
  }
  return <>{children}</>;
};

export const RequirePermission: React.FC<{ permission: Permission; children: React.ReactNode }> = ({
  permission,
  children,
}) => {
  const { can } = useAuth();
  if (!can(permission)) {
    return <ForbiddenPage />;
  }
  return <>{children}</>;
};
