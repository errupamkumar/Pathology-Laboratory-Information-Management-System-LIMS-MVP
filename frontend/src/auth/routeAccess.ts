import { hasPermission, Permission } from './permissions';

/** Permission required by each guarded route prefix (keep in sync with AppRoutes). */
export const ROUTE_PERMISSIONS: readonly (readonly [prefix: string, permission: Permission])[] = [
  ['/register', 'register'],
  ['/accessioning', 'viewClinical'],
  ['/results', 'viewClinical'],
  ['/reports', 'viewClinical'],
  ['/spooler', 'viewClinical'],
  ['/admin', 'admin'],
];

export function permissionForPath(pathname: string): Permission | null {
  const match = ROUTE_PERMISSIONS.find(([prefix]) => pathname === prefix || pathname.startsWith(`${prefix}/`));
  return match ? match[1] : null;
}

/**
 * Whether a user with these roles may open the path. Used after sign-in so that a remembered
 * return path belonging to a previous user never lands the new user on an access-denied screen.
 */
export function canAccessPath(pathname: string, roles: readonly string[]): boolean {
  const permission = permissionForPath(pathname);
  return permission === null || hasPermission(roles, permission);
}
