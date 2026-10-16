import React from 'react';
import { AppRoutes } from './AppRoutes';
import { AuthProvider } from './auth/AuthContext';
import { ErrorBoundary } from './components/ui/ErrorBoundary';
import { ToastProvider } from './components/ui/Toaster';

/** Provider tree shared by the browser entry point and the tests (which supply their own router). */
export const AppProviders: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ErrorBoundary variant="app">
    <ToastProvider>
      <AuthProvider>{children}</AuthProvider>
    </ToastProvider>
  </ErrorBoundary>
);

export const App: React.FC = () => (
  <AppProviders>
    <AppRoutes />
  </AppProviders>
);

export default App;
