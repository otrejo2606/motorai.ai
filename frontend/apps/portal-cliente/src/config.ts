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

// La tarifa de "que Motoria lo obtenga" (Vehicle.registryRequestFee) y la version de los textos de
// consentimiento (Consent.currentTextVersion) vienen del backend; no se configuran aqui.
export const appConfig = {
  apiBaseUrl: env.VITE_API_BASE_URL ?? '',
  defaultLanguage: env.VITE_DEFAULT_LANGUAGE ?? 'es',
  country: env.VITE_DEFAULT_COUNTRY ?? 'CR',
  currency: env.VITE_CURRENCY === 'USD' ? 'USD' : 'CRC',
  pageSize: 12,
} as const;

export const SELLER_ROLES = ['SELLER', 'DEALER', 'ADMIN'];
