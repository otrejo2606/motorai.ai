import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useListing } from '@motoria/api';
import {
  Alert,
  AttributeList,
  Badge,
  Card,
  ErrorAlert,
  Loading,
  PageTitle,
  TrustBadge,
  useBlockedReason,
  useFormat,
} from '@motoria/ui';
import { appConfig } from '../config';

export function ListingDetailPage() {
  const { id } = useParams();
  const { t, i18n } = useTranslation();
  const format = useFormat(appConfig.currency);
  const blockedReason = useBlockedReason();
  const listing = useListing(id);

  if (listing.isLoading) return <Loading />;
  if (listing.error || !listing.data) {
    return (
      <>
        <ErrorAlert error={listing.error} />
        <Link to="/">{t('listing.backToSearch')}</Link>
      </>
    );
  }

  const data = listing.data;
  const vehicle = data.vehicle;
  const attributes = vehicle?.attributes ?? [];
  const verified = attributes.filter((attribute) => attribute.verified);
  const declaredAttributes = attributes.filter((attribute) => !attribute.verified);
  const equipment = data.declaredEquipment ?? [];
  const photos = data.photos.filter((photo) => Boolean(photo.publicUrl));
  const name = data.title || [data.brand, data.model, data.modelYear].filter(Boolean).join(' ') || t('listing.untitled');

  return (
    <article>
      <p>
        <Link to="/">{t('listing.backToSearch')}</Link>
      </p>
      <PageTitle subtitle={format.price(data.price, data.currency)}>{name}</PageTitle>
      {data.status !== 'PUBLISHED' ? (
        <Alert tone="info" title={t('listing.notPublished', { status: t('listingStatus.' + data.status) })} />
      ) : null}

      <Card title={t('listing.trustTitle')}>
        <p>
          <TrustBadge level={data.trustLevel} />
        </p>
        {data.trustLevel === 'REGISTRY_VERIFIED' && vehicle?.registryCheckedAt ? (
          <p>{t('listing.registryCheckedAt', { date: format.date(vehicle.registryCheckedAt) })}</p>
        ) : null}
        <p>{t('listing.trust.' + data.trustLevel)}</p>
        {vehicle?.transferBlocked ? (
          <Alert tone="warning" title={t('listing.transferBlocked')}>
            <p>{blockedReason(vehicle.transferBlockedReason)}</p>
          </Alert>
        ) : null}
      </Card>

      {photos.length > 0 ? (
        <section aria-label={t('listing.photos')}>
          <div className="m-grid">
            {photos.map((photo) => (
              <figure key={photo.slot} className="m-card">
                <img
                  className="m-photo"
                  src={photo.publicUrl ?? ''}
                  alt={t('photoProtocol.slots.' + photo.slot + '.label')}
                  loading="lazy"
                />
                <figcaption>{t('photoProtocol.slots.' + photo.slot + '.label')}</figcaption>
              </figure>
            ))}
          </div>
        </section>
      ) : null}

      <div className="m-grid">
        <Card title={t('listing.verifiedTitle')}>
          <p>
            <Badge tone="success">{t('attributeSource.REGISTRY_DOCUMENT')}</Badge>
          </p>
          <AttributeList attributes={verified} hideOwner />
        </Card>

        <Card title={t('listing.declaredTitle')}>
          <p>
            <Badge tone="neutral">{t('attributeSource.SELLER_DECLARED')}</Badge>
          </p>
          <dl className="m-dl">
            {data.mileage !== null ? (
              <div className="m-dl__row">
                <dt>{t('listing.mileage')}</dt>
                <dd>{t('listing.mileageValue', { value: format.number(data.mileage) })}</dd>
              </div>
            ) : null}
            {data.province ? (
              <div className="m-dl__row">
                <dt>{t('listing.province')}</dt>
                <dd>{t('province.' + data.province)}</dd>
              </div>
            ) : null}
            {data.transmission ? (
              <div className="m-dl__row">
                <dt>{t('listing.transmission')}</dt>
                <dd>{t('transmission.' + data.transmission)}</dd>
              </div>
            ) : null}
            {data.fuel ? (
              <div className="m-dl__row">
                <dt>{t('listing.fuel')}</dt>
                <dd>{t('fuel.' + data.fuel)}</dd>
              </div>
            ) : null}
            <div className="m-dl__row">
              <dt>{t('listing.negotiable')}</dt>
              <dd>{data.negotiable ? t('common.yes') : t('common.no')}</dd>
            </div>
          </dl>
          {declaredAttributes.length > 0 ? <AttributeList attributes={declaredAttributes} hideOwner /> : null}
          {equipment.length > 0 ? (
            <>
              <h3>{t('listing.equipment')}</h3>
              <ul>
                {equipment.map((code) => (
                  <li key={code}>{i18n.exists('equipment.' + code) ? t('equipment.' + code) : code}</li>
                ))}
              </ul>
            </>
          ) : null}
          {data.maintenanceHistory ? (
            <>
              <h3>{t('listing.maintenanceHistory')}</h3>
              <p className="m-pre">{data.maintenanceHistory}</p>
            </>
          ) : null}
        </Card>
      </div>

      {data.description ? (
        <Card title={t('listing.description')}>
          <p className="m-pre">{data.description}</p>
        </Card>
      ) : null}
    </article>
  );
}
