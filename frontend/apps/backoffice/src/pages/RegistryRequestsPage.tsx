import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  REGISTRY_REQUEST_STATUSES,
  acceptFor,
  useAttachRequestedDocument,
  useRegistryRequests,
  useUploadFile,
  useUploadLimits,
  type RegistryRequestTask,
} from '@motoria/api';
import {
  Badge,
  Card,
  EmptyState,
  ErrorAlert,
  FileField,
  Loading,
  PageTitle,
  Pagination,
  SelectField,
  useEnumOptions,
  useFormat,
} from '@motoria/ui';
import { appConfig } from '../config';
import { pageFrom, statusFrom } from './params';

function RequestCard({ task }: { task: RegistryRequestTask }) {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);
  const limits = useUploadLimits();
  const upload = useUploadFile();
  const attach = useAttachRequestedDocument();
  const busy = upload.isPending || attach.isPending;

  return (
    <Card title={t('backoffice.registryRequests.cardTitle', { plate: task.plate })}>
      <p>
        <Badge tone={task.status === 'PENDING' ? 'warning' : task.status === 'FULFILLED' ? 'success' : 'neutral'}>
          {t('registryRequestStatus.' + task.status)}
        </Badge>{' '}
        {t('backoffice.registryRequests.createdAt', { date: format.dateTime(task.createdAt) })}
      </p>
      {task.acceptedFee ? (
        <p>{t('backoffice.registryRequests.fee', { fee: format.price(task.acceptedFee.amount, task.acceptedFee.currency) })}</p>
      ) : null}
      {task.fulfilledAt ? (
        <p>{t('backoffice.registryRequests.fulfilledAt', { date: format.dateTime(task.fulfilledAt) })}</p>
      ) : null}
      {task.status === 'PENDING' ? (
        <>
          <FileField
            label={t('backoffice.registryRequests.upload')}
            hint={t('sell.registry.uploadHint', { size: Math.round(limits.maxDocumentBytes / 1048576) })}
            accept={acceptFor('REGISTRY_DOCUMENT')}
            disabled={busy || attach.isSuccess}
            onFile={(file) =>
              upload.mutate(
                { file, kind: 'REGISTRY_DOCUMENT' },
                { onSuccess: (media) => attach.mutate({ requestId: task.id, mediaId: media.id }) },
              )
            }
          />
          {busy ? <Loading label={t('photoProtocol.uploading')} /> : null}
          {attach.isSuccess ? <Badge tone="success">{t('backoffice.registryRequests.done')}</Badge> : null}
          <ErrorAlert error={upload.error ?? attach.error} />
        </>
      ) : null}
    </Card>
  );
}

export function RegistryRequestsPage() {
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const status = statusFrom(searchParams, REGISTRY_REQUEST_STATUSES, 'PENDING');
  const page = pageFrom(searchParams);
  const requests = useRegistryRequests(status, page);
  const statusOptions = useEnumOptions(REGISTRY_REQUEST_STATUSES, 'registryRequestStatus');
  const items = requests.data?.items ?? [];

  return (
    <>
      <PageTitle subtitle={t('backoffice.registryRequests.subtitle')}>{t('backoffice.registryRequests.title')}</PageTitle>
      <SelectField
        label={t('backoffice.statusFilter')}
        options={statusOptions}
        value={status}
        onChange={(event) => setSearchParams({ status: event.target.value })}
      />
      {requests.isLoading ? <Loading /> : null}
      <ErrorAlert error={requests.error} />
      {requests.data && items.length === 0 ? <EmptyState>{t('backoffice.empty')}</EmptyState> : null}
      <div className="m-grid">
        {items.map((task) => (
          <RequestCard key={task.id} task={task} />
        ))}
      </div>
      {requests.data && requests.data.total > requests.data.size ? (
        <Pagination
          page={requests.data.page}
          size={requests.data.size}
          total={requests.data.total}
          onChange={(next) => setSearchParams({ status, page: String(next) })}
        />
      ) : null}
    </>
  );
}
