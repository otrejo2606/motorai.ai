import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMediaReadUrl } from '@motoria/api';
import { Button, ErrorAlert, Loading } from '@motoria/ui';

/**
 * Imagen privada (documento de identidad, selfie). La URL de lectura de corta duracion se pide
 * solo cuando el analista decide verla (cada lectura queda auditada en el backend) y no se guarda.
 */
export function PrivateImage({ mediaId, alt }: { mediaId: string | null | undefined; alt: string }) {
  const { t } = useTranslation();
  const [requested, setRequested] = useState(false);
  const readUrl = useMediaReadUrl(requested ? mediaId : null);

  if (!mediaId) return <p className="m-empty">{t('backoffice.media.missing')}</p>;
  if (!requested) {
    return (
      <Button variant="secondary" onClick={() => setRequested(true)}>
        {t('backoffice.media.show', { name: alt })}
      </Button>
    );
  }
  if (readUrl.isLoading) return <Loading />;
  if (!readUrl.data) return <ErrorAlert error={readUrl.error} />;
  return <img className="m-photo m-photo--contain" src={readUrl.data.url} alt={alt} referrerPolicy="no-referrer" />;
}
