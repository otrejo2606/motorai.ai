import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageTitle } from '@motoria/ui';

export function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <>
      <PageTitle>{t('notFound.title')}</PageTitle>
      <p>
        <Link to="/">{t('notFound.back')}</Link>
      </p>
    </>
  );
}
