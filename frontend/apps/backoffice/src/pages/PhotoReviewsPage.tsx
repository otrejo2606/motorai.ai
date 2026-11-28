import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  PHOTO_REJECTION_REASONS,
  usePhotoReviews,
  useReviewPhoto,
  type PhotoRejectionReason,
  type PhotoReviewItem,
} from '@motoria/api';
import {
  Alert,
  Badge,
  Button,
  Card,
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
import { pageFrom } from './params';

function useSlotLabel() {
  const { t, i18n } = useTranslation();
  return (slot: string | null): string => {
    if (!slot) return '-';
    if (slot === 'OTHER') return t('photoAngle.detectedOther');
    const key = 'photoProtocol.slots.' + slot + '.label';
    return i18n.exists(key) ? t(key) : slot;
  };
}

/**
 * Una foto de la bandeja: lo que vio el clasificador, lo que leyo (si el servicio tiene OCR) y la decision del analista.
 * Rechazar exige un motivo, que el vendedor ve traducido.
 */
function PhotoReviewCard({ item }: { item: PhotoReviewItem }) {
  const { t, i18n } = useTranslation();
  const format = useFormat(appConfig.currency);
  const slotLabel = useSlotLabel();
  const review = useReviewPhoto();
  const reasonOptions = useEnumOptions(PHOTO_REJECTION_REASONS, 'photoAngle.reason');
  const [reason, setReason] = useState<PhotoRejectionReason | ''>('');
  const busy = review.isPending;
  const reasonKey = item.angleReason ? 'photoAngle.reason.' + item.angleReason : '';
  const errorKey = item.angleReason ? 'errors.' + item.angleReason : '';
  const angleReasonText = !item.angleReason
    ? null
    : i18n.exists(reasonKey)
      ? t(reasonKey)
      : i18n.exists(errorKey)
        ? t(errorKey)
        : item.angleReason;

  const decide = (decision: 'ACCEPTED' | 'REJECTED') => {
    if (decision === 'REJECTED' && !reason) return;
    review.mutate({
      listingId: item.listingId,
      slot: item.slot,
      body: decision === 'REJECTED' && reason ? { decision, reason } : { decision },
    });
  };

  return (
    <Card title={slotLabel(item.slot)}>
      <img className="m-photo m-photo--contain" src={item.publicUrl} alt={slotLabel(item.slot)} referrerPolicy="no-referrer" />
      <p>
        <Badge tone="warning">{t('photoAngle.status.' + item.angleStatus)}</Badge>{' '}
        <Badge tone="neutral">{t('listingStatus.' + item.listingStatus)}</Badge>
      </p>
      <dl className="m-dl">
        <div className="m-dl__row">
          <dt>{t('backoffice.photoReviews.detected')}</dt>
          <dd>{slotLabel(item.detectedSlot)}</dd>
        </div>
        <div className="m-dl__row">
          <dt>{t('backoffice.photoReviews.confidence')}</dt>
          <dd>{item.angleConfidence === null ? '-' : format.percent(item.angleConfidence)}</dd>
        </div>
        <div className="m-dl__row">
          <dt>{t('backoffice.photoReviews.model')}</dt>
          <dd>{item.angleModelVersion ?? '-'}</dd>
        </div>
        {angleReasonText ? (
          <div className="m-dl__row">
            <dt>{t('backoffice.photoReviews.reason')}</dt>
            <dd>{angleReasonText}</dd>
          </div>
        ) : null}
        {item.readVin ? (
          <div className="m-dl__row">
            <dt>{t('backoffice.photoReviews.readVin')}</dt>
            <dd>{item.readVin}</dd>
          </div>
        ) : null}
        {item.readOdometerKm !== null ? (
          <div className="m-dl__row">
            <dt>{t('backoffice.photoReviews.readOdometer')}</dt>
            <dd>{format.number(item.readOdometerKm)}</dd>
          </div>
        ) : null}
        {item.readOdometerKm !== null ? (
          <div className="m-dl__row">
            <dt>{t('backoffice.photoReviews.declaredMileage')}</dt>
            <dd>{item.declaredMileage === null ? '-' : format.number(item.declaredMileage)}</dd>
          </div>
        ) : null}
      </dl>
      {item.readingAnomaly ? <Alert tone="warning" title={t('photoAngle.anomaly.' + item.readingAnomaly)} /> : null}
      <SelectField
        label={t('backoffice.photoReviews.rejectReason')}
        placeholder={t('common.select')}
        options={reasonOptions}
        value={reason}
        onChange={(event) => setReason(event.target.value as PhotoRejectionReason | '')}
      />
      <div className="m-actions">
        <Button loading={busy && review.variables?.body.decision === 'ACCEPTED'} disabled={busy} onClick={() => decide('ACCEPTED')}>
          {t('backoffice.photoReviews.accept')}
        </Button>
        <Button
          variant="danger"
          loading={busy && review.variables?.body.decision === 'REJECTED'}
          disabled={busy || !reason}
          onClick={() => decide('REJECTED')}
        >
          {t('backoffice.photoReviews.reject')}
        </Button>
      </div>
      <p className="m-field__hint">{t('backoffice.photoReviews.rejectHint')}</p>
      <ErrorAlert error={review.error} />
    </Card>
  );
}

/** Bandeja de la validacion del angulo de las fotos (add-captura-guiada-fotos, design D7). */
export function PhotoReviewsPage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = pageFrom(searchParams);
  const reviews = usePhotoReviews(page);
  const items = reviews.data?.items ?? [];

  return (
    <>
      <PageTitle subtitle={t('backoffice.photoReviews.subtitle')}>{t('backoffice.photoReviews.title')}</PageTitle>
      {reviews.isLoading ? <Loading /> : null}
      <ErrorAlert error={reviews.error} />
      {reviews.data && items.length === 0 ? <EmptyState>{t('backoffice.empty')}</EmptyState> : null}
      <div className="m-grid">
        {items.map((item) => (
          <PhotoReviewCard key={item.listingId + '-' + item.slot + '-' + item.mediaId} item={item} />
        ))}
      </div>
      {reviews.data && reviews.data.total > reviews.data.size ? (
        <Pagination
          page={reviews.data.page}
          size={reviews.data.size}
          total={reviews.data.total}
          onChange={(next) => setSearchParams({ page: String(next) })}
        />
      ) : null}
    </>
  );
}
