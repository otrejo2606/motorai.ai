import type { NormalizedBox } from './onDeviceDetector';
import type { SilhouetteKind } from './slotGuides';

/**
 * Siluetas de guia dibujadas en una caja de 100 x 60 unidades y escaladas al encuadre de la toma. Son orientativas:
 * el clasificador del servidor decide si la foto corresponde a la toma.
 */
const PATHS: Record<SilhouetteKind, string[]> = {
  front: [
    'M10 50 L10 32 Q12 26 20 24 L28 10 Q30 8 34 8 L66 8 Q70 8 72 10 L80 24 Q88 26 90 32 L90 50 Z',
    'M30 12 L70 12 L76 24 L24 24 Z',
    'M16 33 L30 33 L30 38 L16 38 Z M70 33 L84 33 L84 38 L70 38 Z',
    'M38 40 L62 40 L62 46 L38 46 Z',
    'M14 50 L14 56 L26 56 L26 50 M74 50 L74 56 L86 56 L86 50',
  ],
  rear: [
    'M10 50 L10 30 Q12 25 20 24 L27 11 Q29 9 33 9 L67 9 Q71 9 73 11 L80 24 Q88 25 90 30 L90 50 Z',
    'M31 13 L69 13 L74 23 L26 23 Z',
    'M14 30 L28 30 L28 37 L14 37 Z M72 30 L86 30 L86 37 L72 37 Z',
    'M38 38 L62 38 L62 44 L38 44 Z',
    'M14 50 L14 56 L26 56 L26 50 M74 50 L74 56 L86 56 L86 50',
  ],
  side: [
    'M4 44 L4 33 Q6 29 14 28 L30 26 L41 14 Q43 12 47 12 L70 12 Q74 12 77 15 L86 26 L94 28 Q97 30 97 34 L97 44 Z',
    'M44 16 L56 16 L56 26 L35 26 Z M59 16 L72 16 L80 26 L59 26 Z',
    'M12 46 A 8 8 0 1 0 28 46 A 8 8 0 1 0 12 46 Z',
    'M72 46 A 8 8 0 1 0 88 46 A 8 8 0 1 0 72 46 Z',
  ],
  threeQuarterFront: [
    'M4 46 L4 34 L20 28 L32 14 Q34 12 38 12 L68 11 Q72 11 75 14 L84 24 L96 28 L96 46 Z',
    'M36 16 L66 15 L72 24 L28 27 Z',
    'M74 30 L96 30 M74 30 L74 46 M78 34 L92 34',
    'M10 48 A 7 7 0 1 0 24 48 A 7 7 0 1 0 10 48 Z',
    'M58 48 A 7 7 0 1 0 72 48 A 7 7 0 1 0 58 48 Z',
  ],
  threeQuarterRear: [
    'M4 46 L4 30 L14 26 L24 14 Q26 12 30 12 L62 12 Q66 12 68 14 L80 28 L96 32 L96 46 Z',
    'M4 30 L26 30 L26 46 M8 34 L22 34',
    'M30 16 L60 16 L72 28 L28 28 Z',
    'M30 48 A 7 7 0 1 0 44 48 A 7 7 0 1 0 30 48 Z',
    'M78 48 A 7 7 0 1 0 92 48 A 7 7 0 1 0 78 48 Z',
  ],
  interior: [
    'M8 56 L8 20 Q8 10 18 10 L82 10 Q92 10 92 20 L92 56',
    'M28 30 A 12 12 0 1 0 52 30 A 12 12 0 1 0 28 30 Z',
    'M58 40 L80 40 L80 56 L58 56 Z M20 44 L46 44 L46 56 L20 56 Z',
  ],
  dashboard: [
    'M6 50 L6 18 Q6 10 14 10 L86 10 Q94 10 94 18 L94 50 Z',
    'M20 30 A 12 12 0 1 0 44 30 A 12 12 0 1 0 20 30 Z',
    'M56 30 A 12 12 0 1 0 80 30 A 12 12 0 1 0 56 30 Z',
    'M38 42 L62 42 L62 48 L38 48 Z',
  ],
  engine: [
    'M10 56 L18 8 L82 8 L90 56 Z',
    'M30 24 L70 24 L70 48 L30 48 Z',
    'M36 28 L64 28 M36 34 L64 34 M36 40 L64 40',
  ],
  tire: [
    'M22 30 A 28 28 0 1 0 78 30 A 28 28 0 1 0 22 30 Z',
    'M36 30 A 14 14 0 1 0 64 30 A 14 14 0 1 0 36 30 Z',
  ],
  vin: [
    'M12 20 L88 20 L88 40 L12 40 Z',
    'M18 30 L24 30 M28 30 L34 30 M38 30 L44 30 M48 30 L54 30 M58 30 L64 30 M68 30 L74 30 M78 30 L82 30',
  ],
};

export interface SilhouetteProps {
  kind: SilhouetteKind;
  mirror: boolean;
  frame: NormalizedBox;
  /** Dimensiones del cuadro de video; la silueta usa el mismo recorte que object-fit: cover. */
  videoWidth: number;
  videoHeight: number;
  /** Resalta la silueta cuando el vehiculo calza. */
  matched: boolean;
}

export function Silhouette({ kind, mirror, frame, videoWidth, videoHeight, matched }: SilhouetteProps) {
  const width = videoWidth > 0 ? videoWidth : 1280;
  const height = videoHeight > 0 ? videoHeight : 720;
  const x = frame.x * width;
  const y = frame.y * height;
  const scaleX = (frame.width * width) / 100;
  const scaleY = (frame.height * height) / 60;
  const inner = mirror ? 'translate(100 0) scale(-1 1)' : undefined;
  return (
    <svg
      className={'m-capture__silhouette' + (matched ? ' m-capture__silhouette--matched' : '')}
      viewBox={'0 0 ' + width + ' ' + height}
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
      focusable="false"
    >
      <g transform={'translate(' + x + ' ' + y + ') scale(' + scaleX + ' ' + scaleY + ')'}>
        <g transform={inner}>
          {PATHS[kind].map((path) => (
            <path key={path} d={path} fill="none" vectorEffect="non-scaling-stroke" />
          ))}
        </g>
      </g>
    </svg>
  );
}
