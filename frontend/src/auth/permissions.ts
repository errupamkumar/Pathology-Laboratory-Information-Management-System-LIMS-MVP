/**
 * Client-side mirror of the backend access matrix (`com.lims.common.security.Roles`).
 * It only decides what the UI offers; the server enforces every rule independently.
 *
 *                      ADMIN  PATHOLOGIST  TECHNICIAN  RECEPTIONIST
 * Register / bill        x                                x
 * View clinical data     x         x            x         x
 * Receive / reject       x                      x
 * Enter results          x         x            x
 * Authorize report                 x
 * Manage users           x
 */
export const ROLES = ['ADMIN', 'PATHOLOGIST', 'TECHNICIAN', 'RECEPTIONIST'] as const;
export type Role = (typeof ROLES)[number];

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrator',
  PATHOLOGIST: 'Pathologist',
  TECHNICIAN: 'Lab Technician',
  RECEPTIONIST: 'Front Desk',
};

export const PERMISSIONS = {
  register: ['ADMIN', 'RECEPTIONIST'],
  viewClinical: ['ADMIN', 'PATHOLOGIST', 'TECHNICIAN', 'RECEPTIONIST'],
  accession: ['ADMIN', 'TECHNICIAN'],
  enterResults: ['ADMIN', 'PATHOLOGIST', 'TECHNICIAN'],
  // ISO 15189 §7.4.1.1: sign-off is PATHOLOGIST-only; ADMIN alone is not enough
  authorize: ['PATHOLOGIST'],
  admin: ['ADMIN'],
} as const satisfies Record<string, readonly Role[]>;

export type Permission = keyof typeof PERMISSIONS;

/** Accepts role names with or without the Spring `ROLE_` prefix. */
export function normalizeRole(role: string): string {
  return role.startsWith('ROLE_') ? role.slice(5) : role;
}

export function hasPermission(roles: readonly string[] | undefined | null, permission: Permission): boolean {
  if (!roles || roles.length === 0) return false;
  const granted = new Set(roles.map(normalizeRole));
  return (PERMISSIONS[permission] as readonly string[]).some(role => granted.has(role));
}
