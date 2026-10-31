"""
Face-swap API using FastAPI + InsightFace.

Run:
  MODEL_PATH=./models/inswapper_128.onnx uvicorn server:app --host 0.0.0.0 --port 8000

Only process images you own or have explicit permission to use.
The InsightFace model weights are NOT bundled; obtain them lawfully and
review their license/usage restrictions before deployment.
"""
from __future__ import annotations

import os
from contextlib import asynccontextmanager
from io import BytesIO
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from starlette.concurrency import run_in_threadpool

MAX_UPLOAD_BYTES = int(os.getenv("MAX_UPLOAD_BYTES", str(10 * 1024 * 1024)))
MAX_IMAGE_SIDE = int(os.getenv("MAX_IMAGE_SIDE", "4096"))
MAX_IMAGE_PIXELS = int(os.getenv("MAX_IMAGE_PIXELS", "16000000"))
MODEL_PATH = Path(os.getenv("MODEL_PATH", "./models/inswapper_128.onnx"))
FACE_ANALYSIS_ROOT = Path(os.getenv("FACE_ANALYSIS_ROOT", "."))
DET_SIZE = int(os.getenv("DET_SIZE", "640"))

face_analyser: Any = None
face_swapper: Any = None
model_load_error: str | None = None


def load_models() -> None:
    """Load InsightFace models once when the API starts."""
    global face_analyser, face_swapper

    if not MODEL_PATH.is_file():
        raise RuntimeError(
            f"Model not found: {MODEL_PATH}. Set MODEL_PATH to your "
            "lawfully obtained inswapper_128.onnx file."
        )

    # Prefer CUDA when available. ONNX Runtime will raise if the installed
    # CUDA/cuDNN runtime is incompatible; set USE_CUDA=0 to force CPU.
    use_cuda = os.getenv("USE_CUDA", "1").lower() not in {"0", "false", "no"}
    providers = (
        ["CUDAExecutionProvider", "CPUExecutionProvider"]
        if use_cuda
        else ["CPUExecutionProvider"]
    )
    ctx_id = 0 if use_cuda else -1

    from insightface.app import FaceAnalysis
    from insightface.model_zoo import get_model

    if not (FACE_ANALYSIS_ROOT / "models" / "buffalo_l").is_dir():
        raise RuntimeError(
            f"Face analysis model folder not found under {FACE_ANALYSIS_ROOT}. "
            "Install an authorized buffalo_l package manually; automatic downloads are disabled."
        )

    face_analyser = FaceAnalysis(name="buffalo_l", root=str(FACE_ANALYSIS_ROOT), providers=providers)
    face_analyser.prepare(ctx_id=ctx_id, det_size=(DET_SIZE, DET_SIZE))
    face_swapper = get_model(str(MODEL_PATH), providers=providers)


@asynccontextmanager
async def lifespan(_: FastAPI):
    global model_load_error
    try:
        load_models()
        model_load_error = None
    except Exception as exc:
        model_load_error = f"{type(exc).__name__}: {exc}"
        print(f"Model initialization failed: {model_load_error}")
    yield


app = FastAPI(
    title="Deepfake Face Swap API",
    version="1.0.0",
    lifespan=lifespan,
)


@app.get("/health")
def health() -> dict[str, str | bool | None]:
    ready = face_analyser is not None and face_swapper is not None
    return {
        "status": "ok" if ready else "model_unavailable",
        "models_loaded": ready,
        "device": "cuda-preferred" if os.getenv("USE_CUDA", "1") != "0" else "cpu",
        "detail": model_load_error,
    }


async def read_image_upload(upload: UploadFile, field_name: str) -> np.ndarray:
    """Read a bounded upload and decode it as an OpenCV BGR image."""
    content_type = (upload.content_type or "").lower()
    if not content_type.startswith("image/"):
        raise HTTPException(status_code=415, detail=f"{field_name} must be an image")

    raw = await upload.read(MAX_UPLOAD_BYTES + 1)
    if not raw:
        raise HTTPException(status_code=400, detail=f"{field_name} is empty")
    if len(raw) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail=f"{field_name} exceeds upload limit")

    image = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail=f"{field_name} is not a valid image")

    height, width = image.shape[:2]
    if width > MAX_IMAGE_SIDE or height > MAX_IMAGE_SIDE:
        raise HTTPException(status_code=413, detail=f"{field_name} dimensions are too large")
    if width * height > MAX_IMAGE_PIXELS:
        raise HTTPException(status_code=413, detail=f"{field_name} has too many pixels")
    return image


def perform_swap(source_img: np.ndarray, target_img: np.ndarray) -> bytes:
    """Run face detection and swap synchronously in a worker thread."""
    if face_analyser is None or face_swapper is None:
        raise RuntimeError("AI models are not loaded; check /health and model licensing/setup")

    source_faces = face_analyser.get(source_img)
    target_faces = face_analyser.get(target_img)

    if not source_faces:
        raise ValueError("No face detected in source image")
    if not target_faces:
        raise ValueError("No face detected in target image")

    # Deterministic default: use the largest detected face in each image.
    source_face = max(
        source_faces,
        key=lambda face: (face.bbox[2] - face.bbox[0]) * (face.bbox[3] - face.bbox[1]),
    )
    target_face = max(
        target_faces,
        key=lambda face: (face.bbox[2] - face.bbox[0]) * (face.bbox[3] - face.bbox[1]),
    )

    result = face_swapper.get(
        target_img,
        target_face,
        source_face,
        paste_back=True,
    )
    ok, encoded = cv2.imencode(
        ".jpg",
        result,
        [int(cv2.IMWRITE_JPEG_QUALITY), 92],
    )
    if not ok:
        raise RuntimeError("Could not encode result as JPEG")
    return encoded.tobytes()


@app.post("/swap", response_class=StreamingResponse)
async def swap_faces(
    source: UploadFile = File(..., description="Image containing the source face"),
    target: UploadFile = File(..., description="Image receiving the source face"),
):
    """Accept multipart fields 'source' and 'target'; return a JPEG image."""
    if face_analyser is None or face_swapper is None:
        raise HTTPException(status_code=503, detail="AI models are not ready. Check /health and install authorized model files.")

    source_img = await read_image_upload(source, "source")
    target_img = await read_image_upload(target, "target")

    try:
        jpeg_bytes = await run_in_threadpool(perform_swap, source_img, target_img)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except HTTPException:
        raise
    except Exception as exc:
        # Log the full exception server-side; do not expose internal details.
        print(f"Face swap processing failed: {type(exc).__name__}: {exc}")
        raise HTTPException(status_code=500, detail="Face swap processing failed") from exc
    finally:
        await source.close()
        await target.close()

    return StreamingResponse(
        BytesIO(jpeg_bytes),
        media_type="image/jpeg",
        headers={"Content-Disposition": 'inline; filename="face-swap.jpg"'},
    )
