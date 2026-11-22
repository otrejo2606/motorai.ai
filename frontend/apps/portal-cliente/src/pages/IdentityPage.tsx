import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  acceptFor,
  useLatestIdentityVerification,
  useStartIdentityVerification,
  useSubmitIdentityVerification,
  useUploadFile,
  useUploadLimits,
  type IdentityChallenge,
} from '@motoria/api';
import { Alert, Badge, Button, Card, ErrorAlert, FileField, Loading, PageTitle, useFormat } from '@motoria/ui';
import { appConfig } from '../config';

type Part = 'front' | 'back' | 'selfie';
const PARTS: Part[] = ['front', 'back', 'selfie'];

interface Uploaded {
  mediaId: string;
  previewUrl: string | null;
}

function PartUpload({ part, value, onUploaded }: { part: Part; value: Uploaded | undefined; onUploaded: (file: File, mediaId: string) => void }) {
  const { t } = useTranslation();
  const limits = useUploadLimits();
  const upload = useUploadFile();
  return (
    <article className="m-card">
      <h3 className="m-card__title">
        {t('identity.parts.' + part + '.title')}{' '}
        {value ? <Badge tone="success">{t('photoProtocol.uploaded')}</Badge> : null}
      </h3>
      <p>{t('identity.parts.' + part + '.instruction')}</p>
      {value?.previewUrl ? <img className="m-photo" src={value.previewUrl} alt={t('identity.parts.' + part + '.title')} /> : null}
      <FileField
        label={value ? t('photoProtocol.replace') : t('photoProtocol.choose')}
        hint={t('photoProtocol.identityFileHint', { size: Math.round(limits.maxPhotoBytes / 1048576) })}
        accept={acceptFor('IDENTITY_DOCUMENT')}
        capture={part === 'selfie' ? 'user' : 'environment'}
        disabled={upload.isPending}
        onFile={(file) =>
          upload.mutate({ file, kind: 'IDENTITY_DOCUMENT' }, { onSuccess: (media) => onUploaded(file, media.id) })
        }
      />
      {upload.isPending ? <Loading label={t('photoProtocol.uploading')} /> : null}
      <ErrorAlert error={upload.error} />
    </article>
  );
}

export function IdentityPage() {
  const { t } = useTranslation();
  const format = useFormat(appConfig.currency);
  const latest = useLatestIdentityVerification();
  const start = useStartIdentityVerification();
  const submit = useSubmitIdentityVerification();
  const [challenge, setChallenge] = useState<IdentityChallenge | null>(null);
  const [parts, setParts] = useState<Partial<Record<Part, Uploaded>>>({});
  const previews = useRef<string[]>([]);

  useEffect(
    () => () => {
      previews.current.forEach((url) => URL.revokeObjectURL(url));
    },
    [],
  );

  // Retoma un reto vigente que aun no se envio.
  useEffect(() => {
    const data = latest.data;
    if (challenge || !data || data.status !== 'CHALLENGE_ISSUED' || !data.challengeCode || !data.expiresAt) return;
    if (new Date(data.expiresAt).getTime() > Date.now()) {
      setChallenge({ id: data.id, challengeCode: data.challengeCode, expiresAt: data.expiresAt });
    }
  }, [latest.data, challenge]);

  if (latest.isLoading) return <Loading />;

  const status = latest.data?.status;
  const onUploaded = (part: Part) => (file: File, mediaId: string) => {
    const canPreview = file.type === 'image/jpeg' || file.type === 'image/png';
    const previewUrl = canPreview ? URL.createObjectURL(file) : null;
    if (previewUrl) previews.current.push(previewUrl);
    setParts((current) => ({ ...current, [part]: { mediaId, previewUrl } }));
  };

  const front = parts.front;
  const back = parts.back;
  const selfie = parts.selfie;
  const ready = Boolean(challenge && front && back && selfie);

  const onSubmit = () => {
    if (!challenge || !front || !back || !selfie) return;
    submit.mutate(
      {
        id: challenge.id,
        body: { documentFrontMediaId: front.mediaId, documentBackMediaId: back.mediaId, selfieMediaId: selfie.mediaId },
      },
      {
        onSuccess: () => {
          setChallenge(null);
          setParts({});
        },
      },
    );
  };

  return (
    <>
      <PageTitle subtitle={t('identity.subtitle')}>{t('identity.title')}</PageTitle>
      <ErrorAlert error={latest.error} />

      {status === 'APPROVED' ? (
        <Alert tone="success" title={t('identity.approvedTitle')}>
          <p>{t('identity.approvedBody')}</p>
        </Alert>
      ) : null}
      {status === 'SUBMITTED' ? (
        <Alert tone="info" title={t('identity.submittedTitle')}>
          <p>{t('identity.submittedBody')}</p>
        </Alert>
      ) : null}
      {status === 'REJECTED' ? (
        <Alert tone="error" title={t('identity.rejectedTitle')}>
          {latest.data?.rejectionReason ? <p className="m-pre">{latest.data.rejectionReason}</p> : null}
        </Alert>
      ) : null}

      {status !== 'APPROVED' && status !== 'SUBMITTED' && !challenge ? (
        <Card
          title={t('identity.startTitle')}
          actions={
            <Button
              loading={start.isPending}
              onClick={() => start.mutate(undefined, { onSuccess: (data) => setChallenge(data) })}
            >
              {t('identity.start')}
            </Button>
          }
        >
          <p>{t('identity.startBody')}</p>
          <ErrorAlert error={start.error} />
        </Card>
      ) : null}

      {challenge ? (
        <>
          <Card title={t('identity.codeTitle')}>
            <p>{t('identity.codeBody')}</p>
            <p className="m-code" aria-label={t('identity.codeAria', { code: challenge.challengeCode.split('').join(' ') })}>
              {challenge.challengeCode}
            </p>
            <p>{t('identity.expiresAt', { date: format.dateTime(challenge.expiresAt) })}</p>
          </Card>
          <div className="m-grid">
            {PARTS.map((part) => (
              <PartUpload key={part} part={part} value={parts[part]} onUploaded={onUploaded(part)} />
            ))}
          </div>
          <ErrorAlert error={submit.error} />
          <div className="m-actions">
            <Button disabled={!ready} loading={submit.isPending} onClick={onSubmit}>
              {t('identity.submit')}
            </Button>
          </div>
        </>
      ) : null}
    </>
  );
}
