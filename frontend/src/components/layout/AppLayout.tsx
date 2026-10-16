import React, { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { usePrintAgent } from '../../hooks/PrintAgentContext';
import { ErrorBoundary } from '../ui/ErrorBoundary';
import { PageSkeleton } from '../ui/Skeleton';
import { Navbar } from './Navbar';

export const AppLayout: React.FC = () => {
  const location = useLocation();
  const { connected } = usePrintAgent();

  return (
    <div className="flex min-h-screen flex-col bg-brand-surface text-slate-800">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2 focus:text-xs focus:font-bold"
      >
        Skip to content
      </a>
      <Navbar />

      <main id="main-content" className="flex-1">
        {/* Keyed by path: a crash on one screen is cleared by navigating to another */}
        <ErrorBoundary resetKey={location.pathname}>
          <Suspense fallback={<PageSkeleton />}>
            <Outlet />
          </Suspense>
        </ErrorBoundary>
      </main>

      <footer className="no-print flex flex-wrap items-center justify-between gap-2 border-t border-brand-grid bg-white px-4 py-2 text-[11px] text-slate-500">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
          <span className="font-semibold text-slate-700">Hotkeys:</span>
          <span>
            <kbd className="rounded border border-slate-300 bg-slate-100 px-1 py-0.5 font-mono">Alt+N</kbd> New patient
          </span>
          <span>
            <kbd className="rounded border border-slate-300 bg-slate-100 px-1 py-0.5 font-mono">Alt+S</kbd> Save / book
          </span>
          <span>
            <kbd className="rounded border border-slate-300 bg-slate-100 px-1 py-0.5 font-mono">Enter</kbd> Next field
          </span>
        </div>
        <div className="flex items-center space-x-2">
          <span className={`h-2 w-2 rounded-full ${connected ? 'bg-emerald-500' : 'bg-amber-500'}`} />
          <span className="font-medium text-slate-600">
            {connected ? 'Zero-click hardware bridge active' : 'Hardware bridge reconnecting'}
          </span>
        </div>
      </footer>
    </div>
  );
};
