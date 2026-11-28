import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { usePhotoProtocol, useUpdatePhotoSlot, type PhotoSlot } from '@motoria/api';
import {
  Badge,
  Button,
  CheckboxField,
  EmptyState,
  ErrorAlert,
  Loading,
  PageTitle,
  TextField,
  useFieldError,
} from '@motoria/ui';
import { appConfig } from '../config';

const slotSchema = z.object({
  required: z.boolean(),
  order: z
    .number({ invalid_type_error: 'validation.number', required_error: 'validation.required' })
    .int('validation.integer')
    .min(1, 'validation.orderRange')
    .max(99, 'validation.orderRange'),
  instructionKey: z
    .string()
    .trim()
    .min(1, 'validation.required')
    .max(120, 'validation.tooLong')
    .regex(/^[A-Za-z0-9_.]+$/, 'validation.i18nKey'),
});

type SlotValues = z.infer<typeof slotSchema>;

function SlotRow({ country, slot }: { country: string; slot: PhotoSlot }) {
  const { t } = useTranslation();
  const fieldError = useFieldError();
  const update = useUpdatePhotoSlot();
  const { register, handleSubmit, formState, watch } = useForm<SlotValues>({
    resolver: zodResolver(slotSchema),
    values: { required: slot.required, order: slot.order, instructionKey: slot.instructionKey },
  });
  const instructionKey = watch('instructionKey');

  return (
    <tr>
      <th scope="row">
        {t('photoProtocol.slots.' + slot.code + '.label')}
        <div className="m-field__hint">{slot.code}</div>
      </th>
      <td colSpan={4}>
        <form
          className="m-form"
          noValidate
          onSubmit={handleSubmit((values) => update.mutate({ country, code: slot.code, body: values }))}
        >
          <div className="m-form-grid">
            <CheckboxField label={t('backoffice.protocol.required')} {...register('required')} />
            <TextField
              label={t('backoffice.protocol.order')}
              type="number"
              inputMode="numeric"
              min={1}
              max={99}
              error={fieldError(formState.errors.order)}
              {...register('order', { valueAsNumber: true })}
            />
            <TextField
              label={t('backoffice.protocol.instructionKey')}
              autoComplete="off"
              error={fieldError(formState.errors.instructionKey)}
              {...register('instructionKey')}
            />
          </div>
          <p className="m-field__hint">
            {t('backoffice.protocol.preview', {
              text: t(instructionKey || 'photoProtocol.missingInstruction', {
                defaultValue: t('backoffice.protocol.keyNotFound'),
              }),
            })}
          </p>
          <div className="m-actions">
            <Button type="submit" variant="secondary" loading={update.isPending} disabled={!formState.isDirty}>
              {t('common.save')}
            </Button>
            {update.isSuccess ? <Badge tone="success">{t('backoffice.protocol.saved')}</Badge> : null}
          </div>
          <ErrorAlert error={update.error} />
        </form>
      </td>
    </tr>
  );
}

export function PhotoProtocolPage() {
  const { t } = useTranslation();
  const [country, setCountry] = useState(appConfig.country);
  const protocol = usePhotoProtocol(country, country.length === 2);

  return (
    <>
      <PageTitle subtitle={t('backoffice.protocol.subtitle')}>{t('backoffice.protocol.title')}</PageTitle>
      <TextField
        label={t('backoffice.protocol.country')}
        value={country}
        maxLength={2}
        onChange={(event) => {
          const value = event.target.value.toUpperCase();
          if (/^[A-Z]{0,2}$/.test(value)) setCountry(value);
        }}
      />
      {country.length === 2 ? (
        <>
          {protocol.isLoading ? <Loading /> : null}
          <ErrorAlert error={protocol.error} />
          {protocol.data && protocol.data.length === 0 ? <EmptyState>{t('backoffice.empty')}</EmptyState> : null}
          {protocol.data && protocol.data.length > 0 ? (
            <div className="m-table-wrap">
              <table className="m-table">
                <caption className="m-visually-hidden">{t('backoffice.protocol.title')}</caption>
                <thead>
                  <tr>
                    <th scope="col">{t('backoffice.protocol.slot')}</th>
                    <th scope="col" colSpan={4}>
                      {t('backoffice.protocol.settings')}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {protocol.data.map((slot) => (
                    <SlotRow key={slot.code} country={country} slot={slot} />
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </>
      ) : null}
    </>
  );
}
