import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useRegistryLookup, useVehicle, type RegistryLookupResult } from '@motoria/api';
import { Alert, Button, Card, ErrorAlert, Loading } from '@motoria/ui';
import { RegistryStep } from './RegistryStep';
import type { WizardProps } from './wizard';

function hasRegistryData(result: RegistryLookupResult): boolean {
  return result.registryLookupStatus === 'FOUND' || result.registryLookupStatus === 'REUSED';
}

/**
 * Consulta la placa en el Registro Nacional por convenio (Credid) al terminar datos y fotos. Si el registro no
 * encuentra la placa o no responde, se muestra el paso del informe PDF (contingencia).
 */
export function RegistryLookupStep(props: WizardProps) {
  const { vehicleId, go } = props;
  const { t } = useTranslation();
  const vehicle = useVehicle(vehicleId);
  const lookup = useRegistryLookup();
  const { mutate } = lookup;
  const started = useRef(false);
  const current = vehicle.data;

  // Una sola consulta automatica al entrar; un vehiculo con datos en revision no se consulta.
  useEffect(() => {
    if (!vehicleId || !current || started.current) return;
    if (current.status === 'REGISTRY_IN_REVIEW') return;
    started.current = true;
    mutate(vehicleId);
  }, [vehicleId, current, mutate]);

  if (!vehicleId) return null;
  if (vehicle.isLoading) return <Loading />;
  if (!current) return <ErrorAlert error={vehicle.error} />;

  const back = (
    <div className="m-actions">
      <Button variant="secondary" onClick={() => go('photos')}>
        {t('common.back')}
      </Button>
    </div>
  );

  if (!lookup.data && !lookup.error && current.status === 'REGISTRY_IN_REVIEW') {
    return (
      <Alert tone="info" title={t('vehicleStatus.REGISTRY_IN_REVIEW')}>
        <p>{t('sell.lookup.inReviewBody')}</p>
        <Button variant="secondary" onClick={() => go('status')}>
          {t('sell.vehicle.goToStatus')}
        </Button>
      </Alert>
    );
  }

  if (lookup.error) {
    return (
      <>
        <Alert tone="warning" title={t('sell.lookup.fallbackTitle')}>
          <ErrorAlert error={lookup.error} />
        </Alert>
        <RegistryStep {...props} />
        {back}
      </>
    );
  }

  const result = lookup.data;
  if (!result) {
    return (
      <Card title={t('sell.lookup.title', { plate: current.plate })}>
        <div aria-live="polite">
          <Loading label={t('sell.lookup.checking')} />
        </div>
      </Card>
    );
  }

  const anomalies =
    result.anomalies.length > 0 ? (
      <Alert tone="warning" title={t('sell.lookup.anomaliesTitle')}>
        <ul>
          {result.anomalies.map((anomaly) => (
            <li key={anomaly}>{t('sell.lookup.anomaly.' + anomaly)}</li>
          ))}
        </ul>
        <p>{t('sell.lookup.anomaliesNotice')}</p>
      </Alert>
    ) : null;

  if (!hasRegistryData(result)) {
    return (
      <>
        <Alert tone="warning" title={t('sell.lookup.fallbackTitle')}>
          <p>{t('sell.lookup.status.' + result.registryLookupStatus)}</p>
        </Alert>
        <RegistryStep {...props} />
        {back}
      </>
    );
  }

  const status = result.vehicle.status;
  return (
    <Card title={t('sell.lookup.title', { plate: result.vehicle.plate })}>
      <div aria-live="polite">
        {status === 'REGISTRY_VERIFIED' ? (
          <Alert tone="success" title={t('vehicleStatus.REGISTRY_VERIFIED')}>
            <p>{t('sell.lookup.status.' + result.registryLookupStatus)}</p>
          </Alert>
        ) : (
          <Alert tone="info" title={t('vehicleStatus.' + status)}>
            <p>{t('sell.lookup.inReviewBody')}</p>
          </Alert>
        )}
        {anomalies}
      </div>
      <div className="m-actions">
        {status === 'REGISTRY_VERIFIED' ? (
          <Button onClick={() => go('vehicle')}>{t('common.continue')}</Button>
        ) : (
          <Button onClick={() => go('status')}>{t('sell.vehicle.goToStatus')}</Button>
        )}
        <Button variant="secondary" onClick={() => go('photos')}>
          {t('common.back')}
        </Button>
      </div>
    </Card>
  );
}
