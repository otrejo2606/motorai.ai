/**
 * Deteccion del vehiculo en el dispositivo (add-captura-guiada-fotos, design D8).
 *
 * Capa abstracta: la captura guiada solo conoce OnDeviceDetector. La implementacion actual usa MediaPipe Tasks Vision
 * (ObjectDetector, categorias car y truck), cargada de forma perezosa con import() dinamico desde una URL de CDN con
 * version fija (VITE_MEDIAPIPE_BASE_URL) y el modelo de VITE_MEDIAPIPE_MODEL_URL. No hay dependencia npm: los tipos de
 * MediaPipe se declaran aqui con lo minimo que se usa. Cuando exista el modelo local de Motoria exportado para el
 * navegador (ONNX con onnxruntime-web o TF.js), bastara otra implementacion de esta interfaz.
 *
 * Si algo falla (sin red, CSP, navegador sin WebAssembly o WebGL), el cargador devuelve null y la captura sigue en modo
 * solo silueta con boton manual.
 */

/** Caja normalizada (0 a 1) respecto del cuadro de video. */
export interface NormalizedBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface VehicleDetection {
  box: NormalizedBox;
  score: number;
}

export interface OnDeviceDetector {
  /** Nombre corto de la implementacion (para diagnostico, nunca se envia al servidor). */
  readonly name: string;
  /** Devuelve el vehiculo mas probable del cuadro actual o null si no hay ninguno. */
  detect(video: HTMLVideoElement, timestampMs: number): VehicleDetection | null;
  close(): void;
}

// ---------- Tipos minimos de MediaPipe Tasks Vision (solo lo que se usa) ----------

interface MpCategory {
  categoryName?: string;
  score?: number;
}

interface MpBoundingBox {
  originX: number;
  originY: number;
  width: number;
  height: number;
}

interface MpDetection {
  boundingBox?: MpBoundingBox;
  categories?: MpCategory[];
}

interface MpDetectionResult {
  detections?: MpDetection[];
}

interface MpObjectDetector {
  detectForVideo(video: HTMLVideoElement, timestampMs: number): MpDetectionResult;
  close(): void;
}

interface MpObjectDetectorOptions {
  baseOptions: { modelAssetPath: string; delegate?: 'GPU' | 'CPU' };
  runningMode: 'VIDEO';
  scoreThreshold: number;
  maxResults: number;
  categoryAllowlist: string[];
}

interface MpVisionModule {
  FilesetResolver: { forVisionTasks(wasmBaseUrl: string): Promise<unknown> };
  ObjectDetector: { createFromOptions(fileset: unknown, options: MpObjectDetectorOptions): Promise<MpObjectDetector> };
}

const VEHICLE_CATEGORIES = ['car', 'truck'];

export interface MediaPipeSettings {
  /** URL base del paquete @mediapipe/tasks-vision con version fija; vacia desactiva la deteccion. */
  baseUrl: string;
  /** URL del modelo de deteccion de objetos (.tflite). */
  modelUrl: string;
}

function isVisionModule(value: unknown): value is MpVisionModule {
  if (!value || typeof value !== 'object') return false;
  // Son clases exportadas por el modulo: typeof devuelve function.
  const candidate = value as Partial<MpVisionModule>;
  return Boolean(candidate.FilesetResolver) && Boolean(candidate.ObjectDetector);
}

class MediaPipeDetector implements OnDeviceDetector {
  readonly name = 'mediapipe';
  private lastTimestamp = -1;

  constructor(private readonly detector: MpObjectDetector) {}

  detect(video: HTMLVideoElement, timestampMs: number): VehicleDetection | null {
    const width = video.videoWidth;
    const height = video.videoHeight;
    if (!width || !height) return null;
    // MediaPipe exige marcas de tiempo crecientes en modo VIDEO.
    const timestamp = Math.max(timestampMs, this.lastTimestamp + 1);
    this.lastTimestamp = timestamp;
    let result: MpDetectionResult;
    try {
      result = this.detector.detectForVideo(video, timestamp);
    } catch {
      return null;
    }
    let best: VehicleDetection | null = null;
    for (const detection of result.detections ?? []) {
      const box = detection.boundingBox;
      const category = (detection.categories ?? []).find((item) =>
        VEHICLE_CATEGORIES.includes((item.categoryName ?? '').toLowerCase()),
      );
      if (!box || !category) continue;
      const score = category.score ?? 0;
      if (best && best.score >= score) continue;
      best = {
        score,
        box: {
          x: box.originX / width,
          y: box.originY / height,
          width: box.width / width,
          height: box.height / height,
        },
      };
    }
    return best;
  }

  close(): void {
    try {
      this.detector.close();
    } catch {
      // Ya estaba cerrado.
    }
  }
}

let cached: Promise<OnDeviceDetector | null> | null = null;

/**
 * Carga el detector una sola vez por pagina (las 14 tomas lo comparten). Nunca lanza: devuelve null si no se puede.
 */
export function loadOnDeviceDetector(settings: MediaPipeSettings): Promise<OnDeviceDetector | null> {
  if (!cached) {
    cached = loadMediaPipe(settings).catch(() => null);
    void cached.then((detector) => {
      if (!detector) cached = null;
    });
  }
  return cached;
}

async function loadMediaPipe(settings: MediaPipeSettings): Promise<OnDeviceDetector | null> {
  const base = settings.baseUrl.trim().replace(/[/]+$/, '');
  if (!base || !settings.modelUrl.trim() || typeof WebAssembly === 'undefined') return null;
  const loaded: unknown = await import(/* @vite-ignore */ base + '/vision_bundle.mjs');
  if (!isVisionModule(loaded)) return null;
  const fileset = await loaded.FilesetResolver.forVisionTasks(base + '/wasm');
  const detector = await loaded.ObjectDetector.createFromOptions(fileset, {
    baseOptions: { modelAssetPath: settings.modelUrl.trim(), delegate: 'GPU' },
    runningMode: 'VIDEO',
    scoreThreshold: 0.4,
    maxResults: 3,
    categoryAllowlist: VEHICLE_CATEGORIES,
  });
  return new MediaPipeDetector(detector);
}
