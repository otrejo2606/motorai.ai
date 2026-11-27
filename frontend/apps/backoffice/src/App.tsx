import type { ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from 'react-oidc-context';
import { AppShell, AuthButtons, Loading, RequireAuth, RequireRole, useRoles, type NavItem } from '@motoria/ui';
import { ADMIN_ROLES, STAFF_ROLES, oidcSettings } from './config';
import { RegistryReviewsPage } from './pages/RegistryReviewsPage';
import { RegistryReviewDetailPage } from './pages/RegistryReviewDetailPage';
import { RegistryRequestsPage } from './pages/RegistryRequestsPage';
import { IdentityVerificationsPage } from './pages/IdentityVerificationsPage';
import { PersonRegistryPage } from './pages/PersonRegistryPage';
import { ListingsQueuePage } from './pages/ListingsQueuePage';
import { PhotoProtocolPage } from './pages/PhotoProtocolPage';

function Staff({ children }: { children: ReactNode }) {
  return (
    <RequireRole anyOf={STAFF_ROLES} rolesClaim={oidcSettings.rolesClaim}>
      {children}
    </RequireRole>
  );
}

export function App() {
  const { t } = useTranslation();
  const auth = useAuth();
  const roles = useRoles(oidcSettings.rolesClaim);
  const isAdmin = ADMIN_ROLES.some((role) => roles.includes(role));

  const nav: NavItem[] = [];
  if (STAFF_ROLES.some((role) => roles.includes(role))) {
    nav.push(
      { to: '/registry-reviews', label: t('backoffice.nav.registryReviews') },
      { to: '/registry-requests', label: t('backoffice.nav.registryRequests') },
      { to: '/identity-verifications', label: t('backoffice.nav.identity') },
      { to: '/person-registry', label: t('backoffice.nav.personRegistry') },
      { to: '/listings', label: t('backoffice.nav.listings') },
    );
  }
  if (isAdmin) {
    nav.push({ to: '/photo-protocol', label: t('backoffice.nav.photoProtocol') });
  }

  return (
    <AppShell brand={t('app.backofficeName')} nav={nav} actions={<AuthButtons />}>
      <Routes>
        <Route path="/" element={<Navigate to="/registry-reviews" replace />} />
        <Route
          path="/callback"
          element={
            auth.error ? (
              <RequireAuth>
                <Navigate to="/" replace />
              </RequireAuth>
            ) : auth.isAuthenticated ? (
              <Navigate to="/registry-reviews" replace />
            ) : (
              <Loading />
            )
          }
        />
        <Route path="/registry-reviews" element={<Staff><RegistryReviewsPage /></Staff>} />
        <Route path="/registry-reviews/:vehicleId" element={<Staff><RegistryReviewDetailPage /></Staff>} />
        <Route path="/registry-requests" element={<Staff><RegistryRequestsPage /></Staff>} />
        <Route path="/identity-verifications" element={<Staff><IdentityVerificationsPage /></Staff>} />
        <Route path="/person-registry" element={<Staff><PersonRegistryPage /></Staff>} />
        <Route path="/listings" element={<Staff><ListingsQueuePage /></Staff>} />
        <Route
          path="/photo-protocol"
          element={
            <RequireRole anyOf={ADMIN_ROLES} rolesClaim={oidcSettings.rolesClaim}>
              <PhotoProtocolPage />
            </RequireRole>
          }
        />
        <Route path="*" element={<Navigate to="/registry-reviews" replace />} />
      </Routes>
    </AppShell>
  );
}
