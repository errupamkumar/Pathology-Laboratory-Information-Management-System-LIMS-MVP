import { request } from '../lib/http';
import type {
  AuditLogEntry,
  Authorization,
  BookOrderRequest,
  CreateUserRequest,
  DiagnosticOrder,
  LoginResponse,
  OrderWithResults,
  PageResponse,
  Patient,
  PrintJob,
  RejectionReason,
  ReportVerification,
  ResultInput,
  SampleStatus,
  SpecimenSample,
  SpoolerStatus,
  StaffUser,
  TestCatalog,
  TestResult,
  UserProfile,
} from '../types';

export interface PageRequest {
  page?: number;
  size?: number;
  /** Spring Data sort expression, e.g. `createdAt,desc`. */
  sort?: string;
}

const enc = encodeURIComponent;

// The catalog is reference data shared by registration, result entry and the report preview;
// one in-flight/settled request per session is enough (the server revalidates with ETags anyway).
let catalogCache: Promise<TestCatalog[]> | null = null;

export const api = {
  // ---- Authentication ----
  login(username: string, password: string): Promise<LoginResponse> {
    return request('/auth/login', { method: 'POST', body: { username, password }, auth: false });
  },

  me(): Promise<UserProfile> {
    return request('/auth/me');
  },

  changePassword(currentPassword: string, newPassword: string): Promise<void> {
    return request('/auth/change-password', { method: 'POST', body: { currentPassword, newPassword } });
  },

  // ---- Catalog ----
  getCatalogTests(): Promise<TestCatalog[]> {
    if (!catalogCache) {
      catalogCache = request<TestCatalog[]>('/catalog/tests').catch(error => {
        catalogCache = null;
        throw error;
      });
    }
    return catalogCache;
  },

  clearCatalogCache(): void {
    catalogCache = null;
  },

  // ---- Patients ----
  searchPatients(query: string | undefined, page: PageRequest = {}): Promise<PageResponse<Patient>> {
    return request('/patients/search', { query: { query, ...page } });
  },

  /** Duplicate-phone check; resolves `null` when no patient owns the number (204). */
  async lookupPatientByPhone(phone: string, signal?: AbortSignal): Promise<Patient | null> {
    const patient = await request<Patient | undefined>(`/patients/phone/${enc(phone)}`, { signal });
    return patient ?? null;
  },

  // ---- Orders & billing ----
  registerAndBookOrder(payload: BookOrderRequest): Promise<DiagnosticOrder> {
    return request('/orders/book', { method: 'POST', body: payload });
  },

  getOrders(page: PageRequest = {}): Promise<PageResponse<DiagnosticOrder>> {
    return request('/orders', { query: { ...page } });
  },

  getOrderById(orderId: string): Promise<DiagnosticOrder> {
    return request(`/orders/${enc(orderId)}`);
  },

  getOrderByAccession(accessionNumber: string): Promise<DiagnosticOrder> {
    return request(`/orders/accession/${enc(accessionNumber)}`);
  },

  // ---- Accessioning ----
  getSamples(status: SampleStatus | undefined, page: PageRequest = {}): Promise<PageResponse<SpecimenSample>> {
    return request('/accessioning/samples', { query: { status, ...page } });
  },

  getSamplesByOrder(orderId: string): Promise<SpecimenSample[]> {
    return request(`/accessioning/samples/order/${enc(orderId)}`);
  },

  receiveSample(barcode: string): Promise<SpecimenSample> {
    return request('/accessioning/scan-receive', { method: 'POST', body: { barcode } });
  },

  rejectSample(barcode: string, rejectionReason: RejectionReason, rejectionNotes?: string): Promise<SpecimenSample> {
    return request('/accessioning/reject', {
      method: 'POST',
      body: { barcode, rejectionReason, rejectionNotes: rejectionNotes || null },
    });
  },

  // ---- Diagnostics & sign-off ----
  getOrderResults(orderId: string): Promise<OrderWithResults> {
    return request(`/diagnostics/results/order/${enc(orderId)}`);
  },

  saveBatchResults(orderId: string, results: ResultInput[]): Promise<TestResult[]> {
    return request('/diagnostics/results/batch', { method: 'POST', body: { orderId, results } });
  },

  authorizeOrder(orderId: string, pathologistName: string, licenseNumber: string): Promise<Authorization> {
    return request('/diagnostics/authorize', { method: 'POST', body: { orderId, pathologistName, licenseNumber } });
  },

  /** Public authenticity check behind the report QR code; no session required. */
  verifyReport(token: string): Promise<ReportVerification> {
    return request(`/diagnostics/verify/${enc(token)}`, { auth: false });
  },

  // ---- Printing & silent spooler ----
  getPrintJobs(): Promise<PrintJob[]> {
    return request('/printing/jobs');
  },

  getSpoolerStatus(): Promise<SpoolerStatus> {
    return request('/printing/status');
  },

  spoolLaserReport(orderId: string): Promise<PrintJob> {
    return request(`/printing/spool/report/${enc(orderId)}`, { method: 'POST' });
  },

  /** The PDF endpoint needs the bearer token, so it is fetched as a blob rather than linked directly. */
  getReportPdf(orderId: string): Promise<Blob> {
    return request(`/printing/pdf/${enc(orderId)}`, { responseType: 'blob' });
  },

  // ---- Administration (ADMIN only) ----
  listUsers(page: PageRequest = {}): Promise<PageResponse<StaffUser>> {
    return request('/admin/users', { query: { ...page } });
  },

  createUser(payload: CreateUserRequest): Promise<StaffUser> {
    return request('/admin/users', { method: 'POST', body: payload });
  },

  updateUserRoles(userId: string, roles: string[]): Promise<StaffUser> {
    return request(`/admin/users/${enc(userId)}/roles`, { method: 'PUT', body: { roles } });
  },

  setUserEnabled(userId: string, enabled: boolean): Promise<StaffUser> {
    return request(`/admin/users/${enc(userId)}/status`, { method: 'PATCH', body: { enabled } });
  },

  unlockUser(userId: string): Promise<StaffUser> {
    return request(`/admin/users/${enc(userId)}/unlock`, { method: 'POST' });
  },

  resetUserPassword(userId: string, newPassword: string): Promise<void> {
    return request(`/admin/users/${enc(userId)}/reset-password`, { method: 'POST', body: { newPassword } });
  },

  getAuditLogs(
    filter: { entityName?: string; entityId?: string },
    page: PageRequest = {},
  ): Promise<PageResponse<AuditLogEntry>> {
    return request('/admin/audit-logs', { query: { ...filter, ...page } });
  },
};
