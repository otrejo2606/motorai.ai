import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { REGISTRY_ID_TYPES, usePersonRegistryLookup, type PersonRegistryLookup } from '@motoria/api';
import {
  Badge,
  Button,
  Card,
  CheckboxField,
  ErrorAlert,
  PageTitle,
  SelectField,
  TextAreaField,
  TextField,
  useEnumOptions,
  useFieldError,
  useFormat,
} from '@motoria/ui';
import { appConfig } from '../config';

/** Deja solo letras y numeros, como el backend (1-0234-0567 equivale a 102340567). */
function normalizeIdNumber(value: string): string {
  return value.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

const lookupSchema = z.object({
  idType: z.enum(REGISTRY_ID_TYPES),
  idNumber: z
    .string()
    .trim()
    .min(1, 'validation.required')
    .max(30, 'validation.tooLong')
    .refine((value) => {
      const length = normalizeIdNumber(value).length;
      return length >= 5 && length <= 20;
    }, 'validation.registryIdNumber'),
  reason: z.string().trim().min(10, 'validation.reasonMin10').max(300, 'validation.tooLong'),
  refresh: z.boolean(),
});

type LookupValues = z.infer<typeof lookupSchema>;

function LookupResult({ result }: { result: PersonRegistryLookup }) {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);

  if (!result.found) {
    return (
      <Card title={t('backoffice.personRegistry.result.title')}>
        <p>
          <Badge tone="error">{t('backoffice.personRegistry.result.notFound')}</Badge>
        </p>
        <p className="m-field__hint">
          {t('backoffice.personRegistry.result.checkedAt', { date: format.dateTime(result.checkedAt), provider: result.provider })}
        </p>
      </Card>
    );
  }
  return (
    <Card title={result.fullName ?? t('backoffice.personRegistry.result.title')}>
      {result.personType ? <p>{t('personType.' + result.personType)}</p> : null}
      {result.nationality ? <p>{t('backoffice.personRegistry.result.nationality', { value: result.nationality })}</p> : null}
      {result.documentExpiresOn ? (
        <p>
          {t('backoffice.personRegistry.result.expiresOn', { date: format.date(result.documentExpiresOn) })}{' '}
          {result.documentExpired ? <Badge tone="error">{t('backoffice.personRegistry.result.expired')}</Badge> : null}
        </p>
      ) : null}
      <p className="m-field__hint">
        {t('backoffice.personRegistry.result.checkedAt', { date: format.dateTime(result.checkedAt), provider: result.provider })}{' '}
        {result.fromCache ? t('backoffice.personRegistry.result.fromCache') : null}
      </p>
    </Card>
  );
}

export function PersonRegistryPage() {
  const { t } = useTranslation();
  const fieldError = useFieldError();
  const lookup = usePersonRegistryLookup();
  const idTypeOptions = useEnumOptions(REGISTRY_ID_TYPES, 'idType');
  const { register, handleSubmit, formState } = useForm<LookupValues>({
    resolver: zodResolver(lookupSchema),
    defaultValues: { idType: 'NATIONAL_ID', idNumber: '', reason: '', refresh: false },
  });

  return (
    <>
      <PageTitle subtitle={t('backoffice.personRegistry.subtitle')}>{t('backoffice.personRegistry.title')}</PageTitle>
      <form
        className="m-form"
        noValidate
        onSubmit={handleSubmit((values) =>
          lookup.mutate({
            idType: values.idType,
            idNumber: values.idNumber.trim(),
            reason: values.reason.trim(),
            refresh: values.refresh,
          }),
        )}
      >
        <SelectField
          label={t('backoffice.personRegistry.idType')}
          options={idTypeOptions}
          required
          error={fieldError(formState.errors.idType)}
          {...register('idType')}
        />
        <TextField
          label={t('backoffice.personRegistry.idNumber')}
          hint={t('backoffice.personRegistry.idNumberHint')}
          autoComplete="off"
          maxLength={30}
          required
          error={fieldError(formState.errors.idNumber)}
          {...register('idNumber')}
        />
        <TextAreaField
          label={t('backoffice.personRegistry.reason')}
          hint={t('backoffice.personRegistry.reasonHint')}
          rows={2}
          maxLength={300}
          required
          error={fieldError(formState.errors.reason)}
          {...register('reason')}
        />
        <CheckboxField label={t('backoffice.personRegistry.refresh')} {...register('refresh')} />
        <ErrorAlert error={lookup.error} />
        <div className="m-actions">
          <Button type="submit" loading={lookup.isPending}>
            {t('backoffice.personRegistry.submit')}
          </Button>
        </div>
      </form>
      {lookup.data ? <LookupResult result={lookup.data} /> : null}
    </>
  );
}
