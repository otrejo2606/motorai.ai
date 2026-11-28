import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import {
  CURRENCIES,
  EQUIPMENT_CODES,
  PROVINCES,
  TRANSMISSIONS,
  useCopySuggestion,
  useListing,
  useSaveListing,
  type ListingDetail,
} from '@motoria/api';
import {
  Alert,
  Button,
  Card,
  CheckboxField,
  ErrorAlert,
  Loading,
  SelectField,
  TextAreaField,
  TextField,
  useEnumOptions,
  useFieldError,
} from '@motoria/ui';
import { appConfig } from '../../config';
import type { WizardProps } from './wizard';

const requiredNumber = { invalid_type_error: 'validation.number', required_error: 'validation.required' };
const KNOWN_EQUIPMENT = EQUIPMENT_CODES as readonly string[];

// Titulo y descripcion pueden ir vacios mientras el anuncio es borrador (D9.1 punto 6);
// se exigen al enviarlo a revision (el backend responde LISTING_CONTENT_INCOMPLETE).
const dataSchema = z.object({
  price: z.number(requiredNumber).positive('validation.positive').max(10000000000, 'validation.tooLarge'),
  currency: z.enum(CURRENCIES, { errorMap: () => ({ message: 'validation.required' }) }),
  mileage: z.number(requiredNumber).int('validation.integer').min(0, 'validation.min0').max(2000000, 'validation.tooLarge'),
  province: z.enum(PROVINCES, { errorMap: () => ({ message: 'validation.required' }) }),
  transmission: z.enum(TRANSMISSIONS, { errorMap: () => ({ message: 'validation.required' }) }),
  declaredEquipment: z.preprocess(
    (value) => (Array.isArray(value) ? value : typeof value === 'string' && value ? [value] : []),
    z.array(z.string().max(80, 'validation.tooLong')).max(50, 'validation.tooLong'),
  ),
  maintenanceHistory: z.string().trim().max(2000, 'validation.tooLong'),
  negotiable: z.boolean(),
  title: z.string().trim().max(120, 'validation.tooLong'),
  description: z.string().trim().max(1024, 'validation.tooLong'),
});

type DataValues = z.infer<typeof dataSchema>;

function toFormValues(listing: ListingDetail): Partial<DataValues> {
  return {
    price: listing.price,
    currency: listing.currency === 'USD' ? 'USD' : 'CRC',
    mileage: listing.mileage ?? undefined,
    province: listing.province ?? undefined,
    transmission: listing.transmission ?? undefined,
    declaredEquipment: (listing.declaredEquipment ?? []).filter((code) => KNOWN_EQUIPMENT.includes(code)),
    maintenanceHistory: listing.maintenanceHistory ?? '',
    negotiable: listing.negotiable,
    title: listing.title ?? '',
    description: listing.description ?? '',
  };
}

export function DeclaredDataStep({ vehicleId, listingId, go }: WizardProps) {
  const { t, i18n } = useTranslation();
  const fieldError = useFieldError();
  const listing = useListing(listingId);
  const save = useSaveListing();
  const copy = useCopySuggestion();
  const [saved, setSaved] = useState(false);
  const [suggested, setSuggested] = useState(false);

  const provinceOptions = useEnumOptions(PROVINCES, 'province');
  const transmissionOptions = useEnumOptions(TRANSMISSIONS, 'transmission');
  const currencyOptions = useEnumOptions(CURRENCIES, 'currency');

  const { register, handleSubmit, formState, setValue, reset } = useForm<DataValues>({
    resolver: zodResolver(dataSchema),
    defaultValues: {
      currency: appConfig.currency,
      declaredEquipment: [],
      maintenanceHistory: '',
      negotiable: false,
      title: '',
      description: '',
    },
  });

  // Al retomar un borrador, el dueno obtiene su anuncio completo con GET /listings/{id} (D9.1 punto 2).
  const loaded = listing.data;
  useEffect(() => {
    if (loaded) reset(toFormValues(loaded));
  }, [loaded, reset]);

  if (!vehicleId) return null;
  if (listingId && listing.isLoading) return <Loading />;

  // Datos de la ficha que el backend copia del vehiculo; no se declaran.
  const editable = !loaded || loaded.status === 'DRAFT' || loaded.status === 'REJECTED';

  const persist = (values: DataValues, next: boolean) => {
    setSaved(false);
    save.mutate(
      { id: listingId, body: { ...values, vehicleId } },
      {
        onSuccess: (detail) => {
          setSaved(true);
          setSuggested(false);
          go(next ? 'photos' : 'data', { listingId: detail?.id ?? listingId });
        },
      },
    );
  };

  const suggest = () => {
    if (!listingId) return;
    copy.mutate(
      { listingId, language: i18n.language },
      {
        onSuccess: (suggestion) => {
          setValue('title', suggestion.title, { shouldDirty: true, shouldValidate: true });
          setValue('description', suggestion.description, { shouldDirty: true, shouldValidate: true });
          setSuggested(true);
        },
      },
    );
  };

  return (
    <form className="m-form" noValidate onSubmit={handleSubmit((values) => persist(values, true))}>
      <ErrorAlert error={listing.error} />
      {!editable && loaded ? (
        <Alert tone="info" title={t('sell.data.notEditable', { status: t('listingStatus.' + loaded.status) })} />
      ) : null}
      <Card title={t('sell.data.title')}>
        <p>{t('sell.data.body')}</p>
        <div className="m-form-grid">
          <TextField
            label={t('sell.data.price')}
            type="number"
            inputMode="numeric"
            min={0}
            required
            error={fieldError(formState.errors.price)}
            {...register('price', { valueAsNumber: true })}
          />
          <SelectField
            label={t('sell.data.currency')}
            options={currencyOptions}
            required
            error={fieldError(formState.errors.currency)}
            {...register('currency')}
          />
          <TextField
            label={t('sell.data.mileage')}
            type="number"
            inputMode="numeric"
            min={0}
            required
            error={fieldError(formState.errors.mileage)}
            {...register('mileage', { valueAsNumber: true })}
          />
          <SelectField
            label={t('sell.data.province')}
            placeholder={t('common.select')}
            options={provinceOptions}
            required
            error={fieldError(formState.errors.province)}
            {...register('province')}
          />
          <SelectField
            label={t('sell.data.transmission')}
            placeholder={t('common.select')}
            options={transmissionOptions}
            required
            error={fieldError(formState.errors.transmission)}
            {...register('transmission')}
          />
        </div>
        <CheckboxField label={t('sell.data.negotiable')} {...register('negotiable')} />
      </Card>

      <Card title={t('sell.data.equipmentTitle')}>
        <fieldset className="m-fieldset">
          <legend>{t('sell.data.equipmentLegend')}</legend>
          <div className="m-form-grid">
            {EQUIPMENT_CODES.map((code) => (
              <CheckboxField key={code} label={t('equipment.' + code)} value={code} {...register('declaredEquipment')} />
            ))}
          </div>
        </fieldset>
        <TextAreaField
          label={t('sell.data.maintenanceHistory')}
          hint={t('sell.data.maintenanceHint')}
          maxLength={2000}
          error={fieldError(formState.errors.maintenanceHistory)}
          {...register('maintenanceHistory')}
        />
      </Card>

      <Card title={t('sell.data.copyTitle')}>
        <p>{t('sell.data.copyBody')}</p>
        <div className="m-actions">
          <Button variant="secondary" onClick={suggest} disabled={!listingId || !editable} loading={copy.isPending}>
            {t('sell.data.suggest')}
          </Button>
        </div>
        {!listingId ? <p className="m-field__hint">{t('sell.data.saveBeforeSuggest')}</p> : null}
        {suggested ? (
          <Alert tone="warning" title={t('sell.data.suggestedTitle')}>
            <p>{t('sell.data.suggestedBody')}</p>
          </Alert>
        ) : null}
        <ErrorAlert error={copy.error} />
        <TextField
          label={t('sell.data.listingTitle')}
          hint={t('sell.data.titleHint')}
          maxLength={120}
          error={fieldError(formState.errors.title)}
          {...register('title')}
        />
        <TextAreaField
          label={t('sell.data.description')}
          maxLength={1024}
          rows={8}
          error={fieldError(formState.errors.description)}
          {...register('description')}
        />
      </Card>

      <ErrorAlert error={save.error} />
      {saved ? <Alert tone="success" title={t('sell.data.saved')} /> : null}
      <div className="m-actions">
        <Button
          variant="secondary"
          loading={save.isPending}
          disabled={!editable}
          onClick={handleSubmit((values) => persist(values, false))}
        >
          {t('sell.data.saveDraft')}
        </Button>
        <Button type="submit" loading={save.isPending} disabled={!editable}>
          {t('sell.data.saveAndContinue')}
        </Button>
        <Button variant="secondary" onClick={() => go('plate')}>
          {t('common.back')}
        </Button>
      </div>
    </form>
  );
}
