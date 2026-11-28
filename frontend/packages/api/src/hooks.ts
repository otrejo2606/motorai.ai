import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, seg } from './client';
import { useApiClient, useUploadLimits } from './context';
import { uploadFile, type UploadFileInput } from './upload';
import type {
  BackofficeIdentityVerification,
  Consent,
  ConsentRequest,
  CopySuggestion,
  CreateVehicleRequest,
  IdentityChallenge,
  IdentityVerification,
  IdentityVerificationSubmit,
  ListingDetail,
  ListingSearchParams,
  ListingStatus,
  ListingSummary,
  ListingUpsertRequest,
  Page,
  PersonRegistryLookup,
  PersonRegistryLookupRequest,
  PhotoSlot,
  PhotoSlotCode,
  PhotoSlotUpdateRequest,
  Profile,
  ProfileUpdateRequest,
  ReadUrl,
  RegistryLookupResult,
  RegistryRequestTask,
  RegistryReviewDetail,
  RegistryReviewSummary,
  ReviewAttribute,
  Uuid,
  Vehicle,
} from './types';

export const DEFAULT_PAGE_SIZE = 20;

export const queryKeys = {
  listings: (params: ListingSearchParams) => ['listings', params] as const,
  listing: (id: Uuid) => ['listing', id] as const,
  myListings: (page: number) => ['me', 'listings', page] as const,
  photoProtocol: (country: string) => ['photo-protocol', country] as const,
  profile: () => ['me', 'profile'] as const,
  consents: () => ['me', 'consents'] as const,
  identityLatest: () => ['me', 'identity-verification', 'latest'] as const,
  vehicle: (id: Uuid) => ['vehicle', id] as const,
  readUrl: (mediaId: Uuid) => ['media', mediaId, 'read-url'] as const,
  registryReviews: (status: string, page: number) => ['backoffice', 'registry-reviews', status, page] as const,
  registryReview: (vehicleId: Uuid) => ['backoffice', 'registry-review', vehicleId] as const,
  registryRequests: (status: string, page: number) => ['backoffice', 'registry-requests', status, page] as const,
  identityVerifications: (status: string, page: number) =>
    ['backoffice', 'identity-verifications', status, page] as const,
  backofficeListings: (status: string, page: number) => ['backoffice', 'listings', status, page] as const,
};

// ---------- Publico ----------

export function useListings(params: ListingSearchParams) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.listings(params),
    queryFn: () => client.get<Page<ListingSummary>>('/listings', { ...params }),
    placeholderData: (previous) => previous,
  });
}

/** Publico: solo PUBLISHED. El dueno obtiene su anuncio en cualquier estado, con sus fotos (D9.1 punto 2). */
export function useListing(id: Uuid | null | undefined) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.listing(id ?? ''),
    queryFn: () => client.get<ListingDetail>('/listings/' + seg(id ?? '')),
    enabled: Boolean(id),
  });
}

export function usePhotoProtocol(country: string, enabled = true) {
  const client = useApiClient();
  return useQuery({
    enabled: enabled && Boolean(country),
    queryKey: queryKeys.photoProtocol(country),
    queryFn: async () => {
      const slots = await client.get<PhotoSlot[]>('/media/photo-protocol', { country });
      return [...slots].sort((a, b) => a.order - b.order);
    },
  });
}

// ---------- Usuario ----------

export function useProfile(enabled = true) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.profile(),
    queryFn: () => client.get<Profile>('/me/profile'),
    enabled,
  });
}

export function useUpdateProfile() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ProfileUpdateRequest) => client.put<Profile>('/me/profile', body),
    onSuccess: (profile) => {
      queryClient.setQueryData(queryKeys.profile(), profile);
    },
  });
}

/** Un elemento por tipo de consentimiento, con la version vigente del texto. */
export function useConsents() {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.consents(),
    queryFn: () => client.get<Page<Consent>>('/me/consents', { page: 0, size: DEFAULT_PAGE_SIZE }),
  });
}

export function useSaveConsent() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ConsentRequest) => client.post<Consent>('/me/consents', body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.consents() }),
  });
}

export function useLatestIdentityVerification() {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.identityLatest(),
    queryFn: async () => {
      try {
        return (await client.get<IdentityVerification | undefined>('/me/identity-verifications/latest')) ?? null;
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
  });
}

export function useStartIdentityVerification() {
  const client = useApiClient();
  return useMutation({
    mutationFn: () => client.post<IdentityChallenge>('/me/identity-verifications'),
  });
}

export function useSubmitIdentityVerification() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: Uuid; body: IdentityVerificationSubmit }) =>
      client.put<IdentityVerification>('/me/identity-verifications/' + seg(id), body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.identityLatest() });
      void queryClient.invalidateQueries({ queryKey: queryKeys.profile() });
    },
  });
}

// ---------- Media ----------

export function useUploadFile() {
  const client = useApiClient();
  const limits = useUploadLimits();
  return useMutation({
    mutationFn: (input: UploadFileInput) => uploadFile(client, input, limits),
  });
}

/**
 * URL de lectura privada de corta duracion. No se guarda: gcTime 0 la saca de la cache
 * al desmontar y nunca se escribe en almacenamiento del navegador.
 */
export function useMediaReadUrl(mediaId: Uuid | null | undefined) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.readUrl(mediaId ?? ''),
    queryFn: () => client.get<ReadUrl>('/media/' + seg(mediaId ?? '') + '/read-url'),
    enabled: Boolean(mediaId),
    gcTime: 0,
    staleTime: 0,
    refetchOnWindowFocus: false,
  });
}

// ---------- Vendedor ----------

export function useCreateVehicle() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateVehicleRequest) => client.post<Vehicle>('/vehicles', body),
    onSuccess: (vehicle) => queryClient.setQueryData(queryKeys.vehicle(vehicle.id), vehicle),
  });
}

const VEHICLE_POLL_MS = 10000;

export function useVehicle(id: Uuid | undefined) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.vehicle(id ?? ''),
    queryFn: () => client.get<Vehicle>('/vehicles/' + seg(id ?? '')),
    enabled: Boolean(id),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'REGISTRY_PENDING' || status === 'REGISTRY_IN_REVIEW' ? VEHICLE_POLL_MS : false;
    },
  });
}

export function useAttachRegistryDocument() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ vehicleId, mediaId }: { vehicleId: Uuid; mediaId: Uuid }) =>
      client.post<Vehicle>('/vehicles/' + seg(vehicleId) + '/registry-documents', { mediaId }),
    onSuccess: (vehicle, variables) => queryClient.setQueryData(queryKeys.vehicle(variables.vehicleId), vehicle),
  });
}

/**
 * Consulta la placa en el Registro Nacional por convenio (Credid). Siempre responde 200 salvo errores propios del
 * vehiculo; si registryLookupStatus no es FOUND ni REUSED, el vendedor sube el informe PDF.
 */
export function useRegistryLookup() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vehicleId: Uuid) =>
      client.post<RegistryLookupResult>('/vehicles/' + seg(vehicleId) + '/registry-lookup'),
    onSuccess: (result, vehicleId) => queryClient.setQueryData(queryKeys.vehicle(vehicleId), result.vehicle),
  });
}

/** acceptedFee debe ser el monto de registryRequestFee que se mostro al vendedor. */
export function useRequestRegistryDocument() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ vehicleId, acceptedFee }: { vehicleId: Uuid; acceptedFee: number }) =>
      client.post<RegistryRequestTask>('/vehicles/' + seg(vehicleId) + '/registry-requests', { acceptedFee }),
    onSuccess: (_data, variables) => queryClient.invalidateQueries({ queryKey: queryKeys.vehicle(variables.vehicleId) }),
  });
}

export function useMyListings(page = 0) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.myListings(page),
    queryFn: () => client.get<Page<ListingSummary>>('/me/listings', { page, size: DEFAULT_PAGE_SIZE }),
    placeholderData: (previous) => previous,
  });
}

function useStoreListing() {
  const queryClient = useQueryClient();
  return (listing: ListingDetail | undefined) => {
    if (listing && listing.id) {
      queryClient.setQueryData(queryKeys.listing(listing.id), listing);
    }
    void queryClient.invalidateQueries({ queryKey: ['me', 'listings'] });
  };
}

export function useSaveListing() {
  const client = useApiClient();
  const store = useStoreListing();
  return useMutation({
    mutationFn: ({ id, body }: { id?: Uuid; body: ListingUpsertRequest }) =>
      id ? client.put<ListingDetail>('/listings/' + seg(id), body) : client.post<ListingDetail>('/listings', body),
    onSuccess: store,
  });
}

/** Sugerencia en el idioma activo (es o en); el vendedor decide si la copia y la guarda. */
export function useCopySuggestion() {
  const client = useApiClient();
  return useMutation({
    mutationFn: ({ listingId, language }: { listingId: Uuid; language: string }) =>
      client.request<CopySuggestion>('POST', '/listings/' + seg(listingId) + '/copy-suggestions', {
        query: { language: language === 'en' ? 'en' : 'es' },
      }),
  });
}

export function useSetListingPhoto() {
  const client = useApiClient();
  const store = useStoreListing();
  return useMutation({
    mutationFn: ({ listingId, slot, mediaId }: { listingId: Uuid; slot: PhotoSlotCode; mediaId: Uuid }) =>
      client.put<ListingDetail>('/listings/' + seg(listingId) + '/photos/' + seg(slot), { mediaId }),
    onSuccess: store,
  });
}

export function useDeleteListingPhoto() {
  const client = useApiClient();
  const store = useStoreListing();
  return useMutation({
    mutationFn: ({ listingId, slot }: { listingId: Uuid; slot: PhotoSlotCode }) =>
      client.delete<ListingDetail>('/listings/' + seg(listingId) + '/photos/' + seg(slot)),
    onSuccess: store,
  });
}

function useListingTransition(action: 'submit-review' | 'mark-sold' | 'publish') {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (listingId: Uuid) => client.patch<ListingDetail>('/listings/' + seg(listingId) + '/' + action),
    onSuccess: (listing) => {
      if (listing && listing.id) {
        queryClient.setQueryData(queryKeys.listing(listing.id), listing);
      }
      void queryClient.invalidateQueries({ queryKey: ['me', 'listings'] });
      void queryClient.invalidateQueries({ queryKey: ['backoffice', 'listings'] });
    },
  });
}

export function useSubmitListingForReview() {
  return useListingTransition('submit-review');
}

export function useMarkListingSold() {
  return useListingTransition('mark-sold');
}

// ---------- Backoffice ----------

export function useRegistryReviews(status: string, page = 0) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.registryReviews(status, page),
    queryFn: () =>
      client.get<Page<RegistryReviewSummary>>('/backoffice/registry-reviews', { status, page, size: DEFAULT_PAGE_SIZE }),
    placeholderData: (previous) => previous,
  });
}

/** Incluye la URL de imagen de cada pagina; de corta duracion, por eso gcTime 0. */
export function useRegistryReview(vehicleId: Uuid | undefined) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.registryReview(vehicleId ?? ''),
    queryFn: () => client.get<RegistryReviewDetail>('/backoffice/registry-reviews/' + seg(vehicleId ?? '')),
    enabled: Boolean(vehicleId),
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
}

/** Devuelve solo el atributo corregido; se vuelve a leer el detalle para refrescar estado y motivos. */
export function useCorrectRegistryAttribute() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ vehicleId, key, value }: { vehicleId: Uuid; key: string; value: string }) =>
      client.put<ReviewAttribute>('/backoffice/registry-reviews/' + seg(vehicleId) + '/attributes/' + seg(key), {
        value,
      }),
    onSuccess: (_data, variables) =>
      queryClient.invalidateQueries({ queryKey: queryKeys.registryReview(variables.vehicleId) }),
  });
}

export function useApproveRegistryReview() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vehicleId: Uuid) =>
      client.post<RegistryReviewSummary>('/backoffice/registry-reviews/' + seg(vehicleId) + '/approve'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backoffice', 'registry-reviews'] }),
  });
}

export function useRejectRegistryReview() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ vehicleId, reason }: { vehicleId: Uuid; reason: string }) =>
      client.post<RegistryReviewSummary>('/backoffice/registry-reviews/' + seg(vehicleId) + '/reject', { reason }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backoffice', 'registry-reviews'] }),
  });
}

export function useRegistryRequests(status: string, page = 0) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.registryRequests(status, page),
    queryFn: () =>
      client.get<Page<RegistryRequestTask>>('/backoffice/registry-requests', { status, page, size: DEFAULT_PAGE_SIZE }),
    placeholderData: (previous) => previous,
  });
}

export function useAttachRequestedDocument() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, mediaId }: { requestId: Uuid; mediaId: Uuid }) =>
      client.post<RegistryRequestTask>('/backoffice/registry-requests/' + seg(requestId) + '/document', { mediaId }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backoffice', 'registry-requests'] }),
  });
}

export function useIdentityVerifications(status: string, page = 0) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.identityVerifications(status, page),
    queryFn: () =>
      client.get<Page<BackofficeIdentityVerification>>('/backoffice/identity-verifications', {
        status,
        page,
        size: DEFAULT_PAGE_SIZE,
      }),
    placeholderData: (previous) => previous,
  });
}

export function useApproveIdentityVerification() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: Uuid) =>
      client.post<BackofficeIdentityVerification>('/backoffice/identity-verifications/' + seg(id) + '/approve'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backoffice', 'identity-verifications'] }),
  });
}

export function useRejectIdentityVerification() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: Uuid; reason: string }) =>
      client.post<BackofficeIdentityVerification>('/backoffice/identity-verifications/' + seg(id) + '/reject', {
        reason,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backoffice', 'identity-verifications'] }),
  });
}

/** Vuelve a cotejar con el registro de personas un caso en revision (reusa la consulta vigente, no paga dos veces). */
export function useRecheckIdentityRegistry() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: Uuid) =>
      client.post<BackofficeIdentityVerification>('/backoffice/identity-verifications/' + seg(id) + '/registry-check'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['backoffice', 'identity-verifications'] }),
  });
}

/** Consulta de una persona por documento; la cedula viaja en el cuerpo, nunca en la URL. */
export function usePersonRegistryLookup() {
  const client = useApiClient();
  return useMutation({
    mutationFn: (request: PersonRegistryLookupRequest) =>
      client.post<PersonRegistryLookup>('/backoffice/person-registry/lookups', request),
  });
}

export function useBackofficeListings(status: ListingStatus, page = 0) {
  const client = useApiClient();
  return useQuery({
    queryKey: queryKeys.backofficeListings(status, page),
    queryFn: () =>
      client.get<Page<ListingSummary>>('/backoffice/listings', { status, page, size: DEFAULT_PAGE_SIZE }),
    placeholderData: (previous) => previous,
  });
}

export function usePublishListing() {
  return useListingTransition('publish');
}

export function useUpdatePhotoSlot() {
  const client = useApiClient();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ country, code, body }: { country: string; code: PhotoSlotCode; body: PhotoSlotUpdateRequest }) =>
      client.put<PhotoSlot>('/backoffice/photo-protocol/' + seg(country) + '/' + seg(code), body),
    onSuccess: (_data, variables) =>
      queryClient.invalidateQueries({ queryKey: queryKeys.photoProtocol(variables.country) }),
  });
}
