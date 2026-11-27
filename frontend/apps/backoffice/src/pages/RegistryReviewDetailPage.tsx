import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  useApproveRegistryReview,
  useCorrectRegistryAttribute,
  useRegistryReview,
  useRejectRegistryReview,
  type ReviewAttribute,
} from '@motoria/api';
import { Alert, Badge, Button, Card, ErrorAlert, Loading, PageTitle, useFieldError, useFormat } from '@motoria/ui';
import { RejectForm } from '../components/RejectForm';
import { appConfig } from '../config';

const fieldSchema = z.object({
  value: z.string().trim().min(1, 'validation.required').max(500, 'validation.tooLong'),
});

type FieldValues = z.infer<typeof fieldSchema>;

/**
 * Para OWNER_ID_HASH el analista escribe la cedula del propietario en claro y el backend guarda solo su hash
 * (D10). Por eso el campo empieza vacio y no muestra el hash.
 */
const OWNER_ID_KEY = 'OWNER_ID_HASH';

function scrollToPage(page: number) {
  const element = document.getElementById('doc-page-' + String(page));
  if (element) {
    element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    element.focus();
  }
}

function AttributeRow({
  vehicleId,
  attribute,
  editable,
}: {
  vehicleId: string;
  attribute: ReviewAttribute;
  editable: boolean;
}) {
  const { t } = useTranslation();
  const fieldError = useFieldError();
  const format = useFormat(appConfig.currency);
  const correct = useCorrectRegistryAttribute();
  const isOwnerId = attribute.key === OWNER_ID_KEY;
  const { register, handleSubmit, formState, reset } = useForm<FieldValues>({
    resolver: zodResolver(fieldSchema),
    values: { value: isOwnerId ? '' : attribute.value },
  });
  const confidence = attribute.confidence;
  const low = confidence !== null && confidence < appConfig.confidenceWarning;
  const label = t('attributes.' + attribute.key);
  const inputId = 'field-' + attribute.key;

  return (
    <tr className={low ? 'm-row-warning' : undefined}>
      <th scope="row">{label}</th>
      <td>
        {isOwnerId ? <p className="m-field__hint">{t('backoffice.review.ownerIdStored')}</p> : null}
        {editable ? (
          <form
            className="m-form"
            noValidate
            onSubmit={handleSubmit((values) =>
              correct.mutate(
                { vehicleId, key: attribute.key, value: values.value },
                { onSuccess: () => (isOwnerId ? reset({ value: '' }) : undefined) },
              ),
            )}
          >
            <label className="m-visually-hidden" htmlFor={inputId}>
              {label}
            </label>
            <input
              id={inputId}
              className="m-input"
              autoComplete="off"
              placeholder={isOwnerId ? t('backoffice.review.ownerIdPlaceholder') : undefined}
              aria-invalid={formState.errors.value ? true : undefined}
              {...register('value')}
            />
            {formState.errors.value ? (
              <p className="m-field__error" role="alert">
                {fieldError(formState.errors.value)}
              </p>
            ) : null}
            {attribute.aiValue && attribute.aiValue !== attribute.value && !isOwnerId ? (
              <p className="m-field__hint">{t('backoffice.review.aiValue', { value: attribute.aiValue })}</p>
            ) : null}
            {attribute.correctedBy ? (
              <p className="m-field__hint">{t('backoffice.review.correctedBy', { user: attribute.correctedBy })}</p>
            ) : null}
            <Button type="submit" variant="secondary" loading={correct.isPending} disabled={!formState.isDirty}>
              {t('backoffice.review.correct')}
            </Button>
            <ErrorAlert error={correct.error} />
            {correct.isSuccess ? <Badge tone="success">{t('backoffice.review.corrected')}</Badge> : null}
          </form>
        ) : (
          <span>{isOwnerId ? '' : attribute.value}</span>
        )}
      </td>
      <td>
        {confidence !== null ? (
          <Badge tone={low ? 'warning' : 'success'}>{format.percent(confidence)}</Badge>
        ) : (
          <Badge tone="neutral">{t('backoffice.review.noConfidence')}</Badge>
        )}
        {low ? <div>{t('backoffice.review.lowConfidence')}</div> : null}
      </td>
      <td>
        {attribute.page ? (
          <Button variant="link" onClick={() => scrollToPage(attribute.page ?? 1)}>
            {t('backoffice.review.page', { page: attribute.page })}
          </Button>
        ) : (
          t('backoffice.review.noPage')
        )}
      </td>
      <td>{t('attributeSource.' + attribute.source)}</td>
    </tr>
  );
}

export function RegistryReviewDetailPage() {
  const { vehicleId } = useParams();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const format = useFormat(appConfig.currency);
  const review = useRegistryReview(vehicleId);
  const approve = useApproveRegistryReview();
  const reject = useRejectRegistryReview();

  if (review.isLoading) return <Loading />;
  if (!review.data || !vehicleId) {
    return (
      <>
        <ErrorAlert error={review.error} />
        <Link to="/registry-reviews">{t('backoffice.back')}</Link>
      </>
    );
  }

  const data = review.data;
  const inReview = data.status === 'REGISTRY_IN_REVIEW';
  // Datos del Registro Nacional por convenio (Credid): no hay documento, paginas ni confianza.
  const agreement = data.registrySource === 'AGREEMENT';
  const done = () => navigate('/registry-reviews', { replace: true });

  return (
    <>
      <p>
        <Link to="/registry-reviews">{t('backoffice.back')}</Link>
      </p>
      <PageTitle
        subtitle={
          agreement
            ? t('backoffice.review.agreementSubtitle')
            : data.documentDate
              ? t('backoffice.review.subtitle', { date: format.date(data.documentDate) })
              : t('backoffice.review.noDocumentDate')
        }
      >
        {t('backoffice.review.title', { plate: data.plate })}
      </PageTitle>
      <p>
        <Badge tone={inReview ? 'warning' : 'neutral'}>{t('vehicleStatus.' + data.status)}</Badge>{' '}
        {data.reviewReason ? <Badge tone="info">{t('reviewReason.' + data.reviewReason)}</Badge> : null}
      </p>
      {data.reviewReason === 'PLATE_MISMATCH' || data.reviewReason === 'EXTRACTION_FAILED' ? (
        <Alert tone="error" title={t('reviewReason.' + data.reviewReason)}>
          <p>{t('backoffice.review.noDataApplied')}</p>
        </Alert>
      ) : null}
      {agreement ? (
        <Alert tone="info" title={t('backoffice.review.agreementTitle')}>
          <p>{t('backoffice.review.agreementNoDocument')}</p>
        </Alert>
      ) : null}
      {data.rejectionReason ? (
        <Alert tone="error" title={t('backoffice.review.rejectedWith')}>
          <p className="m-pre">{data.rejectionReason}</p>
        </Alert>
      ) : null}

      <div className="m-split">
        <section aria-label={t('backoffice.review.documentPages')} className="m-scroll-pane">
          {data.pages.length === 0 ? (
            <p className="m-empty">
              {agreement ? t('backoffice.review.agreementNoDocument') : t('backoffice.review.noPages')}
            </p>
          ) : null}
          {data.pages.map((page) => (
            <figure key={page.page} id={'doc-page-' + String(page.page)} tabIndex={-1}>
              <img
                className="m-doc-page"
                src={page.imageUrl}
                alt={t('backoffice.review.pageAlt', { page: page.page })}
                referrerPolicy="no-referrer"
              />
              <figcaption>{t('backoffice.review.page', { page: page.page })}</figcaption>
            </figure>
          ))}
          {data.pages.length > 0 ? (
            <Button variant="secondary" onClick={() => void review.refetch()}>
              {t('backoffice.review.reloadPages')}
            </Button>
          ) : null}
        </section>

        <section aria-label={t('backoffice.review.fields')}>
          <Card title={t('backoffice.review.fields')}>
            {data.attributes.length === 0 ? <p className="m-empty">{t('backoffice.review.noAttributes')}</p> : null}
            {data.attributes.length > 0 ? (
              <div className="m-table-wrap">
                <table className="m-table">
                  <thead>
                    <tr>
                      <th scope="col">{t('backoffice.columns.field')}</th>
                      <th scope="col">{t('backoffice.columns.value')}</th>
                      <th scope="col">{t('backoffice.columns.confidence')}</th>
                      <th scope="col">{t('backoffice.columns.page')}</th>
                      <th scope="col">{t('backoffice.columns.source')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.attributes.map((attribute) => (
                      <AttributeRow key={attribute.key} vehicleId={vehicleId} attribute={attribute} editable={inReview} />
                    ))}
                  </tbody>
                </table>
              </div>
            ) : null}
          </Card>

          <Card title={t('backoffice.review.encumbrances')}>
            {data.encumbrances.length === 0 ? (
              <p>{t('backoffice.review.noEncumbrances')}</p>
            ) : (
              <ul>
                {data.encumbrances.map((item, index) => (
                  <li key={item.type + String(index)}>
                    <strong>{t('encumbrance.' + item.type)}</strong>
                    {item.description ? ': ' + item.description : ''}{' '}
                    {item.blocksTransfer ? <Badge tone="error">{t('backoffice.review.blocksTransfer')}</Badge> : null}{' '}
                    {item.page ? (
                      <Button variant="link" onClick={() => scrollToPage(item.page ?? 1)}>
                        {t('backoffice.review.page', { page: item.page })}
                      </Button>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </Card>

          {inReview ? (
            <Card title={t('backoffice.review.decision')}>
              <ErrorAlert error={approve.error ?? reject.error} />
              <div className="m-actions">
                <Button loading={approve.isPending} onClick={() => approve.mutate(vehicleId, { onSuccess: done })}>
                  {t('backoffice.review.approve')}
                </Button>
              </div>
              <RejectForm
                loading={reject.isPending}
                onReject={(reason) => reject.mutate({ vehicleId, reason }, { onSuccess: done })}
              />
            </Card>
          ) : null}
        </section>
      </div>
    </>
  );
}
