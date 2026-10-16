import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { PageResponse } from '../../types';

interface PaginationProps {
  page: Pick<PageResponse<unknown>, 'page' | 'size' | 'totalElements' | 'totalPages' | 'first' | 'last'>;
  onPageChange: (page: number) => void;
  disabled?: boolean;
  noun?: string;
}

export const Pagination: React.FC<PaginationProps> = ({ page, onPageChange, disabled = false, noun = 'records' }) => {
  if (page.totalElements === 0) return null;
  const from = page.page * page.size + 1;
  const to = Math.min(page.totalElements, (page.page + 1) * page.size);

  return (
    <nav
      className="flex flex-wrap items-center justify-between gap-2 border-t border-brand-grid bg-slate-50/60 px-4 py-2.5 text-xs text-slate-600"
      aria-label="Pagination"
    >
      <span>
        Showing <strong className="text-slate-900">{from}</strong>–<strong className="text-slate-900">{to}</strong> of{' '}
        <strong className="text-slate-900">{page.totalElements}</strong> {noun}
      </span>
      <div className="flex items-center gap-1.5">
        <button
          type="button"
          onClick={() => onPageChange(page.page - 1)}
          disabled={disabled || page.first}
          className="flex items-center gap-1 rounded-lg border border-brand-grid bg-white px-2.5 py-1 font-semibold hover:bg-slate-100 disabled:opacity-40"
        >
          <ChevronLeft className="h-3.5 w-3.5" aria-hidden />
          Prev
        </button>
        <span className="px-1 font-mono">
          {page.page + 1} / {Math.max(1, page.totalPages)}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(page.page + 1)}
          disabled={disabled || page.last}
          className="flex items-center gap-1 rounded-lg border border-brand-grid bg-white px-2.5 py-1 font-semibold hover:bg-slate-100 disabled:opacity-40"
        >
          Next
          <ChevronRight className="h-3.5 w-3.5" aria-hidden />
        </button>
      </div>
    </nav>
  );
};
