import React from 'react';

interface FieldProps {
  label: React.ReactNode;
  htmlFor: string;
  error?: string;
  hint?: React.ReactNode;
  required?: boolean;
  className?: string;
  children: React.ReactNode;
}

/** Label + control + inline validation message. Controls should set `aria-describedby={errorId(htmlFor)}`. */
export const Field: React.FC<FieldProps> = ({ label, htmlFor, error, hint, required, className = '', children }) => (
  <div className={className}>
    <label htmlFor={htmlFor} className="mb-1 block text-xs font-semibold text-slate-700">
      {label}
      {required && (
        <span className="text-brand-panic" aria-hidden>
          {' '}*
        </span>
      )}
    </label>
    {children}
    {error ? (
      <p id={errorId(htmlFor)} className="mt-1 text-[11px] font-medium text-brand-panic" role="alert">
        {error}
      </p>
    ) : hint ? (
      <p className="mt-1 text-[10px] text-slate-400">{hint}</p>
    ) : null}
  </div>
);

export const errorId = (fieldId: string) => `${fieldId}-error`;

/** Shared input styling; red outline when the field has a validation error. */
export function inputClass(hasError: boolean, extra = ''): string {
  return [
    'w-full rounded-lg border bg-white px-2.5 py-2 text-xs outline-none transition-colors',
    'focus:ring-1 disabled:bg-slate-50 disabled:text-slate-500 read-only:bg-slate-50',
    hasError
      ? 'border-brand-panic focus:border-brand-panic focus:ring-brand-panic/40'
      : 'border-brand-grid focus:border-brand-primary focus:ring-brand-primary',
    extra,
  ].join(' ');
}

/** Props that wire a control to its Field error for screen readers. */
export function a11yProps(fieldId: string, error?: string) {
  return {
    id: fieldId,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? errorId(fieldId) : undefined,
  } as const;
}
