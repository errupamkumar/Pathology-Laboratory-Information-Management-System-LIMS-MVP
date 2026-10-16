import React, { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import {
  Activity,
  ChevronDown,
  Cpu,
  FileText,
  FlaskConical,
  KeyRound,
  LogOut,
  Menu,
  Printer,
  ShieldAlert,
  ShieldCheck,
  UserPlus,
  X,
} from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { normalizeRole, Permission, ROLE_LABELS, Role } from '../../auth/permissions';
import { env } from '../../config/env';
import { usePrintAgent } from '../../hooks/PrintAgentContext';

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  permission: Permission;
  shortcut?: string;
  iconClass?: string;
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/register', label: 'Registration', icon: UserPlus, permission: 'register', shortcut: 'Alt+N' },
  { to: '/accessioning', label: 'Accessioning', icon: Activity, permission: 'viewClinical' },
  { to: '/results', label: 'Results', icon: ShieldAlert, permission: 'viewClinical', iconClass: 'text-amber-300' },
  { to: '/reports', label: 'Reports', icon: FileText, permission: 'viewClinical' },
  { to: '/spooler', label: 'Spooler', icon: Cpu, permission: 'viewClinical', iconClass: 'text-cyan-300' },
  { to: '/admin/users', label: 'Admin', icon: ShieldCheck, permission: 'admin' },
];

export const Navbar: React.FC = () => {
  const { user, can } = useAuth();
  const { connected, agentCount } = usePrintAgent();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const items = NAV_ITEMS.filter(item => can(item.permission));
  const showEnvBadge = env.appEnv && env.appEnv !== 'production';

  return (
    <>
      <header className="no-print sticky top-0 z-40 border-b border-brand-primary/80 bg-brand-primary text-white shadow-md">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="flex h-14 items-center justify-between gap-3">
            <div className="flex items-center space-x-3">
              <button
                type="button"
                onClick={() => setMobileMenuOpen(true)}
                className="rounded-md p-1.5 text-white hover:bg-white/10 lg:hidden"
                aria-label="Open navigation menu"
              >
                <Menu className="h-5 w-5" />
              </button>

              <Link to="/" className="flex items-center space-x-3">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-white/20 bg-white/15 shadow-inner sm:h-9 sm:w-9">
                  <FlaskConical className="h-4 w-4 sm:h-5 sm:w-5" aria-hidden />
                </div>
                <div>
                  <div className="flex items-center space-x-1.5">
                    <span className="text-sm font-extrabold uppercase tracking-tight sm:text-base">ApexPath LIMS</span>
                    {showEnvBadge && (
                      <span className="rounded bg-amber-400/90 px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide text-amber-950">
                        {env.appEnv}
                      </span>
                    )}
                  </div>
                  <p className="hidden truncate text-[10px] tracking-wider text-white/75 sm:block">
                    ISO 15189:2022 Pathology Core
                  </p>
                </div>
              </Link>
            </div>

            <nav className="hidden space-x-1 lg:flex" aria-label="Main">
              {items.map(item => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    `flex items-center space-x-1.5 rounded px-3 py-1.5 text-xs font-semibold transition-all ${
                      isActive ? 'bg-brand-secondary text-white shadow-sm ring-1 ring-white/30' : 'text-white/80 hover:bg-white/10 hover:text-white'
                    }`
                  }
                >
                  <item.icon className={`h-3.5 w-3.5 ${item.iconClass ?? ''}`} />
                  <span>{item.label}</span>
                </NavLink>
              ))}
            </nav>

            <div className="flex items-center space-x-2">
              <Link
                to="/spooler"
                title={`Silent print bridge ${connected ? 'connected' : 'reconnecting'}`}
                className="flex items-center space-x-1.5 rounded-full border border-white/15 bg-black/30 px-2 py-1 text-[10px] transition-colors hover:bg-black/40 sm:px-2.5 sm:text-[11px]"
              >
                <span className="relative flex h-2 w-2">
                  <span
                    className={`absolute inline-flex h-full w-full animate-ping rounded-full opacity-75 ${connected ? 'bg-emerald-400' : 'bg-amber-400'}`}
                  />
                  <span className={`relative inline-flex h-2 w-2 rounded-full ${connected ? 'bg-emerald-500' : 'bg-amber-500'}`} />
                </span>
                <Printer className="h-3.5 w-3.5 text-white/80" aria-hidden />
                <span className="hidden whitespace-nowrap font-medium sm:inline">
                  {connected ? `Spooler (${agentCount})` : 'Connecting…'}
                </span>
              </Link>
              {user && <UserMenu />}
            </div>
          </div>
        </div>
      </header>

      {mobileMenuOpen && (
        <div className="no-print fixed inset-0 z-50 lg:hidden">
          <div onClick={() => setMobileMenuOpen(false)} className="drawer-backdrop fixed inset-0" aria-hidden />
          <div className="fixed inset-y-0 left-0 z-50 flex w-full max-w-xs flex-col justify-between bg-white shadow-2xl">
            <div>
              <div className="flex items-center justify-between bg-brand-primary p-4 text-white">
                <div className="flex items-center space-x-2.5">
                  <FlaskConical className="h-5 w-5" aria-hidden />
                  <span className="text-sm font-bold uppercase tracking-tight">ApexPath Menu</span>
                </div>
                <button
                  type="button"
                  onClick={() => setMobileMenuOpen(false)}
                  className="rounded p-1 hover:bg-white/10"
                  aria-label="Close navigation menu"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>
              <nav className="space-y-1 p-3" aria-label="Main">
                {items.map(item => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    onClick={() => setMobileMenuOpen(false)}
                    className={({ isActive }) =>
                      `flex w-full items-center justify-between rounded-lg px-3 py-2.5 text-xs font-semibold transition-colors ${
                        isActive ? 'bg-brand-primary text-white shadow-sm' : 'text-slate-700 hover:bg-slate-100'
                      }`
                    }
                  >
                    <span className="flex items-center space-x-2.5">
                      <item.icon className="h-4 w-4" />
                      <span>{item.label}</span>
                    </span>
                    {item.shortcut && <kbd className="rounded bg-slate-200 px-1.5 py-0.5 font-mono text-[10px] text-slate-600">{item.shortcut}</kbd>}
                  </NavLink>
                ))}
              </nav>
            </div>
            <div className="space-y-2 border-t border-brand-grid/80 bg-slate-50 p-4 text-[11px] text-slate-500">
              <div className="flex items-center justify-between">
                <span>Signed in as</span>
                <span className="font-bold text-slate-800">{user?.username}</span>
              </div>
              <div className="flex items-center justify-between">
                <span>Silent spooler</span>
                <span className={`font-bold ${connected ? 'text-emerald-600' : 'text-amber-600'}`}>
                  {connected ? 'Active (WS)' : 'Reconnecting…'}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

const UserMenu: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => event.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  if (!user) return null;
  const initials = user.fullName
    .split(/\s+/)
    .filter(part => /^[A-Za-z]/.test(part) && !/^(dr|mr|mrs|ms)\.?$/i.test(part))
    .slice(0, 2)
    .map(part => part[0].toUpperCase())
    .join('');

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen(value => !value)}
        className="flex items-center space-x-1.5 rounded-full border border-white/20 bg-white/10 py-0.5 pl-0.5 pr-2 hover:bg-white/20"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Account menu for ${user.fullName}`}
      >
        <span className="flex h-7 w-7 items-center justify-center rounded-full bg-white text-[11px] font-bold text-brand-primary">
          {initials || user.username.slice(0, 2).toUpperCase()}
        </span>
        <ChevronDown className="h-3.5 w-3.5 text-white/80" aria-hidden />
      </button>

      {open && (
        <div role="menu" className="absolute right-0 mt-2 w-64 overflow-hidden rounded-xl border border-brand-grid bg-white text-slate-800 shadow-xl">
          <div className="border-b border-brand-grid bg-slate-50 px-4 py-3">
            <p className="text-sm font-bold text-slate-900">{user.fullName}</p>
            <p className="font-mono text-[11px] text-slate-500">{user.username}</p>
            <div className="mt-2 flex flex-wrap gap-1">
              {user.roles.map(role => {
                const name = normalizeRole(role) as Role;
                return (
                  <span key={role} className="rounded-full border border-brand-primary/20 bg-brand-primary/10 px-2 py-0.5 text-[10px] font-bold text-brand-primary">
                    {ROLE_LABELS[name] ?? name}
                  </span>
                );
              })}
            </div>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              navigate('/account/password');
            }}
            className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-xs font-semibold hover:bg-slate-50"
          >
            <KeyRound className="h-3.5 w-3.5 text-slate-500" aria-hidden />
            Change password
          </button>
          <button
            type="button"
            role="menuitem"
            onClick={() => logout('manual')}
            className="flex w-full items-center gap-2 border-t border-brand-grid px-4 py-2.5 text-left text-xs font-semibold text-brand-panic hover:bg-red-50"
          >
            <LogOut className="h-3.5 w-3.5" aria-hidden />
            Sign out
          </button>
        </div>
      )}
    </div>
  );
};
