import React from 'react';
import { Link } from 'react-router-dom';
import { Compass } from 'lucide-react';

export const NotFoundPage: React.FC = () => (
  <div className="flex justify-center px-4 py-20">
    <div className="max-w-md rounded-xl border border-brand-grid bg-white p-8 text-center shadow-sm">
      <div className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-full bg-slate-100">
        <Compass className="h-5 w-5 text-slate-500" aria-hidden />
      </div>
      <h1 className="text-base font-bold text-slate-900">Page not found</h1>
      <p className="mt-1 text-xs text-slate-500">The address you opened does not match any LIMS screen.</p>
      <Link to="/" className="uiverse-btn-primary mt-5 inline-block rounded-lg px-4 py-2 text-xs font-bold text-white">
        Back to workspace
      </Link>
    </div>
  </div>
);
