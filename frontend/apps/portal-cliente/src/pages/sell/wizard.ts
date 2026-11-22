export const SELL_STEPS = ['plate', 'registry', 'status', 'vehicle', 'data', 'photos', 'submit'] as const;
export type SellStep = (typeof SELL_STEPS)[number];

export interface WizardIds {
  vehicleId?: string;
  listingId?: string;
}

/** Estado del asistente; los ids viajan en la URL (no son secretos) para poder retomar el flujo. */
export interface WizardProps {
  vehicleId?: string;
  listingId?: string;
  go: (step: SellStep, ids?: WizardIds) => void;
}

export function isSellStep(value: string | null): value is SellStep {
  return value !== null && (SELL_STEPS as readonly string[]).includes(value);
}

/** Ruta para retomar un anuncio desde "Mis anuncios". */
export function resumePath(step: SellStep, ids: WizardIds): string {
  const params = new URLSearchParams({ step });
  if (ids.vehicleId) params.set('vehicleId', ids.vehicleId);
  if (ids.listingId) params.set('listingId', ids.listingId);
  return '/sell?' + params.toString();
}
