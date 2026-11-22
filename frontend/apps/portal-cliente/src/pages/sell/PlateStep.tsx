import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { useCreateVehicle, type Vehicle } from '@motoria/api';
import { Button, Card, ErrorAlert, TextField, useFieldError } from '@motoria/ui';
import { appConfig } from '../../config';
import type { SellStep, WizardProps } from './wizard';

const plateSchema = z.object({
  plate: z
    .string()
    .trim()
    .min(1, 'validation.required')
    .max(12, 'validation.tooLong')
    .regex(/^[A-Za-z0-9 -]+$/, 'validation.plateFormat'),
});

type PlateValues = z.infer<typeof plateSchema>;

/** Siguiente paso segun el estado registral del vehiculo devuelto (se reutiliza si ya existe). */
export function stepForVehicle(vehicle: Vehicle): SellStep {
  if (vehicle.status === 'REGISTRY_VERIFIED') return 'vehicle';
  if (vehicle.status === 'REGISTRY_IN_REVIEW') return 'status';
  return 'registry';
}

export function PlateStep({ go }: WizardProps) {
  const { t } = useTranslation();
  const fieldError = useFieldError();
  const createVehicle = useCreateVehicle();
  const { register, handleSubmit, formState } = useForm<PlateValues>({
    resolver: zodResolver(plateSchema),
    defaultValues: { plate: '' },
  });

  const onSubmit = (values: PlateValues) => {
    createVehicle.mutate(
      { countryCode: appConfig.country, plate: values.plate.toUpperCase() },
      { onSuccess: (vehicle) => go(stepForVehicle(vehicle), { vehicleId: vehicle.id }) },
    );
  };

  return (
    <Card title={t('sell.plate.title')}>
      <form className="m-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <p>{t('sell.plate.body')}</p>
        <TextField
          label={t('sell.plate.label')}
          hint={t('sell.plate.hint')}
          autoComplete="off"
          autoCapitalize="characters"
          required
          error={fieldError(formState.errors.plate)}
          {...register('plate')}
        />
        <ErrorAlert error={createVehicle.error} />
        <div className="m-actions">
          <Button type="submit" loading={createVehicle.isPending}>
            {t('common.continue')}
          </Button>
        </div>
      </form>
    </Card>
  );
}
