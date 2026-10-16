import { z } from 'zod';
import { ROLES } from '../auth/permissions';
import { passwordPolicy, USERNAME_REGEX } from './common';

const roleList = z.array(z.enum(ROLES)).min(1, 'Assign at least one role');

export const createUserSchema = z.object({
  username: z
    .string()
    .trim()
    .regex(USERNAME_REGEX, "Username must be 3-100 characters: letters, digits, '.', '_' or '-'"),
  fullName: z.string().trim().min(1, 'Full name is mandatory').max(200, 'At most 200 characters'),
  email: z.string().trim().min(1, 'Email is mandatory').max(150).email('Email address is invalid'),
  password: passwordPolicy,
  roles: roleList,
});
export type CreateUserForm = z.infer<typeof createUserSchema>;

export const updateRolesSchema = z.object({ roles: roleList });
export type UpdateRolesForm = z.infer<typeof updateRolesSchema>;

export const resetPasswordSchema = z
  .object({
    newPassword: passwordPolicy,
    confirmPassword: z.string().min(1, 'Please confirm the new password'),
  })
  .refine(values => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  });
export type ResetPasswordForm = z.infer<typeof resetPasswordSchema>;
