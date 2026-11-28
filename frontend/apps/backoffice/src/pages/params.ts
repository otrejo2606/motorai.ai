/** Estado de la bandeja leido de la URL; si no es un valor conocido, se usa el de por defecto. */
export function statusFrom<T extends string>(params: URLSearchParams, values: readonly T[], fallback: T): T {
  const requested = params.get('status');
  return requested !== null && (values as readonly string[]).includes(requested) ? (requested as T) : fallback;
}

/** Pagina de la bandeja (base 0) leida de la URL. */
export function pageFrom(params: URLSearchParams): number {
  const value = Number(params.get('page') ?? '0');
  return Number.isInteger(value) && value > 0 ? value : 0;
}
