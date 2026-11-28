import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { en } from './locales/en';
import { es } from './locales/es';

export type { LocaleShape, Messages } from './locales/es';
export { en, es };

export const SUPPORTED_LANGUAGES = ['es', 'en'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

/** Solo guarda la preferencia de idioma; nunca datos personales ni tokens. */
const LANGUAGE_STORAGE_KEY = 'motoria.language';

function isLanguage(value: string | null | undefined): value is Language {
  return value === 'es' || value === 'en';
}

function readStoredLanguage(): Language | null {
  try {
    const stored = window.localStorage.getItem(LANGUAGE_STORAGE_KEY);
    return isLanguage(stored) ? stored : null;
  } catch {
    return null;
  }
}

function storeLanguage(language: string): void {
  try {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  } catch {
    // Sin almacenamiento disponible: el idioma vale solo para esta sesion.
  }
}

function detectLanguage(fallback: Language): Language {
  const stored = readStoredLanguage();
  if (stored) return stored;
  const browser = typeof navigator !== 'undefined' ? navigator.language.slice(0, 2) : '';
  return isLanguage(browser) ? browser : fallback;
}

export function initI18n(defaultLanguage: string | undefined) {
  const fallback: Language = isLanguage(defaultLanguage) ? defaultLanguage : 'es';
  if (!i18n.isInitialized) {
    const language = detectLanguage(fallback);
    void i18n.use(initReactI18next).init({
      resources: {
        es: { translation: es },
        en: { translation: en },
      },
      lng: language,
      fallbackLng: fallback,
      supportedLngs: [...SUPPORTED_LANGUAGES],
      interpolation: { escapeValue: false },
      initImmediate: false,
    });
    document.documentElement.lang = language;
    i18n.on('languageChanged', (next) => {
      document.documentElement.lang = next;
      storeLanguage(next);
    });
  }
  return i18n;
}

export { i18n };
