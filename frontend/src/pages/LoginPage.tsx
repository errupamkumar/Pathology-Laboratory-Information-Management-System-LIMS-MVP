import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Location, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { AlertCircle, Eye, EyeOff, FlaskConical, Info, LogIn, ShieldCheck } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { canAccessPath } from '../auth/routeAccess';
import { a11yProps, Field, inputClass } from '../components/ui/Field';
import { env } from '../config/env';
import { ApiError, errorMessage } from '../lib/apiError';
import { LoginForm, loginSchema } from '../schemas/auth';

/** Seeded by DataInitializer when `lims.seed.demo-data=true`; shown only when VITE_SHOW_DEMO_ACCOUNTS=true. */
const DEMO_ACCOUNTS = [
  { username: 'admin', password: 'admin123', role: 'Administrator (all roles)' },
  { username: 'reception', password: 'Reception@123', role: 'Front desk' },
  { username: 'technician', password: 'Technician@123', role: 'Lab technician' },
  { username: 'pathologist', password: 'Pathologist@123', role: 'Pathologist' },
];

const LoginPage: React.FC = () => {
  const { login, isAuthenticated, user, lastLogoutReason } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [formError, setFormError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: '', password: '' },
  });

  const from = (location.state as { from?: Location } | null)?.from;
  // A remembered path is honoured only if this user may open it (it may belong to a previous user)
  const destinationFor = (roles: readonly string[]) =>
    from && canAccessPath(from.pathname, roles) ? `${from.pathname}${from.search ?? ''}` : '/';

  if (isAuthenticated && user) {
    return <Navigate to={destinationFor(user.roles)} replace />;
  }

  const onSubmit = async (values: LoginForm) => {
    setFormError(null);
    try {
      const signedIn = await login(values.username, values.password);
      navigate(destinationFor(signedIn.roles), { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setFormError('Invalid username or password.');
      } else {
        setFormError(errorMessage(error, 'Sign-in failed. Please try again.'));
      }
      setValue('password', '');
      setFocus('password');
    }
  };

  return (
    <div className="flex min-h-screen bg-brand-surface">
      <aside className="relative hidden w-[44%] flex-col justify-between overflow-hidden bg-brand-primary p-10 text-white lg:flex">
        <div className="flex items-center space-x-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-white/20 bg-white/15">
            <FlaskConical className="h-5 w-5" aria-hidden />
          </div>
          <span className="text-lg font-extrabold uppercase tracking-tight">ApexPath LIMS</span>
        </div>
        <div>
          <h1 className="text-3xl font-extrabold leading-tight">
            Pathology laboratory
            <br />
            information management
          </h1>
          <p className="mt-3 max-w-sm text-sm text-white/75">
            Registration, specimen accessioning, result validation and pathologist sign-off: one audited workflow.
          </p>
          <ul className="mt-6 space-y-2 text-xs text-white/85">
            {['Role-based access for every workstation', 'Immutable audit trail on clinical records', 'Digitally signed, QR-verifiable reports'].map(line => (
              <li key={line} className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-emerald-300" aria-hidden />
                {line}
              </li>
            ))}
          </ul>
        </div>
        <p className="text-[11px] text-white/50">ISO 15189:2022 aligned · Authorized personnel only</p>
      </aside>

      <main className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm">
          <div className="mb-6 flex items-center space-x-2 lg:hidden">
            <FlaskConical className="h-6 w-6 text-brand-primary" aria-hidden />
            <span className="text-base font-extrabold uppercase tracking-tight text-brand-primary">ApexPath LIMS</span>
          </div>

          <h2 className="text-xl font-bold text-slate-900">Sign in</h2>
          <p className="mt-1 text-xs text-slate-500">Use your laboratory staff account.</p>

          {lastLogoutReason === 'expired' && !formError && (
            <div className="mt-4 flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-xs text-amber-900" role="status">
              <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              Your session expired. Please sign in again to continue.
            </div>
          )}
          {formError && (
            <div className="mt-4 flex items-start gap-2 rounded-lg border border-red-300 bg-red-50 p-3 text-xs text-red-900" role="alert">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-brand-panic" aria-hidden />
              {formError}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="mt-5 space-y-4" noValidate>
            <Field label="Username" htmlFor="username" error={errors.username?.message}>
              <input
                {...register('username')}
                {...a11yProps('username', errors.username?.message)}
                autoComplete="username"
                autoFocus
                className={inputClass(!!errors.username)}
              />
            </Field>
            <Field label="Password" htmlFor="password" error={errors.password?.message}>
              <div className="relative">
                <input
                  {...register('password')}
                  {...a11yProps('password', errors.password?.message)}
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  className={inputClass(!!errors.password, 'pr-9')}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(value => !value)}
                  className="absolute right-2 top-1.5 rounded p-1 text-slate-400 hover:text-slate-600"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                </button>
              </div>
            </Field>
            <button
              type="submit"
              disabled={isSubmitting}
              className="uiverse-btn-primary flex w-full items-center justify-center gap-2 rounded-lg py-2.5 text-xs font-bold uppercase tracking-wider text-white disabled:opacity-60"
            >
              <LogIn className="h-4 w-4" aria-hidden />
              {isSubmitting ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          {env.showDemoAccounts && (
            <div className="mt-6 rounded-xl border border-dashed border-brand-grid bg-white p-3">
              <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Demo accounts (non-production)</p>
              <div className="mt-2 grid grid-cols-2 gap-1.5">
                {DEMO_ACCOUNTS.map(account => (
                  <button
                    key={account.username}
                    type="button"
                    onClick={() => {
                      setValue('username', account.username, { shouldValidate: true });
                      setValue('password', account.password, { shouldValidate: true });
                    }}
                    className="rounded-lg border border-brand-grid px-2 py-1.5 text-left hover:border-brand-secondary hover:bg-slate-50"
                  >
                    <span className="block font-mono text-[11px] font-bold text-slate-800">{account.username}</span>
                    <span className="block text-[10px] text-slate-500">{account.role}</span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
};

export default LoginPage;
