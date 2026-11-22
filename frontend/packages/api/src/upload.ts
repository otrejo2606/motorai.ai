import { ApiClient, ApiError, seg } from './client';
import type { Media, MediaKind, PhotoSlotCode, UploadTicket } from './types';

/** Limites de carga en el cliente (el servidor vuelve a validar todo, design D5). */
export interface UploadLimits {
  maxDocumentBytes: number;
  maxPhotoBytes: number;
}

export const DEFAULT_UPLOAD_LIMITS: UploadLimits = {
  maxDocumentBytes: 10 * 1024 * 1024,
  maxPhotoBytes: 15 * 1024 * 1024,
};

/**
 * Tipos por clase de archivo (design D5 y D10): las fotos de anuncio solo JPEG y PNG, porque la copia
 * publica se recodifica con el JDK; HEIC solo para documentos de identidad, que nunca son publicos.
 */
const ALLOWED_TYPES: Record<MediaKind, readonly string[]> = {
  REGISTRY_DOCUMENT: ['application/pdf'],
  LISTING_PHOTO: ['image/jpeg', 'image/png'],
  IDENTITY_DOCUMENT: ['image/jpeg', 'image/png', 'image/heic'],
};

/** Valor para el atributo accept del input de archivo. */
export function acceptFor(kind: MediaKind): string {
  if (kind === 'REGISTRY_DOCUMENT') return 'application/pdf,.pdf';
  if (kind === 'LISTING_PHOTO') return 'image/jpeg,image/png,.jpg,.jpeg,.png';
  return 'image/jpeg,image/png,image/heic,.jpg,.jpeg,.png,.heic';
}

export function maxBytesFor(kind: MediaKind, limits: UploadLimits): number {
  return kind === 'REGISTRY_DOCUMENT' ? limits.maxDocumentBytes : limits.maxPhotoBytes;
}

function startsWith(bytes: Uint8Array, signature: number[], offset = 0): boolean {
  if (bytes.length < offset + signature.length) return false;
  return signature.every((value, index) => bytes[offset + index] === value);
}

function ascii(bytes: Uint8Array, from: number, to: number): string {
  let text = '';
  for (let i = from; i < to && i < bytes.length; i += 1) {
    text += String.fromCharCode(bytes[i] ?? 0);
  }
  return text;
}

/**
 * Detecta el tipo real por la firma de contenido y no por la extension.
 * Devuelve null si no es un tipo permitido en ningun caso.
 */
export async function detectContentType(file: Blob): Promise<string | null> {
  const bytes = new Uint8Array(await file.slice(0, 16).arrayBuffer());
  if (startsWith(bytes, [0x25, 0x50, 0x44, 0x46, 0x2d])) return 'application/pdf';
  if (startsWith(bytes, [0xff, 0xd8, 0xff])) return 'image/jpeg';
  if (startsWith(bytes, [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) return 'image/png';
  if (ascii(bytes, 4, 8) === 'ftyp') {
    const brand = ascii(bytes, 8, 12);
    if (['heic', 'heix', 'hevc', 'hevx', 'mif1', 'msf1'].includes(brand)) return 'image/heic';
  }
  return null;
}

/** Valida tipo y tamano en el cliente. Lanza ApiError con codigo traducible. */
export async function validateFile(file: File, kind: MediaKind, limits: UploadLimits): Promise<string> {
  if (file.size <= 0) {
    throw new ApiError(0, 'UPLOAD_EMPTY');
  }
  if (file.size > maxBytesFor(kind, limits)) {
    throw new ApiError(0, 'UPLOAD_TOO_LARGE');
  }
  const detected = await detectContentType(file);
  if (!detected || !ALLOWED_TYPES[kind].includes(detected)) {
    throw new ApiError(0, 'UPLOAD_TYPE_NOT_ALLOWED');
  }
  return detected;
}

export interface UploadFileInput {
  file: File;
  kind: MediaKind;
  photoSlot?: PhotoSlotCode;
}

/**
 * Carga en dos pasos (design D5): pide la URL prefirmada, sube el archivo con PUT
 * y confirma para que el servidor verifique firma, tamano y sha256.
 * La URL de subida no se guarda en ningun almacenamiento.
 */
export async function uploadFile(client: ApiClient, input: UploadFileInput, limits: UploadLimits): Promise<Media> {
  const contentType = await validateFile(input.file, input.kind, limits);
  const ticket = await client.post<UploadTicket>('/media/uploads', {
    kind: input.kind,
    contentType,
    sizeBytes: input.file.size,
    photoSlot: input.photoSlot,
  });

  let target: URL;
  try {
    target = new URL(ticket.uploadUrl);
  } catch {
    throw new ApiError(0, 'UPLOAD_FAILED');
  }
  if (target.protocol !== 'https:' && target.protocol !== 'http:') {
    throw new ApiError(0, 'UPLOAD_FAILED');
  }

  let putResponse: Response;
  try {
    putResponse = await fetch(target.toString(), {
      method: 'PUT',
      body: input.file,
      headers: { 'Content-Type': contentType },
      credentials: 'omit',
    });
  } catch {
    throw new ApiError(0, 'UPLOAD_FAILED');
  }
  if (!putResponse.ok) {
    throw new ApiError(putResponse.status, 'UPLOAD_FAILED');
  }

  return client.post<Media>('/media/' + seg(ticket.mediaId) + '/confirm');
}
