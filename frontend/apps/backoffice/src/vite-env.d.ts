/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_OIDC_AUTHORITY?: string;
  readonly VITE_OIDC_CLIENT_ID?: string;
  readonly VITE_OIDC_REDIRECT_URI?: string;
  readonly VITE_OIDC_POST_LOGOUT_REDIRECT_URI?: string;
  readonly VITE_OIDC_SCOPE?: string;
  readonly VITE_OIDC_ROLES_CLAIM?: string;
  readonly VITE_DEFAULT_COUNTRY?: string;
  readonly VITE_DEFAULT_LANGUAGE?: string;
  readonly VITE_CURRENCY?: string;
  readonly VITE_MAX_DOCUMENT_MB?: string;
  readonly VITE_MAX_PHOTO_MB?: string;
  readonly VITE_CONFIDENCE_WARNING?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
