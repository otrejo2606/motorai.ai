import { useTranslation } from 'react-i18next';
import { useVehicle } from '@motoria/api';
import { Alert, Button, Card, ErrorAlert, Loading } from '@motoria/ui';
import type { WizardProps } from './wizard';

export function RegistryStatusStep({ vehicleId, go }: WizardProps) {
  const { t } = useTranslation();
  const vehicle = useVehicle(vehicleId);

  if (vehicle.isLoading) return <Loading />;
  if (!vehicle.data) return <ErrorAlert error={vehicle.error} />;

  const status = vehicle.data.status;
  return (
    <Card title={t('sell.status.title', { plate: vehicle.data.plate })}>
      <div aria-live="polite">
        {status === 'REGISTRY_PENDING' ? (
          <Alert tone="info" title={t('vehicleStatus.REGISTRY_PENDING')}>
            <p>{vehicle.data.registryRequestOpen ? t('sell.status.requestOpenBody') : t('sell.status.pendingBody')}</p>
          </Alert>
        ) : null}
        {status === 'REGISTRY_IN_REVIEW' ? (
          <Alert tone="info" title={t('vehicleStatus.REGISTRY_IN_REVIEW')}>
            <p>{t('sell.status.inReviewBody')}</p>
            {vehicle.data.reviewReason ? (
              <p>{t('sell.status.reviewReason', { reason: t('reviewReason.' + vehicle.data.reviewReason) })}</p>
            ) : null}
          </Alert>
        ) : null}
        {status === 'REGISTRY_VERIFIED' ? (
          <Alert tone="success" title={t('vehicleStatus.REGISTRY_VERIFIED')}>
            <p>{t('sell.status.verifiedBody')}</p>
          </Alert>
        ) : null}
        {status === 'REGISTRY_REJECTED' ? (
          <Alert tone="error" title={t('vehicleStatus.REGISTRY_REJECTED')}>
            <p>{t('sell.status.rejectedBody')}</p>
            {vehicle.data.rejectionReason ? <p className="m-pre">{vehicle.data.rejectionReason}</p> : null}
          </Alert>
        ) : null}
      </div>
      {status === 'REGISTRY_PENDING' || status === 'REGISTRY_IN_REVIEW' ? (
        <p className="m-field__hint">{t('sell.status.autoRefresh')}</p>
      ) : null}
      <div className="m-actions">
        {status === 'REGISTRY_VERIFIED' ? <Button onClick={() => go('vehicle')}>{t('common.continue')}</Button> : null}
        {status === 'REGISTRY_REJECTED' || (status === 'REGISTRY_PENDING' && !vehicle.data.registryRequestOpen) ? (
          <Button variant="secondary" onClick={() => go('registry')}>
            {t('sell.status.uploadAgain')}
          </Button>
        ) : null}
        <Button variant="secondary" loading={vehicle.isFetching} onClick={() => void vehicle.refetch()}>
          {t('common.refresh')}
        </Button>
      </div>
    </Card>
  );
}
