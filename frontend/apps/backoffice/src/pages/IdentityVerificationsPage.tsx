import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  IDENTITY_VERIFICATION_STATUSES,
  useApproveIdentityVerification,
  useIdentityVerifications,
  useRejectIdentityVerification,
  type BackofficeIdentityVerification,
} from '@motoria/api';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorAlert,
  Loading,
  PageTitle,
  Pagination,
  SelectField,
  useEnumOptions,
  useFormat,
} from '@motoria/ui';
import { PrivateImage } from '../components/PrivateImage';
import { RegistryCheckPanel } from '../components/RegistryCheckPanel';
import { RejectForm } from '../components/RejectForm';
import { appConfig } from '../config';
import { pageFrom, statusFrom } from './params';

function VerificationCard({ item }: { item: BackofficeIdentityVerification }) {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);
  const approve = useApproveIdentityVerification();
  const reject = useRejectIdentityVerification();

  return (
    <Card title={item.fullName ?? t('backoffice.identity.unnamed')}>
      {item.idType || item.idNumberLast4 ? (
        <p>
          {item.idType ? t('idType.' + item.idType) : ''}{' '}
          {item.idNumberLast4 ? t('backoffice.identity.idLast4', { last4: item.idNumberLast4 }) : ''}
        </p>
      ) : null}
      <p>
        <Badge tone={item.status === 'SUBMITTED' ? 'warning' : 'neutral'}>{t('identityStatus.' + item.status)}</Badge>{' '}
        {item.submittedAt ? t('backoffice.identity.submittedAt', { date: format.dateTime(item.submittedAt) }) : null}
      </p>
      <p>{t('backoffice.identity.expectedCode')}</p>
      <p className="m-code">{item.challengeCode ?? ''}</p>
      <p className="m-field__hint">{t('backoffice.identity.instructions')}</p>
      <RegistryCheckPanel item={item} />
      <div className="m-grid">
        <figure>
          <figcaption>{t('identity.parts.front.title')}</figcaption>
          <PrivateImage mediaId={item.documentFrontMediaId} alt={t('identity.parts.front.title')} />
        </figure>
        <figure>
          <figcaption>{t('identity.parts.back.title')}</figcaption>
          <PrivateImage mediaId={item.documentBackMediaId} alt={t('identity.parts.back.title')} />
        </figure>
        <figure>
          <figcaption>{t('identity.parts.selfie.title')}</figcaption>
          <PrivateImage mediaId={item.selfieMediaId} alt={t('identity.parts.selfie.title')} />
        </figure>
      </div>
      {item.status === 'SUBMITTED' ? (
        <>
          <ErrorAlert error={approve.error ?? reject.error} />
          <div className="m-actions">
            <Button loading={approve.isPending} onClick={() => approve.mutate(item.id)}>
              {t('backoffice.identity.approve')}
            </Button>
          </div>
          <RejectForm loading={reject.isPending} onReject={(reason) => reject.mutate({ id: item.id, reason })} />
        </>
      ) : null}
      {item.reviewedAt ? (
        <p className="m-field__hint">
          {t('backoffice.identity.reviewedAt', { date: format.dateTime(item.reviewedAt), user: item.reviewedBy ?? '' })}
        </p>
      ) : null}
      {item.rejectionReason ? <p className="m-pre">{item.rejectionReason}</p> : null}
    </Card>
  );
}

export function IdentityVerificationsPage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const status = statusFrom(searchParams, IDENTITY_VERIFICATION_STATUSES, 'SUBMITTED');
  const page = pageFrom(searchParams);
  const verifications = useIdentityVerifications(status, page);
  const statusOptions = useEnumOptions(IDENTITY_VERIFICATION_STATUSES, 'identityStatus');
  const items = verifications.data?.items ?? [];

  return (
    <>
      <PageTitle subtitle={t('backoffice.identity.subtitle')}>{t('backoffice.identity.title')}</PageTitle>
      <SelectField
        label={t('backoffice.statusFilter')}
        options={statusOptions}
        value={status}
        onChange={(event) => setSearchParams({ status: event.target.value })}
      />
      {verifications.isLoading ? <Loading /> : null}
      <ErrorAlert error={verifications.error} />
      {verifications.data && items.length === 0 ? <EmptyState>{t('backoffice.empty')}</EmptyState> : null}
      {items.map((item) => (
        <VerificationCard key={item.id} item={item} />
      ))}
      {verifications.data && verifications.data.total > verifications.data.size ? (
        <Pagination
          page={verifications.data.page}
          size={verifications.data.size}
          total={verifications.data.total}
          onChange={(next) => setSearchParams({ status, page: String(next) })}
        />
      ) : null}
    </>
  );
}
