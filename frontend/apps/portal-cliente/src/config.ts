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

/**
 * Captura guiada de fotos (add-captura-guiada-fotos, design D8 y D9). MediaPipe se carga desde un CDN con version fija;
 * VITE_MEDIAPIPE_BASE_URL vacia apaga la deteccion en el dispositivo (queda la silueta con captura manual). La CSP del
 * portal debe permitir estas URL (script-src, connect-src y wasm-unsafe-eval); ver design D10.
 */
const DEFAULT_MEDIAPIPE_BASE_URL = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14';
const DEFAULT_MEDIAPIPE_MODEL_URL =
  'https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float16/1/efficientdet_lite0.tflite';

export const captureConfig = {
  mediaPipe: {
    baseUrl: env.VITE_MEDIAPIPE_BASE_URL ?? DEFAULT_MEDIAPIPE_BASE_URL,
    modelUrl: env.VITE_MEDIAPIPE_MODEL_URL ?? DEFAULT_MEDIAPIPE_MODEL_URL,
  },
  /** La realidad aumentada (WebXR immersive-ar) solo se ofrece con esta bandera y si el navegador la soporta. */
  arEnabled: env.VITE_AR_ENABLED === 'true',
} as const;
