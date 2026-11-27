import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { useCreateVehicle } from '@motoria/api';
import { Button, Card, ErrorAlert, TextField, useFieldError } from '@motoria/ui';
import { appConfig } from '../../config';
import type { WizardProps } from './wizard';

const plateSchema = z.object({
  plate: z
    .string()
    .trim()
    .min(1, 'validation.required')
    .max(12, 'validation.tooLong')
    .regex(/^[A-Za-z0-9 -]+$/, 'validation.plateFormat'),
});

type PlateValues = z.infer<typeof plateSchema>;

/**
 * Tras la placa siempre siguen los datos declarados y las fotos; el estado registral del vehiculo (que puede existir
 * ya) se resuelve despues, en la consulta al registro (add-consulta-credid-vehiculos).
 */

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
      { onSuccess: (vehicle) => go('data', { vehicleId: vehicle.id }) },
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
