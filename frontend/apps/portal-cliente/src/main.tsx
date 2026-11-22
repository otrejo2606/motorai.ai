import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuth } from 'react-oidc-context';
import { ApiError, ApiProvider } from '@motoria/api';
import { initI18n } from '@motoria/i18n';
import { MotoriaAuthProvider } from '@motoria/ui';
import '@motoria/ui/styles.css';
import { App } from './App';
import { appConfig, oidcSettings, uploadLimits } from './config';

initI18n(appConfig.defaultLanguage);

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
        return failureCount < 2;
      },
    },
  },
});

function ApiRoot() {
  const auth = useAuth();
  return (
    <ApiProvider baseUrl={appConfig.apiBaseUrl} accessToken={auth.user?.access_token} uploadLimits={uploadLimits}>
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </ApiProvider>
  );
}

const container = document.getElementById('root');
if (container) {
  createRoot(container).render(
    <StrictMode>
      <BrowserRouter>
        <MotoriaAuthProvider settings={oidcSettings}>
          <ApiRoot />
        </MotoriaAuthProvider>
      </BrowserRouter>
    </StrictMode>,
  );
}
