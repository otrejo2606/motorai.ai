/**
 * Guia de puntos de toma con realidad aumentada en el navegador (add-captura-guiada-fotos, design D9).
 *
 * WebXR immersive-ar con hit-test (Chrome en Android con ARCore). El vendedor apunta al suelo bajo el centro del
 * vehiculo, de frente a el, y toca la pantalla: se marcan en el suelo los puntos de las 8 tomas exteriores alrededor del
 * auto y se resalta el de la toma actual. Cuando llega al punto, sale de la RA y toma la foto con la camara guiada
 * (WebXR no entrega la imagen de la camara de forma estandar). Sin soporte, sin bandera o ante cualquier error, la
 * captura sigue con la camara normal. La RA nativa (ARKit, ARCore) queda fuera hasta que exista app nativa.
 *
 * Los tipos de WebXR no estan en lib.dom de TypeScript: se declaran aqui con lo minimo que se usa.
 */

interface XrRigidTransform {
  readonly position: DOMPointReadOnly;
  readonly matrix: Float32Array;
  readonly inverse: XrRigidTransform;
}

interface XrPose {
  readonly transform: XrRigidTransform;
}

interface XrView {
  readonly projectionMatrix: Float32Array;
  readonly transform: XrRigidTransform;
}

interface XrViewerPose extends XrPose {
  readonly views: readonly XrView[];
}

interface XrViewport {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

interface XrWebGlLayer {
  readonly framebuffer: WebGLFramebuffer | null;
  getViewport(view: XrView): XrViewport | null;
}

type XrSpace = object;

interface XrHitTestResult {
  getPose(space: XrSpace): XrPose | null;
}

interface XrHitTestSource {
  cancel(): void;
}

interface XrFrame {
  getViewerPose(space: XrSpace): XrViewerPose | null;
  getHitTestResults(source: XrHitTestSource): readonly XrHitTestResult[];
}

interface XrSession extends EventTarget {
  readonly renderState: { readonly baseLayer?: XrWebGlLayer | null };
  updateRenderState(state: { baseLayer: XrWebGlLayer }): Promise<void> | void;
  requestReferenceSpace(type: 'local' | 'viewer' | 'local-floor'): Promise<XrSpace>;
  requestHitTestSource?(options: { space: XrSpace }): Promise<XrHitTestSource>;
  requestAnimationFrame(callback: (time: number, frame: XrFrame) => void): number;
  end(): Promise<void>;
}

interface XrSessionInit {
  requiredFeatures?: string[];
  optionalFeatures?: string[];
  domOverlay?: { root: Element };
}

interface XrSystem {
  isSessionSupported(mode: 'immersive-ar'): Promise<boolean>;
  requestSession(mode: 'immersive-ar', init?: XrSessionInit): Promise<XrSession>;
}

type XrWebGlLayerConstructor = new (session: XrSession, context: WebGLRenderingContext) => XrWebGlLayer;

function getXr(): XrSystem | undefined {
  if (typeof navigator === 'undefined') return undefined;
  return (navigator as Navigator & { xr?: XrSystem }).xr;
}

function getLayerConstructor(): XrWebGlLayerConstructor | undefined {
  return (window as unknown as { XRWebGLLayer?: XrWebGlLayerConstructor }).XRWebGLLayer;
}

/** Deteccion de capacidad: contexto seguro, WebXR presente e immersive-ar soportado. Nunca lanza. */
export async function isImmersiveArSupported(): Promise<boolean> {
  const xr = getXr();
  if (!xr || typeof window === 'undefined' || !window.isSecureContext || !getLayerConstructor()) return false;
  try {
    return await xr.isSessionSupported('immersive-ar');
  } catch {
    return false;
  }
}

export type ArPhase = 'placing' | 'walking' | 'atMarker';

export interface ArShotPoint {
  /** Grados desde el frente del vehiculo hacia su lado derecho. */
  angle: number;
  /** Metros desde el centro del vehiculo. */
  distance: number;
}

export interface ArCallbacks {
  onPhase: (phase: ArPhase) => void;
  /** La sesion termino (por el usuario, por el sistema o por un error). */
  onEnd: () => void;
}

export interface ArGuideHandle {
  end: () => void;
}

interface Vec3 {
  x: number;
  y: number;
  z: number;
}

/** Distancia horizontal a la que se considera que el vendedor llego al punto. */
const ARRIVAL_METERS = 0.7;

const VERTEX_SHADER = `
attribute vec2 aCorner;
uniform mat4 uProjection;
uniform mat4 uView;
uniform vec3 uCenter;
uniform float uSize;
varying vec2 vCorner;
void main() {
  vCorner = aCorner;
  vec4 world = vec4(uCenter.x + aCorner.x * uSize, uCenter.y + 0.01, uCenter.z + aCorner.y * uSize, 1.0);
  gl_Position = uProjection * uView * world;
}
`;

const FRAGMENT_SHADER = `
precision mediump float;
uniform vec4 uColor;
varying vec2 vCorner;
void main() {
  float radius = length(vCorner);
  if (radius > 1.0 || radius < 0.62) {
    discard;
  }
  gl_FragColor = uColor;
}
`;

interface MarkerRenderer {
  draw(projection: Float32Array, view: Float32Array, center: Vec3, size: number, color: [number, number, number, number]): void;
}

function compile(gl: WebGLRenderingContext, type: number, source: string): WebGLShader {
  const shader = gl.createShader(type);
  if (!shader) throw new Error('shader');
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error('shader');
  return shader;
}

function createMarkerRenderer(gl: WebGLRenderingContext): MarkerRenderer {
  const program = gl.createProgram();
  if (!program) throw new Error('program');
  gl.attachShader(program, compile(gl, gl.VERTEX_SHADER, VERTEX_SHADER));
  gl.attachShader(program, compile(gl, gl.FRAGMENT_SHADER, FRAGMENT_SHADER));
  gl.linkProgram(program);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error('program');
  const buffer = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  const corner = gl.getAttribLocation(program, 'aCorner');
  const projectionLocation = gl.getUniformLocation(program, 'uProjection');
  const viewLocation = gl.getUniformLocation(program, 'uView');
  const centerLocation = gl.getUniformLocation(program, 'uCenter');
  const sizeLocation = gl.getUniformLocation(program, 'uSize');
  const colorLocation = gl.getUniformLocation(program, 'uColor');
  return {
    draw(projection, view, center, size, color) {
      gl.useProgram(program);
      gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
      gl.enableVertexAttribArray(corner);
      gl.vertexAttribPointer(corner, 2, gl.FLOAT, false, 0, 0);
      gl.uniformMatrix4fv(projectionLocation, false, projection);
      gl.uniformMatrix4fv(viewLocation, false, view);
      gl.uniform3f(centerLocation, center.x, center.y, center.z);
      gl.uniform1f(sizeLocation, size);
      gl.uniform4f(colorLocation, color[0], color[1], color[2], color[3]);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    },
  };
}

function toVec(point: DOMPointReadOnly): Vec3 {
  return { x: point.x, y: point.y, z: point.z };
}

/**
 * Posicion del punto de toma: el frente del vehiculo apunta hacia donde estaba el vendedor al marcar el centro y la
 * derecha del vehiculo es el producto cruz del frente con la vertical.
 */
export function shotPosition(center: Vec3, forward: Vec3, point: ArShotPoint): Vec3 {
  const radians = (point.angle * Math.PI) / 180;
  const right = { x: -forward.z, y: 0, z: forward.x };
  const along = Math.cos(radians) * point.distance;
  const side = Math.sin(radians) * point.distance;
  return {
    x: center.x + forward.x * along + right.x * side,
    y: center.y,
    z: center.z + forward.z * along + right.z * side,
  };
}

/**
 * Abre la sesion de RA con la superposicion DOM en {@code overlayRoot}. Lanza si el navegador no la permite; el llamador
 * cae entonces a la camara normal.
 */
export async function startArGuide(
  overlayRoot: HTMLElement,
  points: ArShotPoint[],
  targetIndex: number,
  callbacks: ArCallbacks,
): Promise<ArGuideHandle> {
  const xr = getXr();
  const Layer = getLayerConstructor();
  if (!xr || !Layer) throw new Error('webxr');
  const session = await xr.requestSession('immersive-ar', {
    requiredFeatures: ['hit-test'],
    optionalFeatures: ['dom-overlay'],
    domOverlay: { root: overlayRoot },
  });
  let ended = false;
  const end = () => {
    if (ended) return;
    ended = true;
    void session.end().catch(() => undefined);
  };
  try {
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl');
    if (!gl) throw new Error('webgl');
    const compatible = (gl as unknown as { makeXRCompatible?: () => Promise<void> }).makeXRCompatible;
    if (compatible) await compatible.call(gl);
    await session.updateRenderState({ baseLayer: new Layer(session, gl) });
    const localSpace = await session.requestReferenceSpace('local');
    const viewerSpace = await session.requestReferenceSpace('viewer');
    if (!session.requestHitTestSource) throw new Error('hit-test');
    const hitSource = await session.requestHitTestSource({ space: viewerSpace });
    const renderer = createMarkerRenderer(gl);

    let reticle: Vec3 | null = null;
    let viewer: Vec3 | null = null;
    let center: Vec3 | null = null;
    let forward: Vec3 = { x: 0, y: 0, z: 1 };
    let phase: ArPhase = 'placing';
    const setPhase = (next: ArPhase) => {
      if (next !== phase) {
        phase = next;
        callbacks.onPhase(next);
      }
    };
    callbacks.onPhase('placing');

    session.addEventListener('select', () => {
      if (center || !reticle || !viewer) return;
      const dx = viewer.x - reticle.x;
      const dz = viewer.z - reticle.z;
      const length = Math.hypot(dx, dz);
      if (length < 0.1) return;
      center = reticle;
      forward = { x: dx / length, y: 0, z: dz / length };
      setPhase('walking');
    });
    session.addEventListener('end', () => {
      ended = true;
      try {
        hitSource.cancel();
      } catch {
        // La fuente ya no existe.
      }
      callbacks.onEnd();
    });

    const onFrame = (_time: number, frame: XrFrame) => {
      if (ended) return;
      session.requestAnimationFrame(onFrame);
      const pose = frame.getViewerPose(localSpace);
      const layer = session.renderState.baseLayer;
      if (!pose || !layer) return;
      viewer = toVec(pose.transform.position);
      if (!center) {
        const hit = frame.getHitTestResults(hitSource)[0];
        const hitPose = hit ? hit.getPose(localSpace) : null;
        reticle = hitPose ? toVec(hitPose.transform.position) : null;
      }

      const markers: { position: Vec3; size: number; color: [number, number, number, number] }[] = [];
      if (center) {
        const anchor = center;
        points.forEach((point, index) => {
          const target = index === targetIndex;
          markers.push({
            position: shotPosition(anchor, forward, point),
            size: target ? 0.45 : 0.25,
            color: target ? [0.2, 0.85, 0.45, 0.9] : [1, 1, 1, 0.55],
          });
        });
        const goal = shotPosition(anchor, forward, points[targetIndex] ?? points[0] ?? { angle: 0, distance: 3.5 });
        const away = Math.hypot(viewer.x - goal.x, viewer.z - goal.z);
        setPhase(away <= ARRIVAL_METERS ? 'atMarker' : 'walking');
      } else if (reticle) {
        markers.push({ position: reticle, size: 0.3, color: [0.35, 0.65, 1, 0.9] });
      }

      gl.bindFramebuffer(gl.FRAMEBUFFER, layer.framebuffer);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      for (const view of pose.views) {
        const viewport = layer.getViewport(view);
        if (!viewport) continue;
        gl.viewport(viewport.x, viewport.y, viewport.width, viewport.height);
        for (const marker of markers) {
          renderer.draw(view.projectionMatrix, view.transform.inverse.matrix, marker.position, marker.size, marker.color);
        }
      }
    };
    session.requestAnimationFrame(onFrame);
  } catch (error) {
    end();
    throw error;
  }
  return { end };
}
