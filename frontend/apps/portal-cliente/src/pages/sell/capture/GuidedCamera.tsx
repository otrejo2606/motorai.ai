import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { PhotoSlotCode } from '@motoria/api';
import { Alert, Button } from '@motoria/ui';
import { captureConfig } from '../../../config';
import { loadOnDeviceDetector, type OnDeviceDetector } from './onDeviceDetector';
import { SLOT_GUIDES, frameFit, type FrameFit } from './slotGuides';
import { Silhouette } from './Silhouette';

type CameraState = 'starting' | 'ready' | 'denied' | 'noCamera' | 'unsupported';

/** Cada cuanto se corre la deteccion (no hace falta en cada cuadro). */
const DETECT_INTERVAL_MS = 200;
/** Tiempo que el vehiculo debe quedarse dentro de la silueta antes de la captura automatica. */
const STABLE_MS = 1200;
const JPEG_QUALITY = 0.92;

export interface GuidedCameraProps {
  slot: PhotoSlotCode;
  /** Foto tomada (JPEG del cuadro completo de la camara); se sube como cualquier archivo elegido. */
  onCapture: (file: File) => void;
  onCancel: () => void;
}

function cameraError(error: unknown): CameraState {
  const name = error instanceof DOMException ? error.name : '';
  if (name === 'NotAllowedError' || name === 'SecurityError') return 'denied';
  if (name === 'NotFoundError' || name === 'OverconstrainedError' || name === 'NotReadableError') return 'noCamera';
  return 'unsupported';
}

/**
 * Camara trasera con la silueta de la toma superpuesta (add-captura-guiada-fotos, design D8). En las tomas exteriores
 * detecta el vehiculo en el dispositivo y, si queda dentro de la silueta de forma estable, toma la foto sola; siempre
 * hay boton manual. Sin camara, sin permiso o sin detector, queda la silueta con captura manual, y la tarjeta de la
 * toma sigue ofreciendo subir un archivo. Ninguna imagen sale del dispositivo hasta que el vendedor la sube.
 */
export function GuidedCamera({ slot, onCapture, onCancel }: GuidedCameraProps) {
  const { t } = useTranslation();
  const guide = SLOT_GUIDES[slot];
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const capturedRef = useRef(false);
  const fitSinceRef = useRef(0);
  // El padre puede pasar una funcion nueva en cada render: se guarda en una ref para no reiniciar la deteccion.
  const onCaptureRef = useRef(onCapture);
  onCaptureRef.current = onCapture;
  const [camera, setCamera] = useState<CameraState>('starting');
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [detector, setDetector] = useState<OnDeviceDetector | null>(null);
  const [detectorFailed, setDetectorFailed] = useState(false);
  const [fit, setFit] = useState<FrameFit>('none');
  const [autoCapture, setAutoCapture] = useState(true);
  const [capturing, setCapturing] = useState(false);
  const [captureFailed, setCaptureFailed] = useState(false);
  const label = t('photoProtocol.slots.' + slot + '.label');

  useEffect(() => {
    let cancelled = false;
    const devices = typeof navigator !== 'undefined' ? navigator.mediaDevices : undefined;
    if (!devices || typeof devices.getUserMedia !== 'function' || !window.isSecureContext) {
      setCamera('unsupported');
      return undefined;
    }
    devices
      .getUserMedia({
        audio: false,
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
      })
      .then((stream) => {
        if (cancelled) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        streamRef.current = stream;
        const video = videoRef.current;
        if (video) {
          video.srcObject = stream;
          void video.play().catch(() => undefined);
        }
        setCamera('ready');
      })
      .catch((error: unknown) => {
        if (!cancelled) setCamera(cameraError(error));
      });
    return () => {
      cancelled = true;
      streamRef.current?.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!guide.detectVehicle || camera !== 'ready') return undefined;
    let active = true;
    void loadOnDeviceDetector(captureConfig.mediaPipe).then((loaded) => {
      if (!active) return;
      if (loaded) setDetector(loaded);
      else setDetectorFailed(true);
    });
    return () => {
      active = false;
    };
  }, [guide.detectVehicle, camera]);

  const capture = useCallback(() => {
    const video = videoRef.current;
    if (!video || !video.videoWidth || !video.videoHeight || capturedRef.current) return;
    capturedRef.current = true;
    setCapturing(true);
    setCaptureFailed(false);
    const fail = () => {
      capturedRef.current = false;
      setCapturing(false);
      setCaptureFailed(true);
    };
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const context = canvas.getContext('2d');
    if (!context) {
      fail();
      return;
    }
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          fail();
          return;
        }
        onCaptureRef.current(new File([blob], slot.toLowerCase() + '.jpg', { type: 'image/jpeg' }));
      },
      'image/jpeg',
      JPEG_QUALITY,
    );
  }, [slot]);

  useEffect(() => {
    if (!detector || camera !== 'ready' || !guide.detectVehicle) return undefined;
    let frame = 0;
    let last = 0;
    const loop = (now: number) => {
      frame = window.requestAnimationFrame(loop);
      if (now - last < DETECT_INTERVAL_MS) return;
      last = now;
      const video = videoRef.current;
      if (!video || video.readyState < 2) return;
      const current = frameFit(guide, detector.detect(video, now));
      setFit(current);
      if (current !== 'fits') {
        fitSinceRef.current = 0;
        return;
      }
      if (fitSinceRef.current === 0) fitSinceRef.current = now;
      if (autoCapture && now - fitSinceRef.current >= STABLE_MS) capture();
    };
    frame = window.requestAnimationFrame(loop);
    return () => window.cancelAnimationFrame(frame);
  }, [detector, camera, guide, autoCapture, capture]);

  const cameraProblem = camera === 'denied' || camera === 'noCamera' || camera === 'unsupported';
  const detecting = guide.detectVehicle && detector !== null;

  let status: string;
  if (capturing) status = t('capture.status.capturing');
  else if (camera === 'starting') status = t('capture.status.loading');
  else if (!guide.detectVehicle || detectorFailed) status = t('capture.status.guideOnly');
  else if (!detector) status = t('capture.status.loadingDetector');
  else if (fit === 'fits') status = autoCapture ? t('capture.status.hold') : t('capture.status.ready');
  else if (fit === 'tooSmall') status = t('capture.status.tooSmall');
  else if (fit === 'outside') status = t('capture.status.outside');
  else status = t('capture.status.searching');

  return (
    <div className="m-capture" role="dialog" aria-modal="true" aria-label={t('capture.title', { slot: label })}>
      <div className="m-capture__stage">
        <video
          ref={videoRef}
          className="m-capture__video"
          playsInline
          muted
          autoPlay
          onLoadedMetadata={(event) =>
            setSize({ width: event.currentTarget.videoWidth, height: event.currentTarget.videoHeight })
          }
        />
        {camera === 'ready' ? (
          <Silhouette
            kind={guide.silhouette}
            mirror={guide.mirror}
            frame={guide.frame}
            videoWidth={size.width}
            videoHeight={size.height}
            matched={detecting && fit === 'fits'}
          />
        ) : null}
        {cameraProblem ? null : (
          <p className="m-capture__status" aria-live="polite">
            {status}
          </p>
        )}
      </div>
      <div className="m-capture__panel">
        <p className="m-capture__title">{t('capture.title', { slot: label })}</p>
        <p>{t('photoProtocol.slots.' + slot + '.instruction')}</p>
        {cameraProblem ? <Alert tone="warning" title={t('capture.errors.' + camera)} /> : null}
        {guide.detectVehicle && detectorFailed && !cameraProblem ? (
          <Alert tone="info" title={t('capture.detectorUnavailable')} />
        ) : null}
        {captureFailed ? <Alert tone="error" title={t('capture.errors.failed')} /> : null}
        <div className="m-actions">
          {cameraProblem ? null : (
            <Button onClick={capture} loading={capturing} disabled={camera !== 'ready'}>
              {t('capture.manual')}
            </Button>
          )}
          {detecting && !cameraProblem ? (
            <Button variant="secondary" aria-pressed={autoCapture} onClick={() => setAutoCapture((value) => !value)}>
              {autoCapture ? t('capture.autoOn') : t('capture.autoOff')}
            </Button>
          ) : null}
          <Button variant="secondary" onClick={onCancel}>
            {cameraProblem ? t('capture.useFile') : t('capture.cancel')}
          </Button>
        </div>
      </div>
    </div>
  );
}
