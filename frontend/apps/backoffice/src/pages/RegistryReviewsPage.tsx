import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { VEHICLE_STATUSES, useRegistryReviews } from '@motoria/api';
import {
  EmptyState,
  ErrorAlert,
  Loading,
  PageTitle,
  Pagination,
  SelectField,
  useEnumOptions,
  useFormat,
} from '@motoria/ui';
import { appConfig } from '../config';
import { pageFrom, statusFrom } from './params';

const DEFAULT_STATUS = 'REGISTRY_IN_REVIEW';

export function RegistryReviewsPage() {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);
  const [searchParams, setSearchParams] = useSearchParams();
  const status = statusFrom(searchParams, VEHICLE_STATUSES, DEFAULT_STATUS);
  const page = pageFrom(searchParams);
  const reviews = useRegistryReviews(status, page);
  const statusOptions = useEnumOptions(VEHICLE_STATUSES, 'vehicleStatus');
  const items = reviews.data?.items ?? [];

  return (
    <>
      <PageTitle subtitle={t('backoffice.registryReviews.subtitle')}>{t('backoffice.registryReviews.title')}</PageTitle>
      <SelectField
        label={t('backoffice.statusFilter')}
        options={statusOptions}
        value={status}
        onChange={(event) => setSearchParams({ status: event.target.value })}
      />
      {reviews.isLoading ? <Loading /> : null}
      <ErrorAlert error={reviews.error} />
      {reviews.data && items.length === 0 ? <EmptyState>{t('backoffice.empty')}</EmptyState> : null}
      {items.length > 0 ? (
        <div className="m-table-wrap">
          <table className="m-table">
            <caption className="m-visually-hidden">{t('backoffice.registryReviews.title')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('backoffice.columns.plate')}</th>
                <th scope="col">{t('backoffice.columns.status')}</th>
                <th scope="col">{t('backoffice.columns.reasons')}</th>
                <th scope="col">{t('backoffice.columns.updated')}</th>
                <th scope="col">{t('backoffice.columns.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((review) => (
                <tr key={review.vehicleId}>
                  <td>
                    {review.plate} <span className="m-field__hint">{review.countryCode}</span>
                  </td>
                  <td>{t('vehicleStatus.' + review.status)}</td>
                  <td>{review.reviewReason ? t('reviewReason.' + review.reviewReason) : ''}</td>
                  <td>{format.dateTime(review.updatedAt)}</td>
                  <td>
                    <Link className="m-btn m-btn--secondary" to={'/registry-reviews/' + encodeURIComponent(review.vehicleId)}>
                      {t('backoffice.open')}
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {reviews.data && reviews.data.total > reviews.data.size ? (
        <Pagination
          page={reviews.data.page}
          size={reviews.data.size}
          total={reviews.data.total}
          onChange={(next) => setSearchParams({ status, page: String(next) })}
        />
      ) : null}
    </>
  );
}
