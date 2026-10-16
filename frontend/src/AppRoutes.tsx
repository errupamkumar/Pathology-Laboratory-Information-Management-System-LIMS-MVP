import React, { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { RequireAuth, RequirePermission } from './auth/guards';
import { AppLayout } from './components/layout/AppLayout';
import { PageSkeleton } from './components/ui/Skeleton';
import { PrintAgentProvider } from './hooks/PrintAgentContext';
import { NotFoundPage } from './pages/NotFoundPage';

// Route-level code splitting: each workflow screen is its own chunk
const LoginPage = lazy(() => import('./pages/LoginPage'));
const VerifyReportPage = lazy(() => import('./pages/VerifyReportPage'));
const RegistrationPage = lazy(() => import('./pages/RegistrationPage'));
const AccessioningPage = lazy(() => import('./pages/AccessioningPage'));
const ResultEntryPage = lazy(() => import('./pages/ResultEntryPage'));
const ReportPage = lazy(() => import('./pages/ReportPage'));
const SpoolerPage = lazy(() => import('./pages/SpoolerPage'));
const ChangePasswordPage = lazy(() => import('./pages/ChangePasswordPage'));
const AdminUsersPage = lazy(() => import('./pages/admin/AdminUsersPage'));
const AuditLogPage = lazy(() => import('./pages/admin/AuditLogPage'));

/** Opt in to React Router v7 behaviour now so the upgrade is a no-op. */
export const ROUTER_FUTURE = { v7_startTransition: true, v7_relativeSplatPath: true } as const;

/** Lands each role on the screen it uses most. */
export const HomeRedirect: React.FC = () => {
  const { can } = useAuth();
  let target = '/account/password';
  if (can('register')) target = '/register';
  else if (can('accession')) target = '/accessioning';
  else if (can('enterResults')) target = '/results';
  else if (can('viewClinical')) target = '/reports';
  return <Navigate to={target} replace />;
};

export const AppRoutes: React.FC = () => (
  <Suspense fallback={<PageSkeleton />}>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/verify/:token" element={<VerifyReportPage />} />

      <Route
        element={
          <RequireAuth>
            <PrintAgentProvider>
              <AppLayout />
            </PrintAgentProvider>
          </RequireAuth>
        }
      >
        <Route index element={<HomeRedirect />} />
        <Route
          path="register"
          element={
            <RequirePermission permission="register">
              <RegistrationPage />
            </RequirePermission>
          }
        />
        <Route
          path="accessioning"
          element={
            <RequirePermission permission="viewClinical">
              <AccessioningPage />
            </RequirePermission>
          }
        />
        <Route
          path="results/:orderId?"
          element={
            <RequirePermission permission="viewClinical">
              <ResultEntryPage />
            </RequirePermission>
          }
        />
        <Route
          path="reports/:orderId?"
          element={
            <RequirePermission permission="viewClinical">
              <ReportPage />
            </RequirePermission>
          }
        />
        <Route
          path="spooler"
          element={
            <RequirePermission permission="viewClinical">
              <SpoolerPage />
            </RequirePermission>
          }
        />
        <Route path="account/password" element={<ChangePasswordPage />} />
        <Route path="admin" element={<Navigate to="/admin/users" replace />} />
        <Route
          path="admin/users"
          element={
            <RequirePermission permission="admin">
              <AdminUsersPage />
            </RequirePermission>
          }
        />
        <Route
          path="admin/audit"
          element={
            <RequirePermission permission="admin">
              <AuditLogPage />
            </RequirePermission>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  </Suspense>
);
