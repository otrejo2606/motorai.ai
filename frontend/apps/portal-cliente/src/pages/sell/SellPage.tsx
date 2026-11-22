import { useCallback } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useProfile } from '@motoria/api';
import { Alert, PageTitle, Stepper } from '@motoria/ui';
import { SELL_STEPS, isSellStep, type SellStep, type WizardIds } from './wizard';
import { PlateStep } from './PlateStep';
import { RegistryStep } from './RegistryStep';
import { RegistryStatusStep } from './RegistryStatusStep';
import { VehicleReviewStep } from './VehicleReviewStep';
import { DeclaredDataStep } from './DeclaredDataStep';
import { PhotosStep } from './PhotosStep';
import { SubmitStep } from './SubmitStep';

function resolveStep(requested: SellStep, ids: WizardIds): SellStep {
  if (requested !== 'plate' && !ids.vehicleId) return 'plate';
  if ((requested === 'photos' || requested === 'submit') && !ids.listingId) return 'data';
  return requested;
}

export function SellPage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const profile = useProfile();

  const ids: WizardIds = {
    vehicleId: searchParams.get('vehicleId') ?? undefined,
    listingId: searchParams.get('listingId') ?? undefined,
  };
  const rawStep = searchParams.get('step');
  const step = resolveStep(isSellStep(rawStep) ? rawStep : 'plate', ids);

  const go = useCallback(
    (next: SellStep, nextIds?: WizardIds) => {
      const params = new URLSearchParams(searchParams);
      params.set('step', next);
      const merged = { ...ids, ...nextIds };
      if (merged.vehicleId) params.set('vehicleId', merged.vehicleId);
      if (merged.listingId) params.set('listingId', merged.listingId);
      setSearchParams(params);
      window.scrollTo({ top: 0 });
    },
    [ids, searchParams, setSearchParams],
  );

  const wizard = { vehicleId: ids.vehicleId, listingId: ids.listingId, go };
  const needsIdentity = profile.data !== undefined && profile.data.identityLevel === 'LEVEL_1';

  return (
    <>
      <PageTitle subtitle={t('sell.subtitle')}>{t('sell.title')}</PageTitle>
      <Stepper steps={SELL_STEPS.map((item) => t('sell.steps.' + item))} current={SELL_STEPS.indexOf(step)} />
      {needsIdentity ? (
        <Alert tone="warning" title={t('sell.identityRequiredTitle')}>
          <p>
            {t('sell.identityRequiredBody')} <Link to="/identity">{t('sell.identityLink')}</Link>
          </p>
        </Alert>
      ) : null}
      {step === 'plate' ? <PlateStep {...wizard} /> : null}
      {step === 'registry' ? <RegistryStep {...wizard} /> : null}
      {step === 'status' ? <RegistryStatusStep {...wizard} /> : null}
      {step === 'vehicle' ? <VehicleReviewStep {...wizard} /> : null}
      {step === 'data' ? <DeclaredDataStep {...wizard} /> : null}
      {step === 'photos' ? <PhotosStep {...wizard} /> : null}
      {step === 'submit' ? <SubmitStep {...wizard} /> : null}
    </>
  );
}
