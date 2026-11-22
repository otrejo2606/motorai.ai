import { useTranslation } from 'react-i18next';

const DATE_ONLY = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;

/**
 * Formatos dependientes del idioma activo. defaultCurrency se usa cuando el dato no trae moneda.
 * Las fechas sin hora (LocalDate) se muestran en UTC para que no cambien de dia por la zona horaria.
 */
export function useFormat(defaultCurrency: string) {
  const { i18n } = useTranslation();
  const locale = i18n.language === 'en' ? 'en-US' : 'es-CR';
  return {
    price: (value: number | null | undefined, currency?: string | null) => {
      if (value === null || value === undefined) return '';
      try {
        return new Intl.NumberFormat(locale, {
          style: 'currency',
          currency: currency || defaultCurrency,
          maximumFractionDigits: 0,
        }).format(value);
      } catch {
        return new Intl.NumberFormat(locale).format(value);
      }
    },
    number: (value: number | null | undefined) =>
      value === null || value === undefined ? '' : new Intl.NumberFormat(locale).format(value),
    date: (value: string | null | undefined) => {
      if (!value) return '';
      const options: Intl.DateTimeFormatOptions = DATE_ONLY.test(value)
        ? { dateStyle: 'medium', timeZone: 'UTC' }
        : { dateStyle: 'medium' };
      return new Intl.DateTimeFormat(locale, options).format(new Date(value));
    },
    dateTime: (value: string | null | undefined) =>
      value ? new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '',
    percent: (value: number | null | undefined) =>
      value === null || value === undefined
        ? ''
        : new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 }).format(value),
  };
}
