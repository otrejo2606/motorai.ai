import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useProfile, useSubmitListingForReview } from '@motoria/api';
import { Alert, Button, Card, ErrorAlert } from '@motoria/ui';
import type { WizardProps } from './wizard';

export function SubmitStep({ listingId, go }: WizardProps) {
  const { t } = useTranslation();
  const profile = useProfile();
  const submit = useSubmitListingForReview();

  if (!listingId) return null;

  const identityOk = profile.data ? profile.data.identityLevel !== 'LEVEL_1' : true;

  if (submit.isSuccess) {
    return (
      <Alert tone="success" title={t('sell.submit.successTitle')}>
        <p>{t('sell.submit.successBody')}</p>
        <p>
          <Link to="/my-listings">{t('nav.myListings')}</Link>
        </p>
      </Alert>
    );
  }

  return (
    <Card title={t('sell.submit.title')}>
      <p>{t('sell.submit.body')}</p>
      <ul>
        <li>{t('sell.submit.checkRegistry')}</li>
        <li>{t('sell.submit.checkIdentity')}</li>
        <li>{t('sell.submit.checkPhotos')}</li>
      </ul>
      {!identityOk ? (
        <Alert tone="warning" title={t('sell.identityRequiredTitle')}>
          <p>
            <Link to="/identity">{t('sell.identityLink')}</Link>
          </p>
        </Alert>
      ) : null}
      <ErrorAlert error={submit.error} />
      <div className="m-actions">
        <Button loading={submit.isPending} disabled={!identityOk} onClick={() => submit.mutate(listingId)}>
          {t('sell.submit.button')}
        </Button>
        <Button variant="secondary" onClick={() => go('vehicle')}>
          {t('common.back')}
        </Button>
      </div>
    </Card>
  );
}
