import { useTranslation } from 'react-i18next';
import { useRecheckIdentityRegistry, type BackofficeIdentityVerification, type RegistryMatchStatus } from '@motoria/api';
import { Badge, Button, ErrorAlert, useFormat, type Tone } from '@motoria/ui';
import { appConfig } from '../config';

const TONES: Record<RegistryMatchStatus, Tone> = {
  MATCH: 'success',
  NAME_MISMATCH: 'error',
  NOT_FOUND: 'error',
  DOCUMENT_EXPIRED: 'error',
  UNAVAILABLE: 'warning',
  NOT_APPLICABLE: 'info',
};

/**
 * Lo que dice el registro de personas (Credid) del documento del caso. Es informativo: el analista sigue decidiendo
 * con el documento y la selfie.
 */
export function RegistryCheckPanel({ item }: { item: BackofficeIdentityVerification }) {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);
  const recheck = useRecheckIdentityRegistry();
  const canRecheck = item.status === 'SUBMITTED' && item.registryStatus !== 'NOT_APPLICABLE';

  return (
    <section aria-label={t('backoffice.identity.registry.title')}>
      <p>
        <strong>{t('backoffice.identity.registry.title')}</strong>{' '}
        {item.registryStatus ? (
          <Badge tone={TONES[item.registryStatus]}>{t('registryMatch.' + item.registryStatus)}</Badge>
        ) : (
          <Badge tone="info">{t('backoffice.identity.registry.notChecked')}</Badge>
        )}
      </p>
      {item.registryFullName ? (
        <p>{t('backoffice.identity.registry.fullName', { name: item.registryFullName })}</p>
      ) : null}
      {item.registryPersonType ? <p>{t('personType.' + item.registryPersonType)}</p> : null}
      {item.registryDocumentExpiresOn ? (
        <p>{t('backoffice.identity.registry.expiresOn', { date: format.date(item.registryDocumentExpiresOn) })}</p>
      ) : null}
      {item.registryCheckedAt ? (
        <p className="m-field__hint">
          {t('backoffice.identity.registry.checkedAt', {
            date: format.dateTime(item.registryCheckedAt),
            provider: item.registryProvider ?? '',
          })}
        </p>
      ) : null}
      <p className="m-field__hint">{t('backoffice.identity.registry.hint')}</p>
      {canRecheck ? (
        <>
          <ErrorAlert error={recheck.error} />
          <div className="m-actions">
            <Button variant="secondary" loading={recheck.isPending} onClick={() => recheck.mutate(item.id)}>
              {t('backoffice.identity.registry.recheck')}
            </Button>
          </div>
        </>
      ) : null}
    </section>
  );
}
