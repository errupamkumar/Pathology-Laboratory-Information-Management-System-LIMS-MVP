import React from 'react';
import { AlertOctagon, RotateCcw } from 'lucide-react';

interface ErrorBoundaryProps {
  children: React.ReactNode;
  /** When this value changes (e.g. the route path), a captured error is cleared. */
  resetKey?: unknown;
  /** `page` renders inside the app shell; `app` replaces the whole screen. */
  variant?: 'page' | 'app';
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * Contains render-time crashes so one broken screen cannot blank the whole LIMS. Data-loading
 * failures are not exceptions here: pages report those through toasts and inline states.
 */
export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo): void {
    console.error('[ErrorBoundary] Unhandled render error', error, info.componentStack);
  }

  componentDidUpdate(prevProps: ErrorBoundaryProps): void {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  private reset = () => this.setState({ error: null });

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    const fullScreen = this.props.variant === 'app';
    return (
      <div className={`flex items-center justify-center p-6 ${fullScreen ? 'min-h-screen bg-brand-surface' : 'py-16'}`}>
        <div role="alert" className="max-w-md w-full rounded-xl border border-red-200 bg-white p-6 text-center shadow-sm">
          <div className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-full bg-red-50">
            <AlertOctagon className="h-6 w-6 text-brand-panic" aria-hidden />
          </div>
          <h2 className="text-base font-bold text-slate-900">This screen ran into a problem</h2>
          <p className="mt-1 text-xs text-slate-500">
            No data was changed. Try again, or reload the application if the problem persists.
          </p>
          {import.meta.env.DEV && (
            <pre className="mt-3 max-h-32 overflow-auto rounded-lg bg-slate-900 p-2 text-left text-[10px] text-red-300 select-text">
              {error.message}
            </pre>
          )}
          <div className="mt-4 flex justify-center gap-2">
            <button
              type="button"
              onClick={this.reset}
              className="flex items-center gap-1.5 rounded-lg border border-brand-grid px-3 py-2 text-xs font-bold text-slate-700 hover:bg-slate-50"
            >
              <RotateCcw className="h-3.5 w-3.5" aria-hidden />
              Try again
            </button>
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="uiverse-btn-primary rounded-lg px-3 py-2 text-xs font-bold text-white"
            >
              Reload application
            </button>
          </div>
        </div>
      </div>
    );
  }
}
