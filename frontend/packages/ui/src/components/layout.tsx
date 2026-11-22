import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router-dom';
import { SUPPORTED_LANGUAGES } from '@motoria/i18n';

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation();
  return (
    <label className="m-lang">
      <span className="m-visually-hidden">{t('common.language')}</span>
      <select
        className="m-input m-select m-lang__select"
        value={i18n.language}
        onChange={(event) => void i18n.changeLanguage(event.target.value)}
      >
        {SUPPORTED_LANGUAGES.map((language) => (
          <option key={language} value={language}>
            {t('languages.' + language)}
          </option>
        ))}
      </select>
    </label>
  );
}

export interface NavItem {
  to: string;
  label: string;
  end?: boolean;
}

export interface AppShellProps {
  brand: string;
  nav: NavItem[];
  actions?: ReactNode;
  children: ReactNode;
}

/** Marco comun: enlace para saltar al contenido, cabecera, navegacion plegable en movil y pie. */
export function AppShell({ brand, nav, actions, children }: AppShellProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <div className="m-app">
      <a className="m-skip" href="#main">
        {t('common.skipToContent')}
      </a>
      <header className="m-header">
        <div className="m-header__bar">
          <NavLink to="/" className="m-header__brand">
            {brand}
          </NavLink>
          <button
            type="button"
            className="m-btn m-btn--secondary m-header__toggle"
            aria-expanded={open}
            aria-controls="m-main-nav"
            onClick={() => setOpen((value) => !value)}
          >
            {t('common.menu')}
          </button>
        </div>
        <nav id="m-main-nav" className={'m-nav' + (open ? ' m-nav--open' : '')} aria-label={t('common.mainNav')}>
          <ul className="m-nav__list">
            {nav.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) => 'm-nav__link' + (isActive ? ' m-nav__link--active' : '')}
                  onClick={() => setOpen(false)}
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
          <div className="m-nav__actions">
            <LanguageSwitcher />
            {actions}
          </div>
        </nav>
      </header>
      <main id="main" className="m-main" tabIndex={-1}>
        {children}
      </main>
      <footer className="m-footer">
        <p>{t('common.footer')}</p>
      </footer>
    </div>
  );
}

export function PageTitle({ children, subtitle }: { children: ReactNode; subtitle?: ReactNode }) {
  return (
    <div className="m-page-title">
      <h1>{children}</h1>
      {subtitle ? <p className="m-page-title__subtitle">{subtitle}</p> : null}
    </div>
  );
}
