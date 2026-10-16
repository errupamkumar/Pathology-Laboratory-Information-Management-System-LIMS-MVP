import { z } from 'zod';
import { passwordPolicy } from './common';

export const loginSchema = z.object({
  username: z.string().trim().min(1, 'Username is mandatory').max(100, 'Username is too long'),
  password: z.string().min(1, 'Password is mandatory').max(128, 'Password is too long'),
});
export type LoginForm = z.infer<typeof loginSchema>;

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is mandatory').max(128),
    newPassword: passwordPolicy,
    confirmPassword: z.string().min(1, 'Please confirm the new password'),
  })
  .superRefine((values, ctx) => {
    if (values.confirmPassword && values.newPassword !== values.confirmPassword) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['confirmPassword'], message: 'Passwords do not match' });
    }
    if (values.currentPassword && values.newPassword === values.currentPassword) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['newPassword'],
        message: 'New password must differ from the current password',
      });
    }
  });
export type ChangePasswordForm = z.infer<typeof changePasswordSchema>;
