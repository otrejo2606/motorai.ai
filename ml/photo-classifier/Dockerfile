# Servicio de inferencia del ángulo de las fotos (CPU). El modelo NO va dentro de la imagen:
# se monta de solo lectura en /models y se elige con MODEL_PATH y MODEL_VERSION.
# Ultralytics es AGPL-3.0: ver LICENSE-NOTICE.md antes de distribuir o desplegar.
FROM python:3.11.10-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PIP_NO_CACHE_DIR=1
ENV PIP_DISABLE_PIP_VERSION_CHECK=1

# OpenCV (dependencia de Ultralytics) necesita estas bibliotecas del sistema.
RUN apt-get update && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 classifier && useradd --system --uid 10001 --gid classifier --home-dir /app --no-create-home --shell /usr/sbin/nologin classifier

WORKDIR /app

COPY requirements-api.txt requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY service ./service
COPY LICENSE-NOTICE.md README.md ./

# Sin telemetría ni descargas de Ultralytics; su carpeta de configuración va a /tmp (tmpfs en compose).
ENV YOLO_OFFLINE=true
ENV YOLO_AUTOINSTALL=false
ENV YOLO_CONFIG_DIR=/tmp/ultralytics
ENV MODEL_PATH=/models/model.onnx
ENV IMAGE_SIZE=224
ENV DEVICE=cpu

USER classifier

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=4)"]

# Un solo proceso: el modelo se carga una vez y las inferencias van de a una (service/classifier.py).
CMD ["uvicorn", "service.main:app", "--host", "0.0.0.0", "--port", "8080", "--workers", "1", "--no-server-header", "--no-access-log"]
