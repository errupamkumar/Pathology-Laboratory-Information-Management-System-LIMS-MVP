import React from 'react';
import { Link } from 'react-router-dom';
import { Lock } from 'lucide-react';

export const ForbiddenPage: React.FC = () => (
  <div className="flex justify-center px-4 py-20">
    <div className="max-w-md rounded-xl border border-brand-grid bg-white p-8 text-center shadow-sm">
      <div className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-full bg-amber-50">
        <Lock className="h-5 w-5 text-amber-600" aria-hidden />
      </div>
      <h1 className="text-base font-bold text-slate-900">Access restricted</h1>
      <p className="mt-1 text-xs text-slate-500">
        Your role does not include access to this screen. Contact a laboratory administrator if you need it.
      </p>
      <Link to="/" className="uiverse-btn-primary mt-5 inline-block rounded-lg px-4 py-2 text-xs font-bold text-white">
        Go to my workspace
      </Link>
    </div>
  </div>
);
