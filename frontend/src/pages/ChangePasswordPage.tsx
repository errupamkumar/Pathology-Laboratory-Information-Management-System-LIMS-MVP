import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { KeyRound } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { a11yProps, Field, inputClass } from '../components/ui/Field';
import { useToast } from '../components/ui/Toaster';
import { ApiError } from '../lib/apiError';
import { ChangePasswordForm, changePasswordSchema } from '../schemas/auth';
import { PASSWORD_POLICY_MESSAGE } from '../schemas/common';
import { api } from '../services/api';

const ChangePasswordPage: React.FC = () => {
  const toast = useToast();
  const { user } = useAuth();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordForm>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = async (values: ChangePasswordForm) => {
    try {
      await api.changePassword(values.currentPassword, values.newPassword);
      reset();
      toast.success('Password changed', 'Use the new password the next time you sign in.');
    } catch (error) {
      // The backend answers a wrong current password with 400 (not 401) so the session survives
      if (error instanceof ApiError && error.status === 400 && /current password/i.test(error.message)) {
        setError('currentPassword', { message: error.message });
        return;
      }
      toast.apiError(error, 'Password change failed');
    }
  };

  return (
    <div className="mx-auto max-w-md px-4 py-8">
      <div className="rounded-xl border border-brand-grid/80 bg-white p-6 shadow-sm">
        <h1 className="flex items-center gap-2 text-base font-bold text-slate-900">
          <KeyRound className="h-5 w-5 text-brand-primary" aria-hidden />
          Change password
        </h1>
        <p className="mt-1 text-xs text-slate-500">
          Signed in as <span className="font-mono font-semibold">{user?.username}</span>. {PASSWORD_POLICY_MESSAGE}.
        </p>
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="mt-5 space-y-4">
          <Field label="Current password" htmlFor="currentPassword" required error={errors.currentPassword?.message}>
            <input
              {...register('currentPassword')}
              {...a11yProps('currentPassword', errors.currentPassword?.message)}
              type="password"
              autoComplete="current-password"
              className={inputClass(!!errors.currentPassword)}
            />
          </Field>
          <Field label="New password" htmlFor="newPassword" required error={errors.newPassword?.message}>
            <input
              {...register('newPassword')}
              {...a11yProps('newPassword', errors.newPassword?.message)}
              type="password"
              autoComplete="new-password"
              className={inputClass(!!errors.newPassword)}
            />
          </Field>
          <Field label="Confirm new password" htmlFor="confirmPassword" required error={errors.confirmPassword?.message}>
            <input
              {...register('confirmPassword')}
              {...a11yProps('confirmPassword', errors.confirmPassword?.message)}
              type="password"
              autoComplete="new-password"
              className={inputClass(!!errors.confirmPassword)}
            />
          </Field>
          <button
            type="submit"
            disabled={isSubmitting}
            className="uiverse-btn-primary w-full rounded-lg py-2.5 text-xs font-bold uppercase tracking-wider text-white disabled:opacity-60"
          >
            {isSubmitting ? 'Updating…' : 'Update password'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default ChangePasswordPage;
