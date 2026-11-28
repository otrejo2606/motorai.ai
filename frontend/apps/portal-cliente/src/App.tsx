import { Navigate, Route, Routes } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from 'react-oidc-context';
import { AppShell, AuthButtons, Loading, RequireAuth, RequireRole, useRoles, type NavItem } from '@motoria/ui';
import { SELLER_ROLES, oidcSettings } from './config';
import { HomePage } from './pages/HomePage';
import { ListingDetailPage } from './pages/ListingDetailPage';
import { MyListingsPage } from './pages/MyListingsPage';
import { ProfilePage } from './pages/ProfilePage';
import { ConsentsPage } from './pages/ConsentsPage';
import { IdentityPage } from './pages/IdentityPage';
import { SellPage } from './pages/sell/SellPage';
import { NotFoundPage } from './pages/NotFoundPage';

export function App() {
  const { t } = useTranslation();
  const auth = useAuth();
  const roles = useRoles(oidcSettings.rolesClaim);
  const isSeller = SELLER_ROLES.some((role) => roles.includes(role));

  const nav: NavItem[] = [{ to: '/', label: t('nav.home'), end: true }];
  if (isSeller) {
    nav.push({ to: '/sell', label: t('nav.sell') }, { to: '/my-listings', label: t('nav.myListings') });
  }
  if (auth.isAuthenticated) {
    nav.push(
      { to: '/profile', label: t('nav.profile') },
      { to: '/identity', label: t('nav.identity') },
      { to: '/consents', label: t('nav.consents') },
    );
  }

  return (
    <AppShell brand={t('app.portalName')} nav={nav} actions={<AuthButtons allowSignUp />}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/listings/:id" element={<ListingDetailPage />} />
        <Route
          path="/callback"
          element={
            auth.error ? (
              <RequireAuth>
                <Navigate to="/" replace />
              </RequireAuth>
            ) : auth.isAuthenticated ? (
              <Navigate to="/" replace />
            ) : (
              <Loading />
            )
          }
        />
        <Route
          path="/sell"
          element={
            <RequireRole anyOf={SELLER_ROLES} rolesClaim={oidcSettings.rolesClaim}>
              <SellPage />
            </RequireRole>
          }
        />
        <Route
          path="/my-listings"
          element={
            <RequireRole anyOf={SELLER_ROLES} rolesClaim={oidcSettings.rolesClaim}>
              <MyListingsPage />
            </RequireRole>
          }
        />
        <Route
          path="/profile"
          element={
            <RequireAuth>
              <ProfilePage />
            </RequireAuth>
          }
        />
        <Route
          path="/identity"
          element={
            <RequireAuth>
              <IdentityPage />
            </RequireAuth>
          }
        />
        <Route
          path="/consents"
          element={
            <RequireAuth>
              <ConsentsPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  );
}
