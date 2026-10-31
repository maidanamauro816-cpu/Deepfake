# Despliegue del motor Deepfake

## Estado real

El backend FastAPI está preparado para ejecutarse en un servidor Linux. El APK Android es solo el cliente; no ejecuta InsightFace dentro del teléfono.

**No hay un servidor remoto activo ni pesos de IA incluidos en el repositorio.** No se puede inventar una URL ni considerar el motor desplegado hasta que el proveedor termine el deploy y `GET /health` devuelva `models_loaded: true`.

## Requisitos antes de desplegar

1. Un servidor Linux accesible por HTTPS. Para inferencia práctica se recomienda una máquina con GPU NVIDIA compatible; en CPU funcionará mucho más lento.
2. Obtener legalmente los pesos necesarios y verificar sus licencias por separado: `inswapper_128.onnx` y el paquete de análisis facial `buffalo_l`. No están incluidos ni se descargan automáticamente.
3. Montar los pesos en el contenedor:
   - `/models/inswapper_128.onnx`
   - `/models/models/buffalo_l/` (carpeta del paquete de análisis facial)
4. Configurar `MODEL_PATH=/models/inswapper_128.onnx`, `FACE_ANALYSIS_ROOT=/models` y `USE_CUDA=0` para CPU. Para GPU hay que usar imagen y dependencias compatibles con la versión CUDA/cuDNN del host.
5. Configurar HTTPS, autenticación, límites de solicitudes y controles de privacidad antes de exponer el endpoint públicamente.

## Docker

Construir desde `backend-server/`:

```bash
docker build -t deepfake-api .
docker run --rm -p 8000:8000 \
  -v /ruta/a/modelos:/models:ro \
  -e MODEL_PATH=/models/inswapper_128.onnx \
  -e FACE_ANALYSIS_ROOT=/models \
  -e USE_CUDA=0 \
  deepfake-api
```

Comprobar en el propio servidor:

```bash
curl http://127.0.0.1:8000/health
```

Solo cuando la respuesta incluya `"models_loaded":true` está listo el motor. Si aparece `false`, el campo `detail` indica qué modelo falta o no pudo cargarse.

## Conectar el Android

En la aplicación, guardar la URL HTTPS pública que entregue el proveedor, sin agregar `/swap` ni `/health`. Después tocar **PROBAR CONEXIÓN**.

## Proveedor

Este repositorio no tiene credenciales de Render, Hugging Face ni otro proveedor de hosting, por lo que el código puede quedar preparado pero no se puede crear un servidor en una cuenta ajena ni aceptar cargos. El plan gratuito de Render duerme el servicio tras 15 minutos y tiene 512 MB de RAM, por lo que no es adecuado para cargar estos modelos de inferencia. Consultar el costo y los recursos del servidor antes de activarlo.
