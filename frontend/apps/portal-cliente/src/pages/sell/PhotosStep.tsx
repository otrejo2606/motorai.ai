import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  acceptFor,
  useDeleteListingPhoto,
  useListing,
  usePhotoProtocol,
  useSetListingPhoto,
  useUploadFile,
  useUploadLimits,
  type ListingPhoto,
  type PhotoSlot,
  type PhotoSlotCode,
} from '@motoria/api';
import { Alert, Badge, Button, Card, ErrorAlert, FileField, Loading } from '@motoria/ui';
import { appConfig, captureConfig } from '../../config';
import type { WizardProps } from './wizard';
import { ArGuide } from './capture/ArGuide';
import { GuidedCamera } from './capture/GuidedCamera';
import { PhotoCheckStatus } from './capture/PhotoCheckStatus';
import { SLOT_GUIDES } from './capture/slotGuides';
import { isImmersiveArSupported } from './capture/webxr';

type CaptureMode = 'idle' | 'camera' | 'ar';

function SlotCard({
  slot,
  listingId,
  photo,
  localPreview,
  onPreview,
  arAvailable,
}: {
  slot: PhotoSlot;
  listingId: string;
  photo: ListingPhoto | undefined;
  /** Vista previa local (URL de objeto del archivo elegido), nunca una URL privada del servidor. */
  localPreview: string | undefined;
  onPreview: (code: PhotoSlotCode, file: File | null) => void;
  arAvailable: boolean;
}) {
  const { t } = useTranslation();
  const limits = useUploadLimits();
  const upload = useUploadFile();
  const setPhoto = useSetListingPhoto();
  const removePhoto = useDeleteListingPhoto();
  const [mode, setMode] = useState<CaptureMode>('idle');
  const label = t('photoProtocol.slots.' + slot.code + '.label');
  const fallbackInstruction = t('photoProtocol.slots.' + slot.code + '.instruction');
  const busy = upload.isPending || setPhoto.isPending;
  const preview = localPreview ?? photo?.publicUrl ?? undefined;
  const offersAr = arAvailable && SLOT_GUIDES[slot.code].ar !== null;

  // Al adjuntar, el servidor valida el angulo en la misma solicitud y la respuesta ya trae el resultado (design D2).
  const onFile = (file: File) => {
    setMode('idle');
    upload.mutate(
      { file, kind: 'LISTING_PHOTO', photoSlot: slot.code },
      {
        onSuccess: (media) =>
          setPhoto.mutate(
            { listingId, slot: slot.code, mediaId: media.id },
            { onSuccess: () => onPreview(slot.code, file) },
          ),
      },
    );
  };

  const retryCheck = () => {
    if (photo?.mediaId) {
      setPhoto.mutate({ listingId, slot: slot.code, mediaId: photo.mediaId });
    }
  };

  return (
    <article className="m-card">
      <h3 className="m-card__title">
        {label}{' '}
        <Badge tone={slot.required ? 'warning' : 'neutral'}>
          {slot.required ? t('photoProtocol.required') : t('photoProtocol.optional')}
        </Badge>
      </h3>
      <p>{t(slot.instructionKey, { defaultValue: fallbackInstruction })}</p>
      {photo ? (
        <>
          {preview ? <img className="m-photo" src={preview} alt={label} /> : null}
          <PhotoCheckStatus photo={photo} onRetry={retryCheck} retrying={setPhoto.isPending} />
          <Button
            variant="secondary"
            loading={removePhoto.isPending}
            onClick={() =>
              removePhoto.mutate({ listingId, slot: slot.code }, { onSuccess: () => onPreview(slot.code, null) })
            }
          >
            {t('photoProtocol.remove')}
          </Button>
        </>
      ) : null}
      <div className="m-actions">
        <Button onClick={() => setMode('camera')} disabled={busy}>
          {photo ? t('capture.retake') : t('capture.open')}
        </Button>
        {offersAr ? (
          <Button variant="secondary" onClick={() => setMode('ar')} disabled={busy}>
            {t('capture.ar.start')}
          </Button>
        ) : null}
      </div>
      <FileField
        label={photo ? t('photoProtocol.replace') : t('photoProtocol.choose')}
        hint={t('photoProtocol.fileHint', { size: Math.round(limits.maxPhotoBytes / 1048576) })}
        accept={acceptFor('LISTING_PHOTO')}
        capture="environment"
        disabled={busy}
        onFile={onFile}
      />
      {busy ? <Loading label={upload.isPending ? t('photoProtocol.uploading') : t('photoAngle.status.PENDING')} /> : null}
      <ErrorAlert error={upload.error ?? setPhoto.error ?? removePhoto.error} />
      {mode === 'camera' ? <GuidedCamera slot={slot.code} onCapture={onFile} onCancel={() => setMode('idle')} /> : null}
      {mode === 'ar' ? (
        <ArGuide slot={slot.code} onArrived={() => setMode('camera')} onExit={() => setMode('idle')} />
      ) : null}
    </article>
  );
}

/** La RA solo se ofrece con la bandera VITE_AR_ENABLED y si el navegador soporta immersive-ar (design D9). */
function useArAvailable(): boolean {
  const [available, setAvailable] = useState(false);
  useEffect(() => {
    if (!captureConfig.arEnabled) return undefined;
    let active = true;
    void isImmersiveArSupported().then((supported) => {
      if (active) setAvailable(supported);
    });
    return () => {
      active = false;
    };
  }, []);
  return available;
}

export function PhotosStep({ listingId, go }: WizardProps) {
  const { t } = useTranslation();
  const protocol = usePhotoProtocol(appConfig.country);
  const listing = useListing(listingId);
  const arAvailable = useArAvailable();
  const [previews, setPreviews] = useState<Partial<Record<PhotoSlotCode, string>>>({});
  const created = useRef<string[]>([]);

  useEffect(
    () => () => {
      created.current.forEach((url) => URL.revokeObjectURL(url));
    },
    [],
  );

  if (!listingId) return null;
  if (protocol.isLoading || listing.isLoading) return <Loading />;
  if (!protocol.data) return <ErrorAlert error={protocol.error} />;
  if (!listing.data) return <ErrorAlert error={listing.error} />;

  // Las tomas ya guardadas vienen en el anuncio del dueno, incluidas las privadas (D9.1 punto 2).
  const photos = new Map<PhotoSlotCode, ListingPhoto>(listing.data.photos.map((photo) => [photo.slot, photo]));

  const onPreview = (code: PhotoSlotCode, file: File | null) => {
    const url = file ? URL.createObjectURL(file) : null;
    if (url) created.current.push(url);
    setPreviews((current) => {
      const next = { ...current };
      if (url) next[code] = url;
      else delete next[code];
      return next;
    });
  };

  const missing = protocol.data.filter((slot) => slot.required && !photos.has(slot.code));
  // Una foto en MISMATCH o rechazada por el analista bloquea el envio a revision (LISTING_PHOTOS_TO_REPEAT).
  const toRepeat = listing.data.photos.filter((photo) => photo.mustBeRepeated === true);

  return (
    <>
      <Card title={t('sell.photos.title')}>
        <p>{t('sell.photos.body')}</p>
        <ul>
          <li>{t('sell.photos.tipGuide')}</li>
          <li>{t('sell.photos.tipLight')}</li>
          <li>{t('sell.photos.tipFrame')}</li>
          <li>{t('sell.photos.tipOdometer')}</li>
        </ul>
      </Card>
      <div className="m-grid">
        {protocol.data.map((slot) => (
          <SlotCard
            key={slot.code}
            slot={slot}
            listingId={listingId}
            photo={photos.get(slot.code)}
            localPreview={previews[slot.code]}
            onPreview={onPreview}
            arAvailable={arAvailable}
          />
        ))}
      </div>
      {missing.length > 0 ? (
        <Alert tone="warning" title={t('sell.photos.missingTitle', { count: missing.length })}>
          <p>{missing.map((slot) => t('photoProtocol.slots.' + slot.code + '.label')).join(', ')}</p>
        </Alert>
      ) : null}
      {toRepeat.length > 0 ? (
        <Alert tone="error" title={t('sell.photos.repeatTitle', { count: toRepeat.length })}>
          <p>{toRepeat.map((photo) => t('photoProtocol.slots.' + photo.slot + '.label')).join(', ')}</p>
        </Alert>
      ) : null}
      {missing.length === 0 && toRepeat.length === 0 ? <Alert tone="success" title={t('sell.photos.complete')} /> : null}
      <div className="m-actions">
        {/* Con las fotos completas y ninguna por repetir se consulta el registro por placa (add-consulta-credid-vehiculos). */}
        <Button onClick={() => go('lookup')} disabled={missing.length > 0 || toRepeat.length > 0}>
          {t('common.continue')}
        </Button>
        <Button variant="secondary" onClick={() => go('data')}>
          {t('common.back')}
        </Button>
      </div>
    </>
  );
}
