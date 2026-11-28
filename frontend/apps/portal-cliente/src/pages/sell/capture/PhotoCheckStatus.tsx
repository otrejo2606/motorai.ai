import { useTranslation } from 'react-i18next';
import type { ListingPhoto } from '@motoria/api';
import { Alert, Badge, Button } from '@motoria/ui';

/** Nombre traducido de la toma que vio el clasificador (OTHER es "otra cosa"). */
function useDetectedLabel() {
  const { t, i18n } = useTranslation();
  return (detected: string | null | undefined): string | null => {
    if (!detected) return null;
    if (detected === 'OTHER') return t('photoAngle.detectedOther');
    const key = 'photoProtocol.slots.' + detected + '.label';
    return i18n.exists(key) ? t(key) : null;
  };
}

export interface PhotoCheckStatusProps {
  photo: ListingPhoto;
  /** Vuelve a pedir la validacion de la misma foto (UNAVAILABLE o PENDING que quedo colgado). */
  onRetry: () => void;
  retrying: boolean;
}

/**
 * Estado de la validacion del angulo de una foto (add-captura-guiada-fotos): validando, correcta, repetir con motivo o
 * en revision por un analista. Tambien muestra la anomalia de lectura (VIN u odometro) si la hay.
 */
export function PhotoCheckStatus({ photo, onRetry, retrying }: PhotoCheckStatusProps) {
  const { t, i18n } = useTranslation();
  const detectedLabel = useDetectedLabel();
  const status = photo.angleStatus ?? 'NOT_CHECKED';
  const detected = detectedLabel(photo.detectedSlot);
  const reasonKey = photo.repeatReason ? 'photoAngle.reason.' + photo.repeatReason : '';

  return (
    <>
      {photo.mustBeRepeated ? (
        <Alert tone="error" title={t('photoAngle.repeatTitle')}>
          {photo.reviewDecision === 'REJECTED' ? <p>{t('photoAngle.reviewDecision.REJECTED')}</p> : null}
          <p>{reasonKey && i18n.exists(reasonKey) ? t(reasonKey) : t('photoAngle.reason.OTHER')}</p>
          {detected && photo.reviewDecision !== 'REJECTED' ? <p>{t('photoAngle.detected', { slot: detected })}</p> : null}
        </Alert>
      ) : (
        <p>
          {photo.reviewDecision === 'ACCEPTED' ? (
            <Badge tone="success">{t('photoAngle.reviewDecision.ACCEPTED')}</Badge>
          ) : status === 'MATCH' ? (
            <Badge tone="success">{t('photoAngle.status.MATCH')}</Badge>
          ) : status === 'PENDING' ? (
            <Badge tone="info">{t('photoAngle.status.PENDING')}</Badge>
          ) : status === 'UNCERTAIN' || status === 'UNAVAILABLE' ? (
            <Badge tone="warning">{t('photoAngle.status.' + status)}</Badge>
          ) : (
            <Badge tone="success">{t('photoProtocol.uploaded')}</Badge>
          )}
        </p>
      )}
      {photo.readingAnomaly ? (
        <Alert tone="warning" title={t('photoAngle.anomaly.' + photo.readingAnomaly)}>
          <p>{t('photoAngle.anomalyNotice')}</p>
        </Alert>
      ) : null}
      {(status === 'UNAVAILABLE' || status === 'PENDING') && !photo.reviewDecision ? (
        <Button variant="link" loading={retrying} onClick={onRetry}>
          {t('photoAngle.retry')}
        </Button>
      ) : null}
    </>
  );
}
