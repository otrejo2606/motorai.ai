import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { PHOTO_SLOT_CODES, type PhotoSlotCode } from '@motoria/api';
import { Alert, Button } from '@motoria/ui';
import { SLOT_GUIDES } from './slotGuides';
import { startArGuide, type ArGuideHandle, type ArPhase, type ArShotPoint } from './webxr';

type GuidePhase = 'starting' | ArPhase | 'failed';

/** Tomas exteriores con punto en el suelo, en el orden del protocolo. */
const EXTERIOR_SLOTS = PHOTO_SLOT_CODES.filter((code) => SLOT_GUIDES[code].ar !== null);

export interface ArGuideProps {
  slot: PhotoSlotCode;
  /** El vendedor llego al punto y quiere tomar la foto: se abre la camara guiada. */
  onArrived: () => void;
  /** Salio de la RA o no se pudo iniciar: vuelve a la tarjeta de la toma. */
  onExit: () => void;
}

/**
 * Superposicion DOM de la sesion WebXR (design D9). Mientras la sesion no arranca, o si falla, se ve como una capa
 * normal de la pagina con la salida a la camara guiada.
 */
export function ArGuide({ slot, onArrived, onExit }: ArGuideProps) {
  const { t } = useTranslation();
  const overlayRef = useRef<HTMLDivElement | null>(null);
  const handleRef = useRef<ArGuideHandle | null>(null);
  const arrivedRef = useRef(false);
  const callbacksRef = useRef({ onArrived, onExit });
  callbacksRef.current = { onArrived, onExit };
  const [phase, setPhase] = useState<GuidePhase>('starting');
  const label = t('photoProtocol.slots.' + slot + '.label');

  useEffect(() => {
    const root = overlayRef.current;
    const points: ArShotPoint[] = EXTERIOR_SLOTS.map((code) => SLOT_GUIDES[code].ar ?? { angle: 0, distance: 3.5 });
    const targetIndex = EXTERIOR_SLOTS.indexOf(slot);
    if (!root || targetIndex < 0) {
      setPhase('failed');
      return undefined;
    }
    let active = true;
    startArGuide(root, points, targetIndex, {
      onPhase: (next) => {
        if (active) setPhase(next);
      },
      onEnd: () => {
        if (!active) return;
        active = false;
        handleRef.current = null;
        if (arrivedRef.current) callbacksRef.current.onArrived();
        else callbacksRef.current.onExit();
      },
    })
      .then((handle) => {
        if (active) handleRef.current = handle;
        else handle.end();
      })
      .catch(() => {
        if (active) setPhase('failed');
      });
    return () => {
      active = false;
      handleRef.current?.end();
      handleRef.current = null;
    };
  }, [slot]);

  const finish = (arrived: boolean) => {
    arrivedRef.current = arrived;
    if (handleRef.current) {
      handleRef.current.end();
    } else if (arrived) {
      onArrived();
    } else {
      onExit();
    }
  };

  const message =
    phase === 'starting'
      ? t('capture.ar.starting')
      : phase === 'placing'
        ? t('capture.ar.placeCenter')
        : phase === 'walking'
          ? t('capture.ar.walk', { slot: label })
          : phase === 'atMarker'
            ? t('capture.ar.atMarker')
            : null;

  return (
    <div ref={overlayRef} className="m-ar" role="dialog" aria-modal="true" aria-label={t('capture.ar.title', { slot: label })}>
      {phase === 'failed' ? (
        <Alert tone="warning" title={t('capture.ar.failed')} />
      ) : (
        <p className="m-ar__message" aria-live="polite">
          {message}
        </p>
      )}
      <div className="m-actions">
        {phase === 'walking' || phase === 'atMarker' || phase === 'failed' ? (
          <Button onClick={() => finish(true)} variant={phase === 'walking' ? 'secondary' : 'primary'}>
            {phase === 'failed' ? t('capture.ar.useCamera') : t('capture.ar.takePhoto')}
          </Button>
        ) : null}
        <Button variant="secondary" onClick={() => finish(false)}>
          {t('capture.ar.exit')}
        </Button>
      </div>
    </div>
  );
}
