import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { ID_TYPES, useProfile, useUpdateProfile, type ProfileUpdateRequest } from '@motoria/api';
import {
  Alert,
  Badge,
  Button,
  Card,
  ErrorAlert,
  Loading,
  PageTitle,
  SelectField,
  TextField,
  useEnumOptions,
  useFieldError,
} from '@motoria/ui';
import { appConfig } from '../config';

const ID_PATTERNS: Record<(typeof ID_TYPES)[number], RegExp> = {
  NATIONAL_ID: /^[0-9]{9}$/,
  RESIDENT_ID: /^[0-9]{11,12}$/,
  PASSPORT: /^[A-Za-z0-9]{6,20}$/,
};

// idType e idNumber son opcionales (D9.1 punto 8): si la cedula queda vacia, se conserva la guardada.
const profileSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(1, 'validation.required')
      .min(3, 'validation.tooShort3')
      .max(200, 'validation.tooLong'),
    idType: z.union([z.enum(ID_TYPES), z.literal('')]),
    idNumber: z.string().trim(),
    phone: z
      .string()
      .trim()
      .regex(/^([+]?[0-9 -]{8,20})?$/, 'validation.phone'),
    countryCode: z.string().trim().regex(/^[A-Z]{2}$/, 'validation.country'),
  })
  .superRefine((values, ctx) => {
    if (!values.idNumber) return;
    if (!values.idType) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['idType'], message: 'validation.required' });
      return;
    }
    if (!ID_PATTERNS[values.idType].test(values.idNumber)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['idNumber'], message: 'validation.idNumber.' + values.idType });
    }
  });

type ProfileValues = z.infer<typeof profileSchema>;

function toRequest(values: ProfileValues): ProfileUpdateRequest {
  return {
    fullName: values.fullName,
    idType: values.idNumber && values.idType ? values.idType : undefined,
    idNumber: values.idNumber || undefined,
    phone: values.phone || undefined,
    countryCode: values.countryCode,
  };
}

export function ProfilePage() {
  const { t } = useTranslation();
  const fieldError = useFieldError();
  const profile = useProfile();
  const update = useUpdateProfile();
  const idTypeOptions = useEnumOptions(ID_TYPES, 'idType');
  const { register, handleSubmit, formState, reset } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: '', idType: '', idNumber: '', phone: '', countryCode: appConfig.country },
  });

  useEffect(() => {
    if (!profile.data) return;
    reset({
      fullName: profile.data.fullName ?? '',
      idType: profile.data.idType ?? '',
      // El backend nunca devuelve la cedula completa; vacia, se conserva la guardada.
      idNumber: '',
      phone: profile.data.phone ?? '',
      countryCode: profile.data.countryCode ?? appConfig.country,
    });
  }, [profile.data, reset]);

  if (profile.isLoading) return <Loading />;

  return (
    <>
      <PageTitle>{t('profile.title')}</PageTitle>
      <ErrorAlert error={profile.error} />
      {profile.data ? (
        <Card title={t('profile.identityTitle')}>
          <p>
            <Badge tone={profile.data.identityLevel === 'LEVEL_1' ? 'warning' : 'success'}>
              {t('identityLevel.' + profile.data.identityLevel)}
            </Badge>
          </p>
          {profile.data.identityLevel === 'LEVEL_1' ? (
            <p>
              <Link to="/identity">{t('profile.verifyIdentity')}</Link>
            </p>
          ) : null}
        </Card>
      ) : null}
      <Card title={t('profile.formTitle')}>
        <form className="m-form" noValidate onSubmit={handleSubmit((values) => update.mutate(toRequest(values)))}>
          <div className="m-form-grid">
            <TextField
              label={t('profile.fullName')}
              autoComplete="name"
              required
              error={fieldError(formState.errors.fullName)}
              {...register('fullName')}
            />
            <SelectField
              label={t('profile.idType')}
              placeholder={t('common.select')}
              options={idTypeOptions}
              error={fieldError(formState.errors.idType)}
              {...register('idType')}
            />
            <TextField
              label={t('profile.idNumber')}
              hint={
                profile.data?.idNumberLast4
                  ? t('profile.idNumberHint', { last4: profile.data.idNumberLast4 })
                  : t('profile.idNumberEmptyHint')
              }
              autoComplete="off"
              inputMode="text"
              error={fieldError(formState.errors.idNumber)}
              {...register('idNumber')}
            />
            <TextField
              label={t('profile.phone')}
              type="tel"
              autoComplete="tel"
              error={fieldError(formState.errors.phone)}
              {...register('phone')}
            />
            <TextField
              label={t('profile.countryCode')}
              autoComplete="country"
              maxLength={2}
              required
              error={fieldError(formState.errors.countryCode)}
              {...register('countryCode')}
            />
          </div>
          <ErrorAlert error={update.error} />
          {update.isSuccess ? <Alert tone="success" title={t('profile.saved')} /> : null}
          <div className="m-actions">
            <Button type="submit" loading={update.isPending}>
              {t('common.save')}
            </Button>
          </div>
        </form>
      </Card>
    </>
  );
}
