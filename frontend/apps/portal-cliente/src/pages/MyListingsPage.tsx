import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMarkListingSold, useMyListings, useSubmitListingForReview, type ListingStatus } from '@motoria/api';
import {
  Badge,
  Button,
  EmptyState,
  ErrorAlert,
  Loading,
  PageTitle,
  Pagination,
  TrustBadge,
  useFormat,
  type Tone,
} from '@motoria/ui';
import { appConfig } from '../config';
import { resumePath } from './sell/wizard';

const STATUS_TONE: Record<ListingStatus, Tone | 'neutral'> = {
  DRAFT: 'neutral',
  PENDING_REVIEW: 'info',
  PUBLISHED: 'success',
  SOLD: 'neutral',
  EXPIRED: 'warning',
  REJECTED: 'error',
};

export function MyListingsPage() {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);
  const [page, setPage] = useState(0);
  const listings = useMyListings(page);
  const submit = useSubmitListingForReview();
  const markSold = useMarkListingSold();
  const items = listings.data?.items ?? [];

  return (
    <>
      <PageTitle>{t('myListings.title')}</PageTitle>
      <p>
        <Link to="/sell">{t('myListings.new')}</Link>
      </p>
      {listings.isLoading ? <Loading /> : null}
      <ErrorAlert error={listings.error ?? submit.error ?? markSold.error} />
      {listings.data && items.length === 0 ? <EmptyState>{t('myListings.empty')}</EmptyState> : null}
      {items.length > 0 ? (
        <div className="m-table-wrap">
          <table className="m-table">
            <caption className="m-visually-hidden">{t('myListings.title')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('myListings.columns.title')}</th>
                <th scope="col">{t('myListings.columns.price')}</th>
                <th scope="col">{t('myListings.columns.status')}</th>
                <th scope="col">{t('myListings.columns.trust')}</th>
                <th scope="col">{t('myListings.columns.actions')}</th>
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
                    <Badge tone={STATUS_TONE[listing.status] ?? 'neutral'}>{t('listingStatus.' + listing.status)}</Badge>
                  </td>
                  <td>
                    <TrustBadge level={listing.trustLevel} />
                  </td>
                  <td>
                    <div className="m-actions">
                      {(listing.status === 'DRAFT' || listing.status === 'REJECTED') && listing.vehicleId ? (
                        <Link
                          className="m-btn m-btn--secondary"
                          to={resumePath('data', { vehicleId: listing.vehicleId, listingId: listing.id })}
                        >
                          {t('myListings.continue')}
                        </Link>
                      ) : null}
                      {listing.status === 'DRAFT' && listing.vehicleId ? (
                        <Link
                          className="m-btn m-btn--secondary"
                          to={resumePath('photos', { vehicleId: listing.vehicleId, listingId: listing.id })}
                        >
                          {t('myListings.photos')}
                        </Link>
                      ) : null}
                      {listing.status === 'DRAFT' ? (
                        <Button
                          loading={submit.isPending && submit.variables === listing.id}
                          onClick={() => submit.mutate(listing.id)}
                        >
                          {t('myListings.submit')}
                        </Button>
                      ) : null}
                      {listing.status === 'PUBLISHED' ? (
                        <>
                          <Link className="m-btn m-btn--secondary" to={'/listings/' + encodeURIComponent(listing.id)}>
                            {t('myListings.view')}
                          </Link>
                          <Button
                            variant="secondary"
                            loading={markSold.isPending && markSold.variables === listing.id}
                            onClick={() => markSold.mutate(listing.id)}
                          >
                            {t('myListings.markSold')}
                          </Button>
                        </>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {listings.data && listings.data.total > listings.data.size ? (
        <Pagination page={listings.data.page} size={listings.data.size} total={listings.data.total} onChange={setPage} />
      ) : null}
    </>
  );
}
