/**
 * RFC 7807 problem body as produced by the backend's GlobalExceptionHandler and
 * ProblemDetailSecurityHandler.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  correlationId?: string;
  invalidFields?: Record<string, string>;
}

interface ApiErrorInit {
  status: number;
  title: string;
  detail: string;
  invalidFields?: Record<string, string>;
  correlationId?: string;
  retryAfterSeconds?: number;
}

export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly invalidFields: Record<string, string>;
  readonly correlationId?: string;
  readonly retryAfterSeconds?: number;

  constructor(init: ApiErrorInit) {
    super(init.detail);
    this.name = 'ApiError';
    this.status = init.status;
    this.title = init.title;
    this.invalidFields = init.invalidFields ?? {};
    this.correlationId = init.correlationId;
    this.retryAfterSeconds = init.retryAfterSeconds;
  }

  get isNetworkError(): boolean {
    return this.status === 0;
  }

  static fromProblem(status: number, problem: ProblemDetail | null, retryAfterSeconds?: number): ApiError {
    return new ApiError({
      status,
      title: problem?.title || defaultTitle(status),
      detail: problem?.detail || defaultDetail(status, retryAfterSeconds),
      invalidFields: problem?.invalidFields,
      correlationId: problem?.correlationId,
      retryAfterSeconds,
    });
  }
}

function defaultTitle(status: number): string {
  if (status === 401) return 'Authentication Required';
  if (status === 403) return 'Access Denied';
  if (status === 404) return 'Not Found';
  if (status === 409) return 'Conflict';
  if (status === 429) return 'Too Many Requests';
  if (status >= 500) return 'Server Error';
  return 'Request Failed';
}

function defaultDetail(status: number, retryAfterSeconds?: number): string {
  if (status === 429) {
    return retryAfterSeconds
      ? `Too many requests. Please wait ${retryAfterSeconds}s and try again.`
      : 'Too many requests. Please wait a moment and try again.';
  }
  if (status >= 500) return 'The LIMS server encountered an error. Please try again or contact support.';
  return `Request failed with status ${status}.`;
}

/** Human-readable message for any thrown value, preferring field-level validation details. */
export function errorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (error instanceof ApiError) {
    const fields = Object.values(error.invalidFields);
    return fields.length > 0 ? fields.join('; ') : error.message;
  }
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}
