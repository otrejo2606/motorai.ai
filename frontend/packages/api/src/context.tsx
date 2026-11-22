import { createContext, useContext, useMemo, useRef, type ReactNode } from 'react';
import { ApiClient } from './client';
import { DEFAULT_UPLOAD_LIMITS, type UploadLimits } from './upload';

interface ApiContextValue {
  client: ApiClient;
  uploadLimits: UploadLimits;
}

const ApiContext = createContext<ApiContextValue | null>(null);

export interface ApiProviderProps {
  baseUrl: string;
  /** Token de acceso en memoria (react-oidc-context); nunca se lee de localStorage. */
  accessToken: string | undefined;
  uploadLimits?: UploadLimits;
  children: ReactNode;
}

export function ApiProvider({ baseUrl, accessToken, uploadLimits, children }: ApiProviderProps) {
  const tokenRef = useRef<string | undefined>(accessToken);
  tokenRef.current = accessToken;

  const value = useMemo<ApiContextValue>(
    () => ({
      client: new ApiClient({ baseUrl, getAccessToken: () => tokenRef.current }),
      uploadLimits: uploadLimits ?? DEFAULT_UPLOAD_LIMITS,
    }),
    [baseUrl, uploadLimits],
  );

  return <ApiContext.Provider value={value}>{children}</ApiContext.Provider>;
}

function useApiContext(): ApiContextValue {
  const value = useContext(ApiContext);
  if (!value) {
    throw new Error('ApiProvider is missing');
  }
  return value;
}

export function useApiClient(): ApiClient {
  return useApiContext().client;
}

export function useUploadLimits(): UploadLimits {
  return useApiContext().uploadLimits;
}
