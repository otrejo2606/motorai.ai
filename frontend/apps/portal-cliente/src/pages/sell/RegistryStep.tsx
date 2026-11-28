import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import {
  acceptFor,
  useAttachRegistryDocument,
  useRequestRegistryDocument,
  useUploadFile,
  useUploadLimits,
  useVehicle,
} from '@motoria/api';
import {
  Alert,
  Button,
  Card,
  CheckboxField,
  ErrorAlert,
  FileField,
  Loading,
  useFieldError,
  useFormat,
} from '@motoria/ui';
import { appConfig } from '../../config';
import type { WizardProps } from './wizard';

const requestSchema = z.object({
  acceptedFee: z.boolean().refine((value) => value, { message: 'validation.mustAccept' }),
});

type RequestValues = z.infer<typeof requestSchema>;

export function RegistryStep({ vehicleId, go }: WizardProps) {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);
  const fieldError = useFieldError();
  const limits = useUploadLimits();
  const vehicle = useVehicle(vehicleId);
  const upload = useUploadFile();
  const attach = useAttachRegistryDocument();
  const request = useRequestRegistryDocument();
  const { register, handleSubmit, formState } = useForm<RequestValues>({
    resolver: zodResolver(requestSchema),
    defaultValues: { acceptedFee: false },
  });

  if (!vehicleId) return null;
  if (vehicle.isLoading) return <Loading />;

  const busy = upload.isPending || attach.isPending;
  const fee = vehicle.data?.registryRequestFee ?? null;
  const requestOpen = vehicle.data?.registryRequestOpen === true;
  const feeText = fee ? format.price(fee.amount, fee.currency) : '';

  const onFile = (file: File) => {
    upload.mutate(
      { file, kind: 'REGISTRY_DOCUMENT' },
      {
        onSuccess: (media) =>
          attach.mutate({ vehicleId, mediaId: media.id }, { onSuccess: () => go('status') }),
      },
    );
  };

  const onRequest = () => {
    if (!fee) return;
    // El backend exige que acceptedFee sea exactamente el monto mostrado.
    request.mutate({ vehicleId, acceptedFee: fee.amount }, { onSuccess: () => go('status') });
  };

  return (
    <>
      {vehicle.data ? <p>{t('sell.registry.forPlate', { plate: vehicle.data.plate })}</p> : null}
      <ErrorAlert error={vehicle.error} />
      <div className="m-grid">
        <Card title={t('sell.registry.uploadTitle')}>
          <p>{t('sell.registry.uploadBody')}</p>
          <FileField
            label={t('sell.registry.uploadLabel')}
            hint={t('sell.registry.uploadHint', { size: Math.round(limits.maxDocumentBytes / 1048576) })}
            accept={acceptFor('REGISTRY_DOCUMENT')}
            disabled={busy}
            onFile={onFile}
          />
          {busy ? <Loading label={t('sell.registry.uploading')} /> : null}
          <ErrorAlert error={upload.error ?? attach.error} />
        </Card>

        <Card title={t('sell.registry.requestTitle')}>
          <p>{t('sell.registry.requestBody')}</p>
          {requestOpen ? (
            <Alert tone="info" title={t('sell.registry.requestOpen')}>
              <Button variant="secondary" onClick={() => go('status')}>
                {t('sell.vehicle.goToStatus')}
              </Button>
            </Alert>
          ) : null}
          {!requestOpen && !fee ? <Alert tone="warning" title={t('sell.registry.requestUnavailable')} /> : null}
          {!requestOpen && fee ? (
            <form className="m-form" onSubmit={handleSubmit(onRequest)} noValidate>
              <p>
                <strong>{t('sell.registry.requestFee', { fee: feeText })}</strong>
              </p>
              <CheckboxField
                label={t('sell.registry.acceptFee', { fee: feeText })}
                error={fieldError(formState.errors.acceptedFee)}
                {...register('acceptedFee')}
              />
              <ErrorAlert error={request.error} />
              <div className="m-actions">
                <Button type="submit" variant="secondary" loading={request.isPending}>
                  {t('sell.registry.requestButton')}
                </Button>
              </div>
            </form>
          ) : null}
        </Card>
      </div>
    </>
  );
}
