import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError, type TrustLevel } from '@motoria/api';

export type Tone = 'info' | 'success' | 'warning' | 'error';

export interface AlertProps {
  tone?: Tone;
  title?: ReactNode;
  children?: ReactNode;
}

export function Alert({ tone = 'info', title, children }: AlertProps) {
  return (
    <div className={'m-alert m-alert--' + tone} role={tone === 'error' ? 'alert' : 'status'}>
      {title ? <p className="m-alert__title">{title}</p> : null}
      {children ? <div className="m-alert__body">{children}</div> : null}
    </div>
  );
}

/** Devuelve el codigo estable de un error (ErrorResponse.code) o UNKNOWN. */
export function errorCode(error: unknown): string {
  if (error instanceof ApiError) return error.code;
  return 'UNKNOWN';
}

/** Traduce un error del backend con la clave errors.CODIGO; si no existe, usa errors.UNKNOWN. */
export function useErrorMessage() {
  const { t, i18n } = useTranslation();
  return (error: unknown): string => {
    const code = errorCode(error);
    const key = 'errors.' + code;
    return i18n.exists(key) ? t(key) : t('errors.UNKNOWN');
  };
}

/**
 * Traduce un codigo de details: primero como error (errors.CODIGO) y si no, como toma de foto
 * (photoProtocol.slots.CODIGO.label). Devuelve null si no hay traduccion.
 */
export function useDetailLabel() {
  const { t, i18n } = useTranslation();
  return (code: string): string | null => {
    if (i18n.exists('errors.' + code)) return t('errors.' + code);
    if (i18n.exists('photoProtocol.slots.' + code + '.label')) {
      return t('errors.missingSlot', { slot: t('photoProtocol.slots.' + code + '.label') });
    }
    return null;
  };
}

export function ErrorAlert({ error }: { error: unknown }) {
  const { t, i18n } = useTranslation();
  const message = useErrorMessage();
  const detailLabel = useDetailLabel();
  if (!error) return null;
  const code = errorCode(error);
  const details =
    error instanceof ApiError
      ? error.details
          .filter((detail) => detail !== code)
          .map((detail) => detailLabel(detail))
          .filter((label): label is string => Boolean(label))
      : [];
  // Solo se nombran los campos con traduccion (fields.NOMBRE); los demas quedan cubiertos por errors.VALIDATION_ERROR.
  const violations = error instanceof ApiError && code === 'VALIDATION_ERROR' ? error.violations : [];
  const invalidFields = Array.from(
    new Set(
      violations
        .map((violation) => violation.field.split('.').pop() ?? '')
        .filter((name) => name !== '' && i18n.exists('fields.' + name))
        .map((name) => t('fields.' + name)),
    ),
  );
  return (
    <Alert tone="error" title={t('common.errorTitle')}>
      <p>{message(error)}</p>
      {details.length > 0 ? (
        <ul>
          {details.map((label) => (
            <li key={label}>{label}</li>
          ))}
        </ul>
      ) : null}
      {invalidFields.length > 0 ? (
        <ul>
          {invalidFields.map((field) => (
            <li key={field}>{t('errors.fieldInvalid', { field })}</li>
          ))}
        </ul>
      ) : null}
    </Alert>
  );
}

export function Loading({ label }: { label?: string }) {
  const { t } = useTranslation();
  return (
    <div className="m-loading" role="status" aria-live="polite">
      <span className="m-spinner" aria-hidden="true" />
      <span>{label ?? t('common.loading')}</span>
    </div>
  );
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="m-empty">{children}</p>;
}

export function Card({ title, children, actions }: { title?: ReactNode; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="m-card">
      {title ? <h2 className="m-card__title">{title}</h2> : null}
      <div className="m-card__body">{children}</div>
      {actions ? <div className="m-card__actions">{actions}</div> : null}
    </section>
  );
}

export function Badge({ tone = 'info', children }: { tone?: Tone | 'neutral'; children: ReactNode }) {
  return <span className={'m-badge m-badge--' + tone}>{children}</span>;
}

export function TrustBadge({ level }: { level: TrustLevel | null | undefined }) {
  const { t } = useTranslation();
  const value: TrustLevel = level ?? 'NONE';
  return (
    <Badge tone={value === 'REGISTRY_VERIFIED' ? 'success' : 'neutral'}>{t('trust.' + value)}</Badge>
  );
}

export interface PaginationProps {
  page: number;
  size: number;
  total: number;
  onChange: (page: number) => void;
}

/** Paginacion con pagina base 0. */
export function Pagination({ page, size, total, onChange }: PaginationProps) {
  const { t } = useTranslation();
  const pages = Math.max(1, Math.ceil(total / Math.max(1, size)));
  return (
    <nav className="m-pagination" aria-label={t('common.pagination')}>
      <button type="button" className="m-btn m-btn--secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        {t('common.previous')}
      </button>
      <span aria-live="polite">{t('common.pageOf', { page: page + 1, pages })}</span>
      <button
        type="button"
        className="m-btn m-btn--secondary"
        disabled={page + 1 >= pages}
        onClick={() => onChange(page + 1)}
      >
        {t('common.next')}
      </button>
    </nav>
  );
}

export interface StepperProps {
  steps: string[];
  current: number;
}

export function Stepper({ steps, current }: StepperProps) {
  const { t } = useTranslation();
  return (
    <ol className="m-stepper" aria-label={t('common.progress')}>
      {steps.map((label, index) => {
        const state = index < current ? 'done' : index === current ? 'current' : 'todo';
        return (
          <li key={label} className={'m-stepper__item m-stepper__item--' + state} aria-current={index === current ? 'step' : undefined}>
            <span className="m-stepper__index" aria-hidden="true">
              {index + 1}
            </span>
            <span className="m-stepper__label">{label}</span>
          </li>
        );
      })}
    </ol>
  );
}
