import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, CheckCircle2, Info, ShieldAlert, X } from 'lucide-react';
import { ApiError, errorMessage } from '../../lib/apiError';
import { isAbortError } from '../../lib/http';

export type ToastTone = 'success' | 'error' | 'info' | 'warning';

interface ToastItem {
  id: number;
  tone: ToastTone;
  title: string;
  description?: string;
  durationMs: number;
}

interface ToastOptions {
  description?: string;
  durationMs?: number;
}

interface ToastApi {
  show: (tone: ToastTone, title: string, options?: ToastOptions) => number;
  success: (title: string, description?: string) => number;
  info: (title: string, description?: string) => number;
  warning: (title: string, description?: string) => number;
  error: (title: string, description?: string) => number;
  /** Uniform reporting of failed API calls (RFC 7807 title, detail and correlation reference). */
  apiError: (error: unknown, fallbackTitle?: string) => void;
  dismiss: (id: number) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

const DEFAULT_DURATION: Record<ToastTone, number> = {
  success: 4000,
  info: 5000,
  warning: 7000,
  error: 8000,
};

const MAX_VISIBLE = 5;

const TONE_STYLES: Record<ToastTone, { container: string; icon: React.ReactNode }> = {
  success: {
    container: 'border-emerald-300 bg-emerald-50 text-emerald-950',
    icon: <CheckCircle2 className="w-4 h-4 text-emerald-600" aria-hidden />,
  },
  info: {
    container: 'border-brand-secondary/40 bg-white text-slate-900',
    icon: <Info className="w-4 h-4 text-brand-primary" aria-hidden />,
  },
  warning: {
    container: 'border-amber-300 bg-amber-50 text-amber-950',
    icon: <AlertTriangle className="w-4 h-4 text-amber-600" aria-hidden />,
  },
  error: {
    container: 'border-red-300 bg-red-50 text-red-950',
    icon: <ShieldAlert className="w-4 h-4 text-brand-panic" aria-hidden />,
  },
};

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts(current => current.filter(t => t.id !== id));
  }, []);

  const show = useCallback((tone: ToastTone, title: string, options: ToastOptions = {}) => {
    const id = nextId.current++;
    const item: ToastItem = {
      id,
      tone,
      title,
      description: options.description,
      durationMs: options.durationMs ?? DEFAULT_DURATION[tone],
    };
    setToasts(current => [...current, item].slice(-MAX_VISIBLE));
    return id;
  }, []);

  const api = useMemo<ToastApi>(
    () => ({
      show,
      dismiss,
      success: (title, description) => show('success', title, { description }),
      info: (title, description) => show('info', title, { description }),
      warning: (title, description) => show('warning', title, { description }),
      error: (title, description) => show('error', title, { description }),
      apiError: (error, fallbackTitle = 'Request failed') => {
        if (isAbortError(error)) return;
        // An expired session is handled by the sign-in redirect; a toast per failed call would be noise
        if (error instanceof ApiError && error.status === 401) return;
        const title = error instanceof ApiError ? error.title : fallbackTitle;
        let description = errorMessage(error, fallbackTitle);
        if (error instanceof ApiError && error.correlationId) {
          description += ` (Ref: ${error.correlationId})`;
        }
        show('error', title, { description });
      },
    }),
    [show, dismiss],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        className="no-print fixed bottom-14 right-4 z-[60] flex w-[calc(100%-2rem)] max-w-sm flex-col gap-2"
        aria-live="polite"
      >
        {toasts.map(toast => (
          <ToastCard key={toast.id} toast={toast} onDismiss={dismiss} />
        ))}
      </div>
    </ToastContext.Provider>
  );
};

const ToastCard: React.FC<{ toast: ToastItem; onDismiss: (id: number) => void }> = ({ toast, onDismiss }) => {
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (paused || toast.durationMs <= 0) return;
    const timer = window.setTimeout(() => onDismiss(toast.id), toast.durationMs);
    return () => window.clearTimeout(timer);
  }, [paused, toast.id, toast.durationMs, onDismiss]);

  const style = TONE_STYLES[toast.tone];
  return (
    <div
      role={toast.tone === 'error' ? 'alert' : 'status'}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      className={`animate-toast-in flex items-start gap-3 rounded-lg border p-3 text-xs shadow-lg ${style.container}`}
    >
      <span className="mt-0.5 shrink-0">{style.icon}</span>
      <div className="min-w-0 flex-1">
        <p className="font-bold">{toast.title}</p>
        {toast.description && <p className="mt-0.5 break-words opacity-80">{toast.description}</p>}
      </div>
      <button
        type="button"
        onClick={() => onDismiss(toast.id)}
        className="shrink-0 rounded p-0.5 opacity-60 hover:opacity-100"
        aria-label="Dismiss notification"
      >
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
  );
};

export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside <ToastProvider>');
  }
  return context;
}
