import React, { useCallback, useEffect, useState } from 'react';
import { useForm, UseFormRegisterReturn } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { KeyRound, Lock, Shield, Unlock, UserPlus, UserX, UserCheck } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { normalizeRole, ROLE_LABELS, ROLES, Role } from '../../auth/permissions';
import { a11yProps, Field, inputClass } from '../../components/ui/Field';
import { Modal } from '../../components/ui/Modal';
import { Pagination } from '../../components/ui/Pagination';
import { TableSkeleton } from '../../components/ui/Skeleton';
import { useToast } from '../../components/ui/Toaster';
import { ApiError } from '../../lib/apiError';
import { formatDateTime } from '../../lib/format';
import {
  CreateUserForm,
  createUserSchema,
  ResetPasswordForm,
  resetPasswordSchema,
  UpdateRolesForm,
  updateRolesSchema,
} from '../../schemas/admin';
import { api } from '../../services/api';
import type { PageResponse, StaffUser } from '../../types';
import { AdminHeader } from './AdminTabs';

const PAGE_SIZE = 20;

type Dialog = { kind: 'create' } | { kind: 'roles'; user: StaffUser } | { kind: 'password'; user: StaffUser } | null;

const AdminUsersPage: React.FC = () => {
  const toast = useToast();
  const { user: currentUser } = useAuth();
  const [pageIndex, setPageIndex] = useState(0);
  const [page, setPage] = useState<PageResponse<StaffUser> | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setPage(await api.listUsers({ page: pageIndex, size: PAGE_SIZE, sort: 'username' }));
    } catch (error) {
      toast.apiError(error, 'Could not load staff accounts');
    } finally {
      setLoading(false);
    }
  }, [pageIndex, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const replaceUser = (updated: StaffUser) =>
    setPage(current => current && { ...current, content: current.content.map(u => (u.id === updated.id ? updated : u)) });

  const runAction = async (target: StaffUser, action: () => Promise<StaffUser>, message: string) => {
    setBusyId(target.id);
    try {
      replaceUser(await action());
      toast.success(message);
    } catch (error) {
      toast.apiError(error, 'Action failed');
    } finally {
      setBusyId(null);
    }
  };

  const users = page?.content ?? [];

  return (
    <div className="mx-auto max-w-7xl space-y-4 px-4 py-4 sm:space-y-6 sm:py-6">
      <AdminHeader
        actions={
          <button
            type="button"
            onClick={() => setDialog({ kind: 'create' })}
            className="uiverse-btn-primary flex items-center gap-1.5 rounded-lg px-3.5 py-2 text-xs font-bold text-white"
          >
            <UserPlus className="h-3.5 w-3.5" aria-hidden />
            New staff user
          </button>
        }
      />

      <div className="overflow-hidden rounded-xl border border-brand-grid/80 bg-white shadow-sm">
        {loading && !page ? (
          <TableSkeleton rows={6} columns={6} />
        ) : (
          <div className={`overflow-x-auto ${loading ? 'opacity-60' : ''}`}>
            <table className="table-dense w-full min-w-[880px] text-left">
              <thead>
                <tr>
                  <th scope="col">User</th>
                  <th scope="col">Roles</th>
                  <th scope="col">Status</th>
                  <th scope="col">Last sign-in</th>
                  <th scope="col" className="text-right">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.map(user => {
                  const isSelf = user.username === currentUser?.username;
                  const busy = busyId === user.id;
                  return (
                    <tr key={user.id} className={user.enabled ? '' : 'bg-slate-50 text-slate-400'}>
                      <td>
                        <span className="block font-bold text-slate-900">
                          {user.fullName} {isSelf && <span className="text-[10px] font-semibold text-brand-secondary">(you)</span>}
                        </span>
                        <span className="block font-mono text-[11px] text-slate-500">
                          {user.username} · {user.email}
                        </span>
                      </td>
                      <td>
                        <div className="flex flex-wrap gap-1">
                          {user.roles.map(role => (
                            <span key={role} className="rounded-full border border-brand-primary/20 bg-brand-primary/10 px-2 py-0.5 text-[10px] font-bold text-brand-primary">
                              {ROLE_LABELS[normalizeRole(role) as Role] ?? role}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td>
                        {!user.enabled ? (
                          <span className="rounded-full border border-slate-300 bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-600">Disabled</span>
                        ) : user.locked ? (
                          <span className="inline-flex items-center gap-1 rounded-full border border-red-300 bg-red-50 px-2 py-0.5 text-[10px] font-bold text-brand-panic">
                            <Lock className="h-3 w-3" aria-hidden />
                            Locked until {formatDateTime(user.lockedUntil)}
                          </span>
                        ) : (
                          <span className="rounded-full border border-emerald-300 bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-700">Active</span>
                        )}
                        {user.failedLoginAttempts > 0 && !user.locked && (
                          <span className="ml-1 text-[10px] text-amber-700">{user.failedLoginAttempts} failed</span>
                        )}
                      </td>
                      <td className="font-mono text-[11px] text-slate-500">{formatDateTime(user.lastLoginAt)}</td>
                      <td className="whitespace-nowrap text-right">
                        <div className="inline-flex gap-1">
                          <IconButton label="Edit roles" onClick={() => setDialog({ kind: 'roles', user })} disabled={busy}>
                            <Shield className="h-3.5 w-3.5" />
                          </IconButton>
                          <IconButton label="Reset password" onClick={() => setDialog({ kind: 'password', user })} disabled={busy}>
                            <KeyRound className="h-3.5 w-3.5" />
                          </IconButton>
                          {user.locked && (
                            <IconButton label="Unlock account" onClick={() => runAction(user, () => api.unlockUser(user.id), `${user.username} unlocked`)} disabled={busy}>
                              <Unlock className="h-3.5 w-3.5" />
                            </IconButton>
                          )}
                          <IconButton
                            label={user.enabled ? 'Disable account' : 'Enable account'}
                            danger={user.enabled}
                            disabled={busy || isSelf}
                            onClick={() =>
                              runAction(
                                user,
                                () => api.setUserEnabled(user.id, !user.enabled),
                                `${user.username} ${user.enabled ? 'disabled' : 'enabled'}`,
                              )
                            }
                          >
                            {user.enabled ? <UserX className="h-3.5 w-3.5" /> : <UserCheck className="h-3.5 w-3.5" />}
                          </IconButton>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {page && <Pagination page={page} onPageChange={setPageIndex} disabled={loading} noun="users" />}
      </div>

      <CreateUserDialog
        open={dialog?.kind === 'create'}
        onClose={() => setDialog(null)}
        onCreated={created => {
          setDialog(null);
          toast.success(`Account ${created.username} created`);
          void load();
        }}
      />
      {dialog?.kind === 'roles' && (
        <RolesDialog
          user={dialog.user}
          isSelf={dialog.user.username === currentUser?.username}
          onClose={() => setDialog(null)}
          onSaved={updated => {
            setDialog(null);
            replaceUser(updated);
            toast.success(`Roles updated for ${updated.username}`, 'Takes effect on their next request.');
          }}
        />
      )}
      {dialog?.kind === 'password' && (
        <ResetPasswordDialog
          user={dialog.user}
          onClose={() => setDialog(null)}
          onDone={() => {
            toast.success(`Password reset for ${dialog.user.username}`, 'Share it securely; ask them to change it after signing in.');
            setDialog(null);
          }}
        />
      )}
    </div>
  );
};

const IconButton: React.FC<{ label: string; onClick: () => void; disabled?: boolean; danger?: boolean; children: React.ReactNode }> = ({
  label,
  onClick,
  disabled,
  danger,
  children,
}) => (
  <button
    type="button"
    title={label}
    aria-label={label}
    onClick={onClick}
    disabled={disabled}
    className={`rounded-lg border border-brand-grid p-1.5 transition-colors disabled:opacity-40 ${
      danger ? 'text-brand-panic hover:bg-red-50' : 'text-slate-600 hover:bg-slate-100 hover:text-brand-primary'
    }`}
  >
    {children}
  </button>
);

/** Checkbox group bound to a `roles: Role[]` field; RHF collects the checked values into the array. */
const RoleCheckboxes: React.FC<{ field: UseFormRegisterReturn<'roles'>; lockAdmin?: boolean }> = ({ field, lockAdmin }) => (
  <div className="grid grid-cols-2 gap-2">
    {ROLES.map(role => (
      <label key={role} className="flex items-center gap-2 rounded-lg border border-brand-grid px-2.5 py-2 text-xs has-[:checked]:border-brand-primary has-[:checked]:bg-brand-primary/5">
        <input type="checkbox" value={role} {...field} disabled={lockAdmin && role === 'ADMIN'} />
        <span className="font-semibold text-slate-800">{ROLE_LABELS[role]}</span>
      </label>
    ))}
  </div>
);

const CreateUserDialog: React.FC<{ open: boolean; onClose: () => void; onCreated: (user: StaffUser) => void }> = ({ open, onClose, onCreated }) => {
  const toast = useToast();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateUserForm>({
    resolver: zodResolver(createUserSchema),
    defaultValues: { username: '', fullName: '', email: '', password: '', roles: ['TECHNICIAN'] },
  });

  useEffect(() => {
    if (open) reset();
  }, [open, reset]);

  const onSubmit = async (values: CreateUserForm) => {
    try {
      onCreated(await api.createUser(values));
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setError(/email/i.test(error.message) ? 'email' : 'username', { message: error.message });
        return;
      }
      toast.apiError(error, 'Could not create the account');
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      busy={isSubmitting}
      title="New staff user"
      footer={
        <>
          <button type="button" onClick={onClose} disabled={isSubmitting} className="rounded-lg border border-brand-grid px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button type="submit" form="create-user-form" disabled={isSubmitting} className="uiverse-btn-primary rounded-lg px-5 py-2 text-xs font-bold text-white disabled:opacity-60">
            {isSubmitting ? 'Creating…' : 'Create account'}
          </button>
        </>
      }
    >
      <form id="create-user-form" onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-3.5">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Field label="Username" htmlFor="new-username" required error={errors.username?.message}>
            <input {...register('username')} {...a11yProps('new-username', errors.username?.message)} autoComplete="off" className={inputClass(!!errors.username, 'font-mono')} />
          </Field>
          <Field label="Full name" htmlFor="new-fullName" required error={errors.fullName?.message}>
            <input {...register('fullName')} {...a11yProps('new-fullName', errors.fullName?.message)} autoComplete="off" className={inputClass(!!errors.fullName)} />
          </Field>
        </div>
        <Field label="Email" htmlFor="new-email" required error={errors.email?.message}>
          <input {...register('email')} {...a11yProps('new-email', errors.email?.message)} type="email" autoComplete="off" className={inputClass(!!errors.email)} />
        </Field>
        <Field label="Initial password" htmlFor="new-password" required error={errors.password?.message} hint="8+ characters with a letter and a digit">
          <input {...register('password')} {...a11yProps('new-password', errors.password?.message)} type="password" autoComplete="new-password" className={inputClass(!!errors.password)} />
        </Field>
        <fieldset>
          <legend className="mb-1 text-xs font-semibold text-slate-700">Roles</legend>
          <RoleCheckboxes field={register('roles')} />
          {errors.roles && (
            <p className="mt-1 text-[11px] text-brand-panic" role="alert">
              {errors.roles.message}
            </p>
          )}
        </fieldset>
      </form>
    </Modal>
  );
};

const RolesDialog: React.FC<{ user: StaffUser; isSelf: boolean; onClose: () => void; onSaved: (user: StaffUser) => void }> = ({
  user,
  isSelf,
  onClose,
  onSaved,
}) => {
  const toast = useToast();
  const currentRoles = user.roles.map(normalizeRole).filter((r): r is Role => (ROLES as readonly string[]).includes(r));
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<UpdateRolesForm>({ resolver: zodResolver(updateRolesSchema), defaultValues: { roles: currentRoles } });

  const onSubmit = async (values: UpdateRolesForm) => {
    // An admin cannot remove their own ADMIN role (enforced server-side too); the box is disabled, so re-add it
    const roles = isSelf && currentRoles.includes('ADMIN') && !values.roles.includes('ADMIN') ? [...values.roles, 'ADMIN' as Role] : values.roles;
    try {
      onSaved(await api.updateUserRoles(user.id, roles));
    } catch (error) {
      toast.apiError(error, 'Could not update roles');
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      busy={isSubmitting}
      title={`Roles for ${user.fullName}`}
      footer={
        <>
          <button type="button" onClick={onClose} disabled={isSubmitting} className="rounded-lg border border-brand-grid px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button type="submit" form="roles-form" disabled={isSubmitting} className="uiverse-btn-primary rounded-lg px-5 py-2 text-xs font-bold text-white disabled:opacity-60">
            {isSubmitting ? 'Saving…' : 'Save roles'}
          </button>
        </>
      }
    >
      <form id="roles-form" onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-3">
        <RoleCheckboxes field={register('roles')} lockAdmin={isSelf && currentRoles.includes('ADMIN')} />
        {errors.roles && (
          <p className="text-[11px] text-brand-panic" role="alert">
            {errors.roles.message}
          </p>
        )}
        <p className="text-[11px] text-slate-500">Report sign-off requires the Pathologist role; Administrator alone cannot authorize reports.</p>
      </form>
    </Modal>
  );
};

const ResetPasswordDialog: React.FC<{ user: StaffUser; onClose: () => void; onDone: () => void }> = ({ user, onClose, onDone }) => {
  const toast = useToast();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordForm>({ resolver: zodResolver(resetPasswordSchema), defaultValues: { newPassword: '', confirmPassword: '' } });

  const onSubmit = async (values: ResetPasswordForm) => {
    try {
      await api.resetUserPassword(user.id, values.newPassword);
      onDone();
    } catch (error) {
      toast.apiError(error, 'Password reset failed');
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      busy={isSubmitting}
      title={`Reset password for ${user.username}`}
      footer={
        <>
          <button type="button" onClick={onClose} disabled={isSubmitting} className="rounded-lg border border-brand-grid px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-50">
            Cancel
          </button>
          <button type="submit" form="reset-password-form" disabled={isSubmitting} className="uiverse-btn-primary rounded-lg px-5 py-2 text-xs font-bold text-white disabled:opacity-60">
            {isSubmitting ? 'Saving…' : 'Set password'}
          </button>
        </>
      }
    >
      <form id="reset-password-form" onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-3.5">
        <Field label="New password" htmlFor="reset-newPassword" required error={errors.newPassword?.message}>
          <input {...register('newPassword')} {...a11yProps('reset-newPassword', errors.newPassword?.message)} type="password" autoComplete="new-password" className={inputClass(!!errors.newPassword)} />
        </Field>
        <Field label="Confirm password" htmlFor="reset-confirmPassword" required error={errors.confirmPassword?.message}>
          <input
            {...register('confirmPassword')}
            {...a11yProps('reset-confirmPassword', errors.confirmPassword?.message)}
            type="password"
            autoComplete="new-password"
            className={inputClass(!!errors.confirmPassword)}
          />
        </Field>
      </form>
    </Modal>
  );
};

export default AdminUsersPage;
