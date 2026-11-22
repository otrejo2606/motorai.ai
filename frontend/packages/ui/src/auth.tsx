import { useMemo, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from 'react-oidc-context';
import { InMemoryWebStorage, WebStorageStateStore, type User } from 'oidc-client-ts';
import { Alert, Card, Loading } from './components/feedback';
import { Button } from './components/forms';

/**
 * Configuracion OIDC de Keycloak (realm motoria), leida de variables VITE_ por cada app.
 * Cliente publico con authorization code y PKCE (oidc-client-ts usa PKCE por defecto).
 */
export interface OidcSettings {
  authority: string;
  clientId: string;
  redirectUri: string;
  postLogoutRedirectUri: string;
  scope: string;
  /** Ruta con puntos del claim de roles en el token; en Keycloak, realm_access.roles. */
  rolesClaim: string;
}

export const DEFAULT_ROLES_CLAIM = 'realm_access.roles';

interface ReturnState {
  returnTo?: string;
}

/** Solo rutas internas, para no abrir una redireccion a otro sitio. */
function safeReturnTo(state: unknown): string {
  const value = (state as ReturnState | undefined)?.returnTo;
  if (typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')) {
    return value;
  }
  return '/';
}

/**
 * Proveedor OIDC. El usuario y su token viven solo en memoria (InMemoryWebStorage):
 * nada se escribe en localStorage. Debe ir dentro del BrowserRouter.
 */
export function MotoriaAuthProvider({ settings, children }: { settings: OidcSettings; children: ReactNode }) {
  const navigate = useNavigate();
  const userStore = useMemo(() => new WebStorageStateStore({ store: new InMemoryWebStorage() }), []);
  return (
    <AuthProvider
      authority={settings.authority}
      client_id={settings.clientId}
      redirect_uri={settings.redirectUri}
      post_logout_redirect_uri={settings.postLogoutRedirectUri}
      scope={settings.scope}
      response_type="code"
      userStore={userStore}
      automaticSilentRenew
      onSigninCallback={(user: User | void) => {
        navigate(safeReturnTo(user ? user.state : undefined), { replace: true });
      }}
    >
      {children}
    </AuthProvider>
  );
}

function normalizeRoles(value: unknown): string[] {
  const raw: unknown[] = Array.isArray(value) ? value : typeof value === 'string' ? value.split(',') : [];
  return raw
    .filter((item): item is string => typeof item === 'string')
    .map((item) => {
      const trimmed = item.trim();
      const slash = trimmed.lastIndexOf('/');
      return (slash >= 0 ? trimmed.slice(slash + 1) : trimmed).toUpperCase();
    })
    .filter(Boolean);
}

function decodeJwtPayload(token: string | undefined): Record<string, unknown> | null {
  if (!token) return null;
  const part = token.split('.')[1];
  if (!part) return null;
  try {
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    return JSON.parse(atob(padded)) as Record<string, unknown>;
  } catch {
    return null;
  }
}

/** Lee un claim por ruta con puntos, por ejemplo realm_access.roles. */
function readClaim(source: Record<string, unknown> | null | undefined, path: string): unknown {
  let current: unknown = source;
  for (const part of path.split('.')) {
    if (current === null || typeof current !== 'object') return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  return current;
}

/**
 * Roles leidos del access token de Keycloak (realm_access.roles) y, si no estan, del id_token.
 * Solo sirven para la interfaz: el backend vuelve a autorizar cada llamada.
 */
export function useRoles(rolesClaim: string): string[] {
  const auth = useAuth();
  const user = auth.user;
  return useMemo(() => {
    if (!user) return [];
    const fromAccessToken = normalizeRoles(readClaim(decodeJwtPayload(user.access_token), rolesClaim));
    if (fromAccessToken.length > 0) return fromAccessToken;
    return normalizeRoles(readClaim(user.profile as Record<string, unknown>, rolesClaim));
  }, [user, rolesClaim]);
}

function currentReturnState(): ReturnState {
  return { returnTo: window.location.pathname + window.location.search };
}

export function useSignIn() {
  const auth = useAuth();
  return () => {
    void auth.signinRedirect({ state: currentReturnState() });
  };
}

/**
 * Registro de usuarios en la pantalla de Keycloak, sin pantalla propia:
 * se usa el parametro estandar OIDC prompt=create (Keycloak lo atiende con su formulario de registro
 * si el realm tiene habilitado el registro de usuarios).
 */
export function useSignUp() {
  const auth = useAuth();
  return () => {
    void auth.signinRedirect({ state: currentReturnState(), prompt: 'create' });
  };
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const { t } = useTranslation();
  const signIn = useSignIn();
  if (auth.isLoading || auth.activeNavigator) {
    return <Loading label={t('auth.checking')} />;
  }
  if (auth.error) {
    return (
      <Alert tone="error" title={t('auth.errorTitle')}>
        <Button onClick={signIn}>{t('auth.signIn')}</Button>
      </Alert>
    );
  }
  if (!auth.isAuthenticated) {
    return (
      <Card title={t('auth.requiredTitle')} actions={<Button onClick={signIn}>{t('auth.signIn')}</Button>}>
        <p>{t('auth.requiredBody')}</p>
      </Card>
    );
  }
  return <>{children}</>;
}

export function RequireRole({ anyOf, rolesClaim, children }: { anyOf: string[]; rolesClaim: string; children: ReactNode }) {
  const roles = useRoles(rolesClaim);
  const { t } = useTranslation();
  return (
    <RequireAuth>
      {anyOf.some((role) => roles.includes(role)) ? (
        children
      ) : (
        <Alert tone="error" title={t('auth.forbiddenTitle')}>
          <p>{t('auth.forbiddenBody')}</p>
        </Alert>
      )}
    </RequireAuth>
  );
}

export function AuthButtons({ allowSignUp = false }: { allowSignUp?: boolean }) {
  const auth = useAuth();
  const { t } = useTranslation();
  const signIn = useSignIn();
  const signUp = useSignUp();
  if (auth.isLoading) return null;
  if (auth.isAuthenticated) {
    return (
      <Button variant="secondary" onClick={() => void auth.signoutRedirect()}>
        {t('auth.signOut')}
      </Button>
    );
  }
  return (
    <>
      <Button onClick={signIn}>{t('auth.signIn')}</Button>
      {allowSignUp ? (
        <Button variant="secondary" onClick={signUp}>
          {t('auth.signUp')}
        </Button>
      ) : null}
    </>
  );
}
