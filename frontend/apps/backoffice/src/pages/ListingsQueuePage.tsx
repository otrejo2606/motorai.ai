import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LISTING_STATUSES, useBackofficeListings, usePublishListing } from '@motoria/api';
import {
  Button,
  EmptyState,
  ErrorAlert,
  Loading,
  PageTitle,
  Pagination,
  SelectField,
  TrustBadge,
  useEnumOptions,
  useFormat,
} from '@motoria/ui';
import { appConfig } from '../config';
import { pageFrom, statusFrom } from './params';

export function ListingsQueuePage() {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);
  const [searchParams, setSearchParams] = useSearchParams();
  const status = statusFrom(searchParams, LISTING_STATUSES, 'PENDING_REVIEW');
  const page = pageFrom(searchParams);
  const listings = useBackofficeListings(status, page);
  const publish = usePublishListing();
  const statusOptions = useEnumOptions(LISTING_STATUSES, 'listingStatus');
  const items = listings.data?.items ?? [];

  return (
    <>
      <PageTitle subtitle={t('backoffice.listings.subtitle')}>{t('backoffice.listings.title')}</PageTitle>
      <SelectField
        label={t('backoffice.statusFilter')}
        options={statusOptions}
        value={status}
        onChange={(event) => setSearchParams({ status: event.target.value })}
      />
      {listings.isLoading ? <Loading /> : null}
      {/* El error de publicar trae en details las reglas que no se cumplen y las fotos que faltan. */}
      <ErrorAlert error={listings.error ?? publish.error} />
      {listings.data && items.length === 0 ? <EmptyState>{t('backoffice.empty')}</EmptyState> : null}
      {items.length > 0 ? (
        <div className="m-table-wrap">
          <table className="m-table">
            <caption className="m-visually-hidden">{t('backoffice.listings.title')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('myListings.columns.title')}</th>
                <th scope="col">{t('myListings.columns.price')}</th>
                <th scope="col">{t('myListings.columns.trust')}</th>
                <th scope="col">{t('backoffice.columns.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((listing) => (
                <tr key={listing.id}>
                  <td>
                    {listing.title ||
                      [listing.brand, listing.model, listing.modelYear].filter(Boolean).join(' ') ||
                      t('listing.untitled')}
                  </td>
                  <td>{format.price(listing.price, listing.currency)}</td>
                  <td>
                    <TrustBadge level={listing.trustLevel} />
                  </td>
                  <td>
                    {listing.status === 'PENDING_REVIEW' ? (
                      <Button
                        loading={publish.isPending && publish.variables === listing.id}
                        disabled={listing.trustLevel === 'NONE'}
                        onClick={() => publish.mutate(listing.id)}
                      >
                        {t('backoffice.listings.publish')}
                      </Button>
                    ) : null}
                    {listing.status === 'PENDING_REVIEW' && listing.trustLevel === 'NONE' ? (
                      <p className="m-field__hint">{t('backoffice.listings.notVerified')}</p>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {listings.data && listings.data.total > listings.data.size ? (
        <Pagination
          page={listings.data.page}
          size={listings.data.size}
          total={listings.data.total}
          onChange={(next) => setSearchParams({ status, page: String(next) })}
        />
      ) : null}
    </>
  );
}
