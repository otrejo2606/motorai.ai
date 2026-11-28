import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Button, TextAreaField, useFieldError } from '@motoria/ui';

const rejectSchema = z.object({
  reason: z.string().trim().min(5, 'validation.reasonMin').max(500, 'validation.tooLong'),
});

type RejectValues = z.infer<typeof rejectSchema>;

export function RejectForm({ loading, onReject }: { loading: boolean; onReject: (reason: string) => void }) {
  const { t } = useTranslation();
  const fieldError = useFieldError();
  const { register, handleSubmit, formState } = useForm<RejectValues>({
    resolver: zodResolver(rejectSchema),
    defaultValues: { reason: '' },
  });
  return (
    <form className="m-form" noValidate onSubmit={handleSubmit((values) => onReject(values.reason))}>
      <TextAreaField
        label={t('backoffice.reject.reason')}
        hint={t('backoffice.reject.hint')}
        rows={3}
        maxLength={500}
        required
        error={fieldError(formState.errors.reason)}
        {...register('reason')}
      />
      <div className="m-actions">
        <Button type="submit" variant="danger" loading={loading}>
          {t('backoffice.reject.button')}
        </Button>
      </div>
    </form>
  );
}
