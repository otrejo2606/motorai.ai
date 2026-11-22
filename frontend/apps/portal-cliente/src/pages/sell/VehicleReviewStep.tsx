import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useVehicle } from '@motoria/api';
import { Alert, AttributeList, Badge, Button, Card, ErrorAlert, Loading, useBlockedReason } from '@motoria/ui';
import type { WizardProps } from './wizard';

export function VehicleReviewStep({ vehicleId, go }: WizardProps) {
  const { t } = useTranslation();
  const blockedReason = useBlockedReason();
  const vehicle = useVehicle(vehicleId);

  if (vehicle.isLoading) return <Loading />;
  if (!vehicle.data) return <ErrorAlert error={vehicle.error} />;

  const data = vehicle.data;
  if (data.status !== 'REGISTRY_VERIFIED') {
    return (
      <Alert tone="warning" title={t('sell.vehicle.notVerifiedTitle')}>
        <Button variant="secondary" onClick={() => go('status')}>
          {t('sell.vehicle.goToStatus')}
        </Button>
      </Alert>
    );
  }

  const encumbrances = data.encumbrances ?? [];
  return (
    <>
      <Card title={t('sell.vehicle.title', { plate: data.plate })}>
        <p>{t('sell.vehicle.body')}</p>
        <AttributeList attributes={data.attributes ?? []} showSource />
      </Card>

      <Card title={t('sell.vehicle.ownerTitle')}>
        <p>
          <Badge tone={data.ownerMatch === 'MATCH' ? 'success' : data.ownerMatch === 'MISMATCH' ? 'error' : 'warning'}>
            {t('ownerMatch.' + data.ownerMatch)}
          </Badge>
        </p>
        <p>{t('sell.vehicle.owner.' + data.ownerMatch)}</p>
        {data.ownerMatch === 'UNKNOWN' ? (
          <p>
            <Link to="/identity">{t('sell.identityLink')}</Link>
          </p>
        ) : null}
      </Card>

      <Card title={t('sell.vehicle.encumbrancesTitle')}>
        {encumbrances.length === 0 ? (
          <p>{t('sell.vehicle.noEncumbrances')}</p>
        ) : (
          <ul>
            {encumbrances.map((item, index) => (
              <li key={item.type + String(index)}>
                <strong>{t('encumbrance.' + item.type)}</strong>
                {item.description ? ': ' + item.description : ''}{' '}
                {item.blocksTransfer ? <span>({t('sell.vehicle.blocksTransfer')})</span> : null}
              </li>
            ))}
          </ul>
        )}
      </Card>

      {data.transferBlocked ? (
        <Alert tone="warning" title={t('sell.vehicle.blockedTitle')}>
          <p>{t('sell.vehicle.blockedBody')}</p>
          {data.transferBlockedReason ? <p>{blockedReason(data.transferBlockedReason)}</p> : null}
        </Alert>
      ) : null}

      <div className="m-actions">
        <Button onClick={() => go('data')}>{t('sell.vehicle.confirm')}</Button>
      </div>
    </>
  );
}
