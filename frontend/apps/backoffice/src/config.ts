import type { UploadLimits } from '@motoria/api';
import { DEFAULT_ROLES_CLAIM, type OidcSettings } from '@motoria/ui';

const env = import.meta.env;

function megabytes(value: string | undefined, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed * 1024 * 1024 : fallback * 1024 * 1024;
}

const origin = window.location.origin;

export const oidcSettings: OidcSettings = {
  authority: env.VITE_OIDC_AUTHORITY ?? '',
  clientId: env.VITE_OIDC_CLIENT_ID ?? '',
  redirectUri: env.VITE_OIDC_REDIRECT_URI ?? origin + '/callback',
  postLogoutRedirectUri: env.VITE_OIDC_POST_LOGOUT_REDIRECT_URI ?? origin + '/',
  scope: env.VITE_OIDC_SCOPE ?? 'openid profile email',
  rolesClaim: env.VITE_OIDC_ROLES_CLAIM ?? DEFAULT_ROLES_CLAIM,
};

export const uploadLimits: UploadLimits = {
  maxDocumentBytes: megabytes(env.VITE_MAX_DOCUMENT_MB, 10),
  maxPhotoBytes: megabytes(env.VITE_MAX_PHOTO_MB, 15),
};

const threshold = Number(env.VITE_CONFIDENCE_WARNING);

export const appConfig = {
  apiBaseUrl: env.VITE_API_BASE_URL ?? '',
  defaultLanguage: env.VITE_DEFAULT_LANGUAGE ?? 'es',
  country: env.VITE_DEFAULT_COUNTRY ?? 'CR',
  currency: env.VITE_CURRENCY ?? 'CRC',
  confidenceWarning: Number.isFinite(threshold) && threshold > 0 && threshold <= 1 ? threshold : 0.85,
};

export const STAFF_ROLES = ['BACKOFFICE', 'ADMIN'];
export const ADMIN_ROLES = ['ADMIN'];
