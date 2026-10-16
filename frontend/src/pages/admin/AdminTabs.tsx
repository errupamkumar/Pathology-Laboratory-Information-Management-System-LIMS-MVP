import React from 'react';
import { NavLink } from 'react-router-dom';
import { History, ShieldCheck, Users } from 'lucide-react';

export const AdminHeader: React.FC<{ actions?: React.ReactNode }> = ({ actions }) => (
  <div className="uiverse-card flex flex-col justify-between gap-4 rounded-xl border border-brand-grid/80 bg-white p-4 shadow-sm sm:p-5 md:flex-row md:items-center">
    <div className="flex items-center space-x-3">
      <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary/10">
        <ShieldCheck className="h-5 w-5 text-brand-primary" aria-hidden />
      </div>
      <div>
        <h1 className="text-base font-bold tracking-tight text-slate-900 sm:text-lg">Administration</h1>
        <p className="text-xs text-slate-500">Staff accounts, role-based access and the immutable audit trail.</p>
      </div>
    </div>
    <div className="flex flex-wrap items-center gap-2">
      <nav className="flex rounded-lg border border-brand-grid bg-slate-50 p-0.5" aria-label="Administration">
        {[
          { to: '/admin/users', label: 'Staff users', icon: Users },
          { to: '/admin/audit', label: 'Audit trail', icon: History },
        ].map(tab => (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={({ isActive }) =>
              `flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-bold ${isActive ? 'bg-white text-brand-primary shadow-sm' : 'text-slate-500 hover:text-slate-800'}`
            }
          >
            <tab.icon className="h-3.5 w-3.5" aria-hidden />
            {tab.label}
          </NavLink>
        ))}
      </nav>
      {actions}
    </div>
  </div>
);
