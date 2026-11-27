import type { PhotoSlotCode } from '@motoria/api';
import type { NormalizedBox, VehicleDetection } from './onDeviceDetector';

/**
 * Guia de cada toma del protocolo (add-captura-guiada-fotos, design D8): silueta, encuadre esperado del vehiculo,
 * si se detecta el vehiculo en el dispositivo y el punto de toma alrededor del auto para la realidad aumentada.
 */
export type SilhouetteKind =
  | 'front'
  | 'rear'
  | 'side'
  | 'threeQuarterFront'
  | 'threeQuarterRear'
  | 'interior'
  | 'dashboard'
  | 'engine'
  | 'tire'
  | 'vin';

export interface SlotGuide {
  silhouette: SilhouetteKind;
  /** Dibuja la silueta en espejo (por ejemplo, el lateral izquierdo). */
  mirror: boolean;
  /** Solo las tomas exteriores detectan el vehiculo; interior, tablero, motor, llantas y VIN usan solo la silueta. */
  detectVehicle: boolean;
  /** Encuadre esperado del vehiculo, normalizado respecto del cuadro de video. */
  frame: NormalizedBox;
  /** Fraccion minima del encuadre que debe ocupar la caja detectada. */
  minFill: number;
  /**
   * Punto de toma para la realidad aumentada: angulo en grados medido desde el frente del vehiculo hacia su lado
   * derecho, y distancia en metros desde el centro. Null en las tomas sin punto exterior.
   */
  ar: { angle: number; distance: number } | null;
}

const WIDE: NormalizedBox = { x: 0.06, y: 0.22, width: 0.88, height: 0.56 };
const FACE: NormalizedBox = { x: 0.14, y: 0.18, width: 0.72, height: 0.64 };
const CORNER: NormalizedBox = { x: 0.08, y: 0.2, width: 0.84, height: 0.6 };
const DETAIL: NormalizedBox = { x: 0.12, y: 0.15, width: 0.76, height: 0.7 };

export const SLOT_GUIDES: Record<PhotoSlotCode, SlotGuide> = {
  FRONT: { silhouette: 'front', mirror: false, detectVehicle: true, frame: FACE, minFill: 0.45, ar: { angle: 0, distance: 3.5 } },
  REAR: { silhouette: 'rear', mirror: false, detectVehicle: true, frame: FACE, minFill: 0.45, ar: { angle: 180, distance: 3.5 } },
  // Desde el lado izquierdo del vehiculo, su frente queda a la izquierda de quien toma la foto.
  LEFT: { silhouette: 'side', mirror: true, detectVehicle: true, frame: WIDE, minFill: 0.5, ar: { angle: 270, distance: 3.2 } },
  RIGHT: { silhouette: 'side', mirror: false, detectVehicle: true, frame: WIDE, minFill: 0.5, ar: { angle: 90, distance: 3.2 } },
  FRONT_LEFT: {
    silhouette: 'threeQuarterFront',
    mirror: true,
    detectVehicle: true,
    frame: CORNER,
    minFill: 0.45,
    ar: { angle: 315, distance: 3.8 },
  },
  FRONT_RIGHT: {
    silhouette: 'threeQuarterFront',
    mirror: false,
    detectVehicle: true,
    frame: CORNER,
    minFill: 0.45,
    ar: { angle: 45, distance: 3.8 },
  },
  REAR_LEFT: {
    silhouette: 'threeQuarterRear',
    mirror: false,
    detectVehicle: true,
    frame: CORNER,
    minFill: 0.45,
    ar: { angle: 225, distance: 3.8 },
  },
  REAR_RIGHT: {
    silhouette: 'threeQuarterRear',
    mirror: true,
    detectVehicle: true,
    frame: CORNER,
    minFill: 0.45,
    ar: { angle: 135, distance: 3.8 },
  },
  INTERIOR_FRONT: { silhouette: 'interior', mirror: false, detectVehicle: false, frame: DETAIL, minFill: 0, ar: null },
  INTERIOR_REAR: { silhouette: 'interior', mirror: false, detectVehicle: false, frame: DETAIL, minFill: 0, ar: null },
  DASHBOARD_ODOMETER: { silhouette: 'dashboard', mirror: false, detectVehicle: false, frame: DETAIL, minFill: 0, ar: null },
  ENGINE: { silhouette: 'engine', mirror: false, detectVehicle: false, frame: DETAIL, minFill: 0, ar: null },
  TIRES: { silhouette: 'tire', mirror: false, detectVehicle: false, frame: DETAIL, minFill: 0, ar: null },
  VIN: { silhouette: 'vin', mirror: false, detectVehicle: false, frame: DETAIL, minFill: 0, ar: null },
};

/** Holgura del encuadre: la caja puede salirse este tanto (normalizado) de la silueta. */
export const FRAME_TOLERANCE = 0.12;

export type FrameFit = 'none' | 'outside' | 'tooSmall' | 'fits';

/** Compara la caja del vehiculo detectado con el encuadre esperado de la toma. */
export function frameFit(guide: SlotGuide, detection: VehicleDetection | null): FrameFit {
  if (!detection) return 'none';
  const { box } = detection;
  const frame = guide.frame;
  const inside =
    box.x >= frame.x - FRAME_TOLERANCE &&
    box.y >= frame.y - FRAME_TOLERANCE &&
    box.x + box.width <= frame.x + frame.width + FRAME_TOLERANCE &&
    box.y + box.height <= frame.y + frame.height + FRAME_TOLERANCE;
  if (!inside) return 'outside';
  const fill = (box.width * box.height) / (frame.width * frame.height);
  return fill >= guide.minFill ? 'fits' : 'tooSmall';
}
