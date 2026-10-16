export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'ALL';
export type PatientGender = Exclude<Gender, 'ALL'>;
export type OrderStatus = 'REGISTERED' | 'COLLECTED' | 'PROCESSING' | 'COMPLETED' | 'AUTHORIZED';
export type SampleStatus = 'COLLECTED' | 'RECEIVED' | 'REJECTED';
export type RejectionReason =
  | 'HEMOLYZED'
  | 'CLOTTED'
  | 'INSUFFICIENT_VOLUME'
  | 'INCORRECT_CONTAINER'
  | 'CONTAMINATED'
  | 'LEAKING_SPECIMEN'
  | 'LABEL_MISMATCH';

/** Stable page envelope returned by every paged list endpoint (backend `PageResponse<T>`). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Patient {
  id: string;
  uhid: string;
  firstName: string;
  lastName: string;
  fullName: string;
  dob: string;
  age: number;
  gender: PatientGender;
  phone: string;
  email?: string | null;
  createdAt: string;
}

export interface ReferenceRange {
  id: string;
  gender: Gender;
  minAge: number;
  maxAge: number;
  normalLow?: number | null;
  normalHigh?: number | null;
  criticalLow?: number | null;
  criticalHigh?: number | null;
  textNormalVal?: string | null;
}

export interface TestParameter {
  id: string;
  parameterName: string;
  unit?: string | null;
  formula?: string | null;
  displayOrder: number;
  referenceRanges: ReferenceRange[];
}

export interface TestCatalog {
  id: string;
  testCode: string;
  testName: string;
  department: string;
  specimenType: string;
  containerType: string;
  price: number;
  tatMinutes: number;
  isActive: boolean;
  parameters: TestParameter[];
}

export interface OrderItem {
  id: string;
  testCatalogId: string;
  testCode: string;
  testName: string;
  price: number;
}

export interface DiagnosticOrder {
  id: string;
  accessionNumber: string;
  patient: Patient;
  status: OrderStatus;
  totalAmount: number;
  paidAmount: number;
  balanceAmount: number;
  referringDoctor?: string | null;
  createdAt: string;
  items: OrderItem[];
}

export interface BookOrderRequest {
  patientId?: string | null;
  uhid?: string | null;
  firstName?: string;
  lastName?: string;
  dob?: string;
  gender?: PatientGender;
  phone?: string;
  email?: string | null;
  testCatalogIds: string[];
  discountAmount: number;
  paidAmount: number;
  referringDoctor?: string | null;
}

export interface SpecimenSample {
  id: string;
  orderId: string;
  accessionNumber: string;
  barcode: string;
  containerType: string;
  specimenType: string;
  status: SampleStatus;
  rejectionReason?: RejectionReason | null;
  rejectionNotes?: string | null;
  collectedAt?: string | null;
  receivedAt?: string | null;
  createdAt: string;
}

export interface TestResult {
  id?: string;
  orderId: string;
  parameterId: string;
  parameterName: string;
  unit?: string | null;
  observedValue: string;
  isCritical?: boolean | null;
  isOutOfRange?: boolean | null;
  requiresReview?: boolean | null;
  deltaChangePercentage?: number | null;
  technicianNotes?: string | null;
  normalLow?: number | null;
  normalHigh?: number | null;
  criticalLow?: number | null;
  criticalHigh?: number | null;
  textNormalVal?: string | null;
  enteredAt?: string | null;
}

export interface ResultInput {
  parameterId: string;
  parameterName: string;
  unit?: string | null;
  observedValue: string;
  technicianNotes?: string | null;
}

export interface AuthorizationInfo {
  pathologistName: string;
  licenseNumber: string;
  digitalSignatureHash: string;
  verificationToken: string;
  authorizedBy?: string | null;
  authorizedAt: string;
}

export interface OrderWithResults {
  orderId: string;
  accessionNumber: string;
  patientUhid: string;
  patientName: string;
  patientAge: number;
  patientGender: PatientGender;
  referringDoctor?: string | null;
  status: OrderStatus;
  results: TestResult[];
  authorization?: AuthorizationInfo | null;
}

export interface Authorization extends AuthorizationInfo {
  orderId: string;
}

export interface ReportVerification {
  valid: boolean;
  verificationToken: string;
  pathologistName: string;
  licenseNumber: string;
  digitalSignatureHash: string;
  authorizedAt: string;
}

export type PrintTarget = 'BARCODE_THERMAL' | 'INVOICE_POS' | 'REPORT_LASER';

export interface PrintJob {
  jobId: string;
  target: PrintTarget;
  contentType: string;
  payload: string;
  accessionNumber: string;
  title: string;
  queuedAt: string;
}

export interface SpoolerStatus {
  activeBridgeAgents: number;
  spooledJobsCount: number;
  status: string;
  endpoint: string;
}

// ---- Authentication & administration ----

export interface UserProfile {
  id: string;
  username: string;
  fullName: string;
  email: string;
  roles: string[];
  lastLoginAt?: string | null;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  /** Token lifetime in seconds. */
  expiresIn: number;
  user: UserProfile;
}

export interface StaffUser {
  id: string;
  username: string;
  email: string;
  fullName: string;
  roles: string[];
  enabled: boolean;
  locked: boolean;
  lockedUntil?: string | null;
  failedLoginAttempts: number;
  lastLoginAt?: string | null;
  createdAt?: string | null;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  fullName: string;
  password: string;
  roles: string[];
}

export interface AuditLogEntry {
  id: string;
  entityName: string;
  entityId: string;
  actionType: string;
  changedBy?: string | null;
  newData?: string | null;
  ipAddress?: string | null;
  timestamp: string;
}
