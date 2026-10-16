import React from 'react';

export const Skeleton: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`animate-pulse rounded bg-slate-200/80 ${className}`} aria-hidden />
);

export const TableSkeleton: React.FC<{ rows?: number; columns?: number }> = ({ rows = 6, columns = 6 }) => (
  <div className="divide-y divide-brand-grid/50" role="status" aria-label="Loading">
    {Array.from({ length: rows }).map((_, row) => (
      <div key={row} className="flex gap-4 px-4 py-3">
        {Array.from({ length: columns }).map((__, col) => (
          <Skeleton key={col} className={`h-3.5 ${col === 0 ? 'w-28' : 'flex-1'}`} />
        ))}
      </div>
    ))}
  </div>
);

export const CardListSkeleton: React.FC<{ items?: number }> = ({ items = 5 }) => (
  <div className="space-y-2.5" role="status" aria-label="Loading">
    {Array.from({ length: items }).map((_, i) => (
      <div key={i} className="rounded-xl border border-brand-grid/60 p-3">
        <Skeleton className="h-3.5 w-2/3" />
        <Skeleton className="mt-2 h-3 w-1/2" />
      </div>
    ))}
  </div>
);

/** Suspense fallback while a lazily loaded route chunk downloads. */
export const PageSkeleton: React.FC = () => (
  <div className="max-w-7xl mx-auto px-4 py-6 space-y-5" role="status" aria-label="Loading page">
    <div className="uiverse-card rounded-xl border border-brand-grid/80 bg-white p-5">
      <Skeleton className="h-5 w-72" />
      <Skeleton className="mt-2 h-3 w-96 max-w-full" />
    </div>
    <div className="rounded-xl border border-brand-grid/80 bg-white">
      <TableSkeleton rows={7} />
    </div>
  </div>
);
