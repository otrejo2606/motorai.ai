// Tipos del contrato de API de F1 (design D9, D9.1 y D10 de add-mvp-placa-publicacion),
// alineados con los DTO de backend/src/main/java/ai/motoria/*/dto.

export type Uuid = string;
export type IsoDateTime = string;
/** Fecha sin hora (LocalDate), por ejemplo 2026-11-21. */
export type IsoDate = string;

export interface ValidationViolation {
  field: string;
  message: string;
}

/**
 * ErrorResponse del backend. En errores de publicacion, details trae primero los codigos de regla
 * y despues los codigos de las tomas de foto que faltan. VALIDATION_ERROR trae violations.
 */
export interface ErrorResponse {
  code: string;
  message?: string;
  timestamp?: IsoDateTime;
  details?: string[];
  violations?: ValidationViolation[];
}

/** Toda lista del backend es una pagina numerada desde 0 (D9.1 punto 3). */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface PageParams {
  page?: number;
  size?: number;
}

// ---------- Catalogos ----------

export const LISTING_STATUSES = ['DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'SOLD', 'EXPIRED', 'REJECTED'] as const;
export type ListingStatus = (typeof LISTING_STATUSES)[number];

export const TRUST_LEVELS = ['NONE', 'REGISTRY_VERIFIED'] as const;
export type TrustLevel = (typeof TRUST_LEVELS)[number];

export const LISTING_SORTS = ['RELEVANCE', 'PRICE_ASC', 'PRICE_DESC', 'YEAR_DESC'] as const;
export type ListingSort = (typeof LISTING_SORTS)[number];

export const PROVINCES = ['SAN_JOSE', 'ALAJUELA', 'CARTAGO', 'HEREDIA', 'GUANACASTE', 'PUNTARENAS', 'LIMON'] as const;
export type Province = (typeof PROVINCES)[number];

export const FUELS = ['GASOLINE', 'DIESEL', 'HYBRID', 'PLUG_IN_HYBRID', 'ELECTRIC', 'LPG', 'OTHER'] as const;
export type Fuel = (typeof FUELS)[number];

export const TRANSMISSIONS = ['MANUAL', 'AUTOMATIC', 'CVT', 'DUAL_CLUTCH'] as const;
export type Transmission = (typeof TRANSMISSIONS)[number];

export const CURRENCIES = ['CRC', 'USD'] as const;
export type Currency = (typeof CURRENCIES)[number];

/** Codigos de equipo que ofrece el formulario; el backend los guarda como texto libre (hasta 80 caracteres). */
export const EQUIPMENT_CODES = [
  'AIR_CONDITIONING',
  'POWER_STEERING',
  'POWER_WINDOWS',
  'ABS',
  'AIRBAGS',
  'ALLOY_WHEELS',
  'BACKUP_CAMERA',
  'BLUETOOTH',
  'CRUISE_CONTROL',
  'LEATHER_SEATS',
  'SUNROOF',
  'NAVIGATION',
] as const;
export type EquipmentCode = (typeof EQUIPMENT_CODES)[number];

export const VEHICLE_STATUSES = ['REGISTRY_PENDING', 'REGISTRY_IN_REVIEW', 'REGISTRY_VERIFIED', 'REGISTRY_REJECTED'] as const;
export type VehicleStatus = (typeof VEHICLE_STATUSES)[number];

export const REVIEW_REASONS = [
  'MANUAL_REVIEW_DEFAULT',
  'LOW_CONFIDENCE',
  'DOCUMENT_TOO_OLD',
  'PLATE_MISMATCH',
  'MISSING_REQUIRED',
  'EXTRACTION_FAILED',
  'AGREEMENT_REVIEW',
] as const;
export type ReviewReason = (typeof REVIEW_REASONS)[number];

/** Origen de los datos registrales: informe PDF o convenio con el Registro Nacional (Credid). */
export const REGISTRY_SOURCES = ['DOCUMENT', 'AGREEMENT'] as const;
export type RegistrySource = (typeof REGISTRY_SOURCES)[number];

/** Resultado de la consulta por placa; FOUND y REUSED traen datos, el resto pide el informe PDF. */
export const REGISTRY_LOOKUP_STATUSES = [
  'FOUND',
  'REUSED',
  'NOT_FOUND',
  'UNAVAILABLE',
  'NOT_CONFIGURED',
  'QUOTA_EXHAUSTED',
] as const;
export type RegistryLookupStatus = (typeof REGISTRY_LOOKUP_STATUSES)[number];

export const REGISTRY_ANOMALIES = ['OWNER_MISMATCH', 'PLEDGE'] as const;
export type RegistryAnomaly = (typeof REGISTRY_ANOMALIES)[number];

export const OWNERSHIP_MATCHES = ['MATCH', 'MISMATCH', 'UNKNOWN'] as const;
export type OwnershipMatch = (typeof OWNERSHIP_MATCHES)[number];

export const ATTRIBUTE_KEYS = [
  'BRAND',
  'MODEL',
  'STYLE',
  'YEAR',
  'COLOR',
  'VIN',
  'ENGINE_NUMBER',
  'DISPLACEMENT_CC',
  'FUEL',
  'DRIVETRAIN',
  'DOORS',
  'CAPACITY',
  'OWNER_ID_HASH',
  'OWNER_NAME',
] as const;
export type AttributeKey = (typeof ATTRIBUTE_KEYS)[number];

export const ATTRIBUTE_SOURCES = ['REGISTRY_DOCUMENT', 'REGISTRY_AGREEMENT', 'SELLER_DECLARED', 'ANALYST'] as const;
export type AttributeSource = (typeof ATTRIBUTE_SOURCES)[number];

export const ENCUMBRANCE_TYPES = ['PLEDGE', 'SEIZURE', 'ANNOTATION', 'COLLISION', 'OTHER'] as const;
export type EncumbranceType = (typeof ENCUMBRANCE_TYPES)[number];

export const MEDIA_KINDS = ['REGISTRY_DOCUMENT', 'LISTING_PHOTO', 'IDENTITY_DOCUMENT'] as const;
export type MediaKind = (typeof MEDIA_KINDS)[number];

export const MEDIA_STATUSES = ['PENDING_UPLOAD', 'CONFIRMED', 'REJECTED'] as const;
export type MediaStatus = (typeof MEDIA_STATUSES)[number];

export const MEDIA_VISIBILITIES = ['PRIVATE', 'PUBLIC'] as const;
export type MediaVisibility = (typeof MEDIA_VISIBILITIES)[number];

export const PHOTO_SLOT_CODES = [
  'FRONT',
  'REAR',
  'LEFT',
  'RIGHT',
  'FRONT_LEFT',
  'FRONT_RIGHT',
  'REAR_LEFT',
  'REAR_RIGHT',
  'INTERIOR_FRONT',
  'INTERIOR_REAR',
  'DASHBOARD_ODOMETER',
  'ENGINE',
  'TIRES',
  'VIN',
] as const;
export type PhotoSlotCode = (typeof PHOTO_SLOT_CODES)[number];

export const IDENTITY_LEVELS = ['LEVEL_1', 'LEVEL_2', 'LEVEL_3'] as const;
export type IdentityLevel = (typeof IDENTITY_LEVELS)[number];

export const ID_TYPES = ['NATIONAL_ID', 'RESIDENT_ID', 'PASSPORT'] as const;
export type IdType = (typeof ID_TYPES)[number];

export const CONSENT_TYPES = [
  'DATA_PROCESSING',
  'SHARE_INSURANCE_BROKER',
  'SHARE_BANK',
  'PHOTO_LOCATION',
  'MODEL_TRAINING',
] as const;
export type ConsentType = (typeof CONSENT_TYPES)[number];

export const IDENTITY_VERIFICATION_STATUSES = ['CHALLENGE_ISSUED', 'SUBMITTED', 'APPROVED', 'REJECTED', 'EXPIRED'] as const;
export type IdentityVerificationStatus = (typeof IDENTITY_VERIFICATION_STATUSES)[number];

/** Comparacion del perfil con el registro de personas (Credid). Informativa: decide el analista. */
export const REGISTRY_MATCH_STATUSES = [
  'MATCH',
  'NAME_MISMATCH',
  'NOT_FOUND',
  'DOCUMENT_EXPIRED',
  'UNAVAILABLE',
  'NOT_APPLICABLE',
] as const;
export type RegistryMatchStatus = (typeof REGISTRY_MATCH_STATUSES)[number];

/** F persona fisica, J persona juridica, E extranjero. */
export const PERSON_TYPES = ['F', 'J', 'E'] as const;
export type PersonType = (typeof PERSON_TYPES)[number];

/** Tipos de documento que el registro de personas puede consultar (el pasaporte no). */
export const REGISTRY_ID_TYPES = ['NATIONAL_ID', 'RESIDENT_ID'] as const;
export type RegistryIdType = (typeof REGISTRY_ID_TYPES)[number];

export const REGISTRY_REQUEST_STATUSES = ['PENDING', 'FULFILLED', 'CANCELLED'] as const;
export type RegistryRequestStatus = (typeof REGISTRY_REQUEST_STATUSES)[number];

// ---------- Anuncios ----------

export interface ListingSearchParams extends PageParams {
  brand?: string;
  model?: string;
  yearFrom?: number;
  yearTo?: number;
  minPrice?: number;
  maxPrice?: number;
  maxMileage?: number;
  province?: Province;
  fuel?: Fuel;
  transmission?: Transmission;
  trustLevel?: TrustLevel;
  sort?: ListingSort;
}

/** ListingSummary (D9.1 punto 4). */
export interface ListingSummary {
  id: Uuid;
  status: ListingStatus;
  title: string | null;
  price: number;
  currency: string;
  modelYear: number | null;
  mileage: number | null;
  brand: string | null;
  model: string | null;
  province: Province | null;
  trustLevel: TrustLevel;
  coverPhotoUrl: string | null;
  vehicleId: Uuid | null;
}

export interface VehicleAttributeView {
  key: AttributeKey;
  value: string;
  source: AttributeSource;
  verified: boolean;
}

/** Para el dueno incluye las tomas privadas: mediaId siempre, publicUrl solo si ya es publica. */
export interface ListingPhoto {
  slot: PhotoSlotCode;
  mediaId: Uuid | null;
  publicUrl: string | null;
}

export interface ListingVehicle {
  id: Uuid;
  countryCode: string;
  status: VehicleStatus;
  transferBlocked: boolean;
  /** Codigos de gravamen separados por coma (EncumbranceType). */
  transferBlockedReason: string | null;
  registryCheckedAt: IsoDateTime | null;
  attributes: VehicleAttributeView[];
}

/**
 * ListingDetail. El publico solo ve PUBLISHED; el dueno ve su anuncio en cualquier estado (D9.1 punto 2).
 * sellerId y los precios recomendados llegan nulos para el publico.
 */
export interface ListingDetail {
  id: Uuid;
  status: ListingStatus;
  title: string | null;
  description: string | null;
  category: string | null;
  sellerId: Uuid | null;
  vehicleId: Uuid | null;
  price: number;
  currency: string;
  recommendedPriceMin: number | null;
  recommendedPriceMax: number | null;
  modelYear: number | null;
  mileage: number | null;
  brand: string | null;
  model: string | null;
  fuel: Fuel | null;
  province: Province | null;
  transmission: Transmission | null;
  trustLevel: TrustLevel;
  negotiable: boolean;
  declaredEquipment: string[];
  maintenanceHistory: string | null;
  vehicle: ListingVehicle | null;
  photos: ListingPhoto[];
  publishedAt: IsoDateTime | null;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

/** Cuerpo de POST /listings y PUT /listings/{id}; title y description pueden ir vacios en DRAFT. */
export interface ListingUpsertRequest {
  vehicleId: Uuid;
  price: number;
  currency: Currency;
  mileage: number;
  province: Province;
  transmission: Transmission;
  declaredEquipment: string[];
  maintenanceHistory: string;
  negotiable: boolean;
  title: string;
  description: string;
}

export interface CopySuggestion {
  inferenceId: Uuid;
  title: string;
  description: string;
}

// ---------- Vehiculo ----------

export interface Encumbrance {
  type: EncumbranceType;
  description: string | null;
  blocksTransfer: boolean;
  page: number | null;
}

export interface Fee {
  amount: number;
  currency: string;
}

/** Vista del vendedor: sin datos del propietario, VIN ni numero de motor. */
export interface Vehicle {
  id: Uuid;
  countryCode: string;
  plate: string;
  status: VehicleStatus;
  reviewReason: ReviewReason | null;
  rejectionReason: string | null;
  transferBlocked: boolean;
  /** Codigos de gravamen separados por coma (EncumbranceType). */
  transferBlockedReason: string | null;
  registryCheckedAt: IsoDateTime | null;
  attributes: VehicleAttributeView[];
  encumbrances: Encumbrance[];
  /** Tarifa de "que Motoria lo obtenga"; null mientras no este configurada. */
  registryRequestFee: Fee | null;
  registryRequestOpen: boolean;
  ownerMatch: OwnershipMatch;
}

export interface CreateVehicleRequest {
  countryCode: string;
  plate: string;
}

/** Respuesta de POST /vehicles/{id}/registry-lookup (nunca 5xx por fallas del registro). */
export interface RegistryLookupResult {
  registryLookupStatus: RegistryLookupStatus;
  /** Codigo estable (VEHICLE_REGISTRY_*) cuando el registro no pudo responder. */
  errorCode: string | null;
  anomalies: RegistryAnomaly[];
  vehicle: Vehicle;
}

export interface RegistryRequestTask {
  id: Uuid;
  vehicleId: Uuid;
  countryCode: string;
  plate: string;
  status: RegistryRequestStatus;
  acceptedFee: Fee | null;
  requestedBy: Uuid | null;
  documentMediaId: Uuid | null;
  fulfilledAt: IsoDateTime | null;
  createdAt: IsoDateTime;
}

// ---------- Media ----------

export interface PhotoSlot {
  code: PhotoSlotCode;
  required: boolean;
  order: number;
  instructionKey: string;
}

export interface UploadRequest {
  kind: MediaKind;
  contentType: string;
  sizeBytes: number;
  photoSlot?: PhotoSlotCode;
}

export interface UploadTicket {
  mediaId: Uuid;
  uploadUrl: string;
  expiresAt: IsoDateTime;
}

export interface Media {
  id: Uuid;
  kind: MediaKind;
  photoSlot: PhotoSlotCode | null;
  contentType: string;
  sizeBytes: number;
  sha256: string | null;
  width: number | null;
  height: number | null;
  visibility: MediaVisibility;
  status: MediaStatus;
  publicUrl: string | null;
  confirmedAt: IsoDateTime | null;
}

export interface ReadUrl {
  url: string;
  expiresAt: IsoDateTime;
}

export interface PhotoSlotUpdateRequest {
  required: boolean;
  order: number;
  instructionKey: string;
}

// ---------- Usuario ----------

export interface Profile {
  id: Uuid;
  fullName: string | null;
  idType: IdType | null;
  idNumberLast4: string | null;
  phone: string | null;
  email: string | null;
  countryCode: string | null;
  identityLevel: IdentityLevel;
}

/** idType e idNumber son opcionales: si no vienen, se conservan los guardados (D9.1 punto 8). */
export interface ProfileUpdateRequest {
  fullName: string;
  idType?: IdType;
  idNumber?: string;
  phone?: string;
  countryCode: string;
}

/** Un elemento por tipo; currentTextVersion es la version vigente del texto (D9.1 punto 9). */
export interface Consent {
  type: ConsentType;
  currentTextVersion: string;
  textVersion: string | null;
  granted: boolean;
  decidedAt: IsoDateTime | null;
}

export interface ConsentRequest {
  type: ConsentType;
  textVersion: string;
  granted: boolean;
}

export interface IdentityChallenge {
  id: Uuid;
  challengeCode: string;
  expiresAt: IsoDateTime;
}

export interface IdentityVerificationSubmit {
  documentFrontMediaId: Uuid;
  documentBackMediaId: Uuid;
  selfieMediaId: Uuid;
}

export interface IdentityVerification {
  id: Uuid;
  status: IdentityVerificationStatus;
  challengeCode: string | null;
  expiresAt: IsoDateTime | null;
  submittedAt: IsoDateTime | null;
  reviewedAt: IsoDateTime | null;
  rejectionReason: string | null;
}

/** Vista del analista; las imagenes se abren con GET /media/{id}/read-url (auditado). */
export interface BackofficeIdentityVerification {
  id: Uuid;
  userId: Uuid;
  fullName: string | null;
  idType: IdType | null;
  idNumberLast4: string | null;
  status: IdentityVerificationStatus;
  challengeCode: string | null;
  documentFrontMediaId: Uuid | null;
  documentBackMediaId: Uuid | null;
  selfieMediaId: Uuid | null;
  submittedAt: IsoDateTime | null;
  reviewedBy: string | null;
  reviewedAt: IsoDateTime | null;
  rejectionReason: string | null;
  registryStatus: RegistryMatchStatus | null;
  registryProvider: string | null;
  registryPersonType: PersonType | null;
  registryFullName: string | null;
  /** Fecha sin hora (yyyy-MM-dd). */
  registryDocumentExpiresOn: string | null;
  registryCheckedAt: IsoDateTime | null;
}

// ---------- Backoffice: registro de personas ----------

export interface PersonRegistryLookupRequest {
  idType: RegistryIdType;
  idNumber: string;
  reason: string;
  refresh?: boolean;
}

export interface PersonRegistryLookup {
  found: boolean;
  provider: string;
  personType: PersonType | null;
  fullName: string | null;
  /** Fecha sin hora (yyyy-MM-dd). */
  documentExpiresOn: string | null;
  documentExpired: boolean;
  nationality: string | null;
  checkedAt: IsoDateTime;
  fromCache: boolean;
}

// ---------- Backoffice: revision registral ----------

export interface RegistryReviewSummary {
  vehicleId: Uuid;
  countryCode: string;
  plate: string;
  status: VehicleStatus;
  reviewReason: ReviewReason | null;
  documentMediaId: Uuid | null;
  updatedAt: IsoDateTime | null;
}

/** Pagina del documento renderizada en el servidor; imageUrl es privada y de corta duracion. */
export interface ReviewPage {
  page: number;
  mediaId: Uuid;
  imageUrl: string;
  expiresAt: IsoDateTime;
}

export interface ReviewAttribute {
  key: AttributeKey;
  value: string;
  source: AttributeSource;
  confidence: number | null;
  page: number | null;
  aiValue: string | null;
  correctedBy: string | null;
}

export interface RegistryReviewDetail {
  vehicleId: Uuid;
  countryCode: string;
  plate: string;
  status: VehicleStatus;
  reviewReason: ReviewReason | null;
  rejectionReason: string | null;
  documentMediaId: Uuid | null;
  documentDate: IsoDate | null;
  inferenceId: Uuid | null;
  pages: ReviewPage[];
  attributes: ReviewAttribute[];
  encumbrances: Encumbrance[];
  /** AGREEMENT: datos del convenio, sin documento ni paginas. */
  registrySource: RegistrySource | null;
}
