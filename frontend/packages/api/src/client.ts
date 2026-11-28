import type { ErrorResponse } from './types';

/** Error de API con el codigo estable del backend (ErrorResponse.code) para traducirlo con errors.CODIGO. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  /** Codigos adicionales (reglas de publicacion y tomas de foto que faltan). */
  readonly details: string[];
  readonly violations: NonNullable<ErrorResponse['violations']>;

  constructor(
    status: number,
    code: string,
    message?: string,
    details?: ErrorResponse['details'],
    violations?: ErrorResponse['violations'],
  ) {
    super(message ?? code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = Array.isArray(details) ? details.filter((item) => typeof item === 'string') : [];
    this.violations = Array.isArray(violations) ? violations : [];
  }
}

export type QueryValue = string | number | boolean | null | undefined;

export interface ApiClientConfig {
  /** URL base del backend, desde VITE_API_BASE_URL. */
  baseUrl: string;
  /** Devuelve el token de acceso vigente, que vive solo en memoria. */
  getAccessToken: () => string | undefined;
}

interface RequestOptions {
  query?: Record<string, QueryValue>;
  body?: unknown;
}

function statusToCode(status: number): string {
  if (status === 400) return 'VALIDATION_ERROR';
  if (status === 401) return 'UNAUTHORIZED';
  if (status === 403) return 'FORBIDDEN';
  if (status === 404) return 'NOT_FOUND';
  if (status === 409) return 'CONFLICT';
  if (status === 413) return 'MEDIA_TOO_LARGE';
  if (status === 429) return 'RATE_LIMIT_EXCEEDED';
  if (status >= 500) return 'INTERNAL_ERROR';
  return 'UNKNOWN';
}

/** Codifica un segmento de ruta para que un id nunca altere la ruta. */
export function seg(value: string | number): string {
  return encodeURIComponent(String(value));
}

export function buildQuery(query: Record<string, QueryValue> | undefined): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    params.append(key, String(value));
  }
  const text = params.toString();
  return text ? '?' + text : '';
}

export class ApiClient {
  private readonly baseUrl: string;
  private readonly getAccessToken: () => string | undefined;

  constructor(config: ApiClientConfig) {
    this.baseUrl = config.baseUrl.replace(/[/]+$/, '');
    this.getAccessToken = config.getAccessToken;
  }

  get<T>(path: string, query?: Record<string, QueryValue>): Promise<T> {
    return this.request<T>('GET', path, { query });
  }

  post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('POST', path, { body });
  }

  put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('PUT', path, { body });
  }

  patch<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>('PATCH', path, { body });
  }

  delete<T>(path: string): Promise<T> {
    return this.request<T>('DELETE', path, {});
  }

  async request<T>(method: string, path: string, options: RequestOptions): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    const token = this.getAccessToken();
    if (token) {
      headers['Authorization'] = 'Bearer ' + token;
    }
    let body: string | undefined;
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(options.body);
    }

    let response: Response;
    try {
      response = await fetch(this.baseUrl + path + buildQuery(options.query), {
        method,
        headers,
        body,
        credentials: 'omit',
        cache: 'no-store',
      });
    } catch {
      throw new ApiError(0, 'NETWORK_ERROR');
    }

    if (!response.ok) {
      let payload: Partial<ErrorResponse> | undefined;
      try {
        payload = (await response.json()) as Partial<ErrorResponse>;
      } catch {
        payload = undefined;
      }
      const code = payload && typeof payload.code === 'string' && payload.code ? payload.code : statusToCode(response.status);
      throw new ApiError(response.status, code, payload?.message, payload?.details, payload?.violations);
    }

    if (response.status === 204) {
      return undefined as T;
    }
    const text = await response.text();
    if (!text) {
      return undefined as T;
    }
    return JSON.parse(text) as T;
  }
}
