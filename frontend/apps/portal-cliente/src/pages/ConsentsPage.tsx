import { useTranslation } from 'react-i18next';
import { CONSENT_TYPES, useConsents, useSaveConsent, type Consent, type ConsentType } from '@motoria/api';
import { Alert, Badge, Button, Card, ErrorAlert, Loading, PageTitle, useFormat } from '@motoria/ui';
import { appConfig } from '../config';

export function ConsentsPage() {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);
  const consents = useConsents();
  const save = useSaveConsent();

  if (consents.isLoading) return <Loading />;

  // Un elemento por tipo, con la version vigente del texto (D9.1 punto 9).
  const byType = new Map<ConsentType, Consent>((consents.data?.items ?? []).map((item) => [item.type, item]));
  const versions = Array.from(new Set((consents.data?.items ?? []).map((item) => item.currentTextVersion)));

  return (
    <>
      <PageTitle subtitle={t('consents.subtitle')}>{t('consents.title')}</PageTitle>
      <ErrorAlert error={consents.error ?? save.error} />
      {CONSENT_TYPES.map((type) => {
        const consent = byType.get(type);
        if (!consent) return null;
        const granted = consent.granted;
        const outdated = granted && consent.textVersion !== consent.currentTextVersion;
        const pending = save.isPending && save.variables?.type === type;
        return (
          <Card
            key={type}
            title={t('consents.types.' + type + '.title')}
            actions={
              <Button
                variant={granted && !outdated ? 'secondary' : 'primary'}
                loading={pending}
                onClick={() =>
                  save.mutate({ type, textVersion: consent.currentTextVersion, granted: !granted || outdated })
                }
              >
                {granted && !outdated ? t('consents.revoke') : t('consents.grant')}
              </Button>
            }
          >
            <p className="m-pre">{t('consents.types.' + type + '.text')}</p>
            <p>
              <Badge tone={granted ? 'success' : 'neutral'}>{granted ? t('consents.granted') : t('consents.notGranted')}</Badge>{' '}
              {consent.decidedAt ? t('consents.decidedAt', { date: format.dateTime(consent.decidedAt) }) : null}
            </p>
            {outdated ? <Alert tone="warning" title={t('consents.outdated')} /> : null}
            {type === 'DATA_PROCESSING' ? <p className="m-field__hint">{t('consents.requiredHint')}</p> : null}
          </Card>
        );
      })}
      {versions.length > 0 ? (
        <p className="m-field__hint">{t('consents.version', { version: versions.join(', ') })}</p>
      ) : null}
    </>
  );
}
