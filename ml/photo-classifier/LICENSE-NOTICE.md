# Aviso de licencia: Ultralytics YOLO (AGPL-3.0)

Decisión del dueño del 2026-11-27 (design D2 de `openspec/changes/add-clasificador-fotos-yolo/`):

- Durante el desarrollo se usa **Ultralytics YOLO con su licencia gratuita AGPL-3.0**.
- **Antes del lanzamiento** se negociará y firmará con Ultralytics la **licencia Enterprise** (puerta 0.4). Sin ella firmada, este servicio no se pone en producción para usuarios reales.

## Qué obliga la AGPL-3.0 mientras se use

- Ultralytics (`ultralytics` en `requirements.txt` y `requirements-train.txt`) es AGPL-3.0. Este servicio lo importa y lo sirve por red, así que, mientras se use con esa licencia, **el código fuente de esta carpeta (`ml/photo-classifier/`) se distribuye bajo AGPL-3.0**.
- Sección 13 de la AGPL-3.0 (interacción por red): quien interactúe con el servicio a través de una red tiene derecho a recibir el código fuente correspondiente de la versión que está corriendo, incluidas nuestras modificaciones. Hoy el servicio solo lo llama el backend de Motoria por la red privada (WireGuard), pero cualquier usuario cuya foto se clasifique podría considerarse quien interactúa de forma remota. Mientras rija la AGPL, Motoria debe poder entregar este código a quien lo pida.
- Los pesos entrenados con Ultralytics y el modelo exportado (`.pt`, `.onnx`) se tratan como sujetos a la misma licencia hasta tener la Enterprise. Ultralytics sostiene que los modelos entrenados con su código son obra derivada.
- **Lo que no se ve afectado:** el backend Java y el portal hablan con el servicio solo por HTTP (contrato de design D3 de `add-captura-guiada-fotos`). No enlazan ni incluyen código de Ultralytics. Esa separación se mantiene a propósito: el servicio es un proceso aparte y no se copia código de Ultralytics a otro módulo.
- No usar el modelo exportado en el navegador (`onnxruntime-web`, design D8 de `add-captura-guiada-fotos`) hasta tener la licencia Enterprise. Eso sería distribuirlo a cada usuario.

## Qué hacer antes del lanzamiento (puerta 0.4)

1. Firmar la licencia Enterprise de Ultralytics que cubra el uso comercial del servicio por red, los pesos entrenados y, si se quiere, la exportación al navegador.
2. Guardar el contrato fuera del repositorio y registrar aquí la fecha y el alcance, sin datos confidenciales.
3. Quitar o ajustar este aviso y el campo `license` de `pyproject.toml`.

Si la negociación no prospera, la alternativa es cambiar a un clasificador con licencia permisiva (`timm` o `torchvision`, Apache-2.0 o BSD) manteniendo el mismo contrato HTTP. El backend no cambia.

Texto completo de la licencia: https://www.gnu.org/licenses/agpl-3.0.html
Licencias de Ultralytics: https://www.ultralytics.com/license
