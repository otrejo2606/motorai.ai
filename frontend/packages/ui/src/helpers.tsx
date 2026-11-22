import { useTranslation } from 'react-i18next';
import type { VehicleAttributeView } from '@motoria/api';
import { Badge } from './components/feedback';
import type { SelectOption } from './components/forms';

/** Opciones de un catalogo traducidas con la clave prefijo.VALOR. */
export function useEnumOptions(values: readonly string[], prefix: string): SelectOption[] {
  const { t } = useTranslation();
  return values.map((value) => ({ value, label: t(prefix + '.' + value) }));
}

/**
 * Los esquemas zod usan claves i18n como mensaje (por ejemplo validation.required);
 * esta funcion las traduce para mostrarlas bajo el campo.
 */
export function useFieldError() {
  const { t } = useTranslation();
  return (error: { message?: string } | undefined): string | undefined =>
    error?.message ? t(error.message) : undefined;
}

/**
 * transferBlockedReason llega como codigos de gravamen separados por coma (por ejemplo PLEDGE,SEIZURE);
 * se traducen con encumbrance.CODIGO.
 */
export function useBlockedReason() {
  const { t, i18n } = useTranslation();
  return (reason: string | null | undefined): string =>
    (reason ?? '')
      .split(',')
      .map((code) => code.trim())
      .filter(Boolean)
      .map((code) => (i18n.exists('encumbrance.' + code) ? t('encumbrance.' + code) : t('encumbrance.OTHER')))
      .join(', ');
}

/** Claves que nunca se muestran a terceros (hash de la cedula del propietario). */
const HIDDEN_KEYS = new Set(['OWNER_ID_HASH']);

export interface AttributeListProps {
  attributes: VehicleAttributeView[];
  /** Oculta tambien el nombre del propietario (vista publica). */
  hideOwner?: boolean;
  showSource?: boolean;
}

export function AttributeList({ attributes, hideOwner = false, showSource = false }: AttributeListProps) {
  const { t } = useTranslation();
  const visible = attributes.filter(
    (attribute) => !HIDDEN_KEYS.has(attribute.key) && !(hideOwner && attribute.key === 'OWNER_NAME'),
  );
  if (visible.length === 0) {
    return <p className="m-empty">{t('vehicle.noAttributes')}</p>;
  }
  return (
    <dl className="m-dl">
      {visible.map((attribute) => (
        <div key={attribute.key} className="m-dl__row">
          <dt>{t('attributes.' + attribute.key)}</dt>
          <dd>
            {attribute.value}{' '}
            {showSource ? (
              <Badge tone={attribute.verified ? 'success' : 'neutral'}>{t('attributeSource.' + attribute.source)}</Badge>
            ) : null}
          </dd>
        </div>
      ))}
    </dl>
  );
}
