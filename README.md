# Deepfake — Android client + AI backend

This repository contains a native Android client and a separate FastAPI inference service. The integration branch keeps the existing Android app and documents Locally Uncensored as an architecture reference; no Locally Uncensored source code has been copied into this branch.

## Build the Android APK

The **Android APK** GitHub Actions workflow runs on pushes to `main` and `integration/locally-uncensored-review` when Android files change. It can also be started manually from **Actions → Android APK → Run workflow**.

When the workflow succeeds, open the run and download the `deepfake-debug-apk` artifact. This is a debug build for testing, not a Play Store release.

## Connect from a physical Android phone

Open the app and enter the address of the computer or server that runs the API, for example:

```
http://192.168.1.20:8000
```

Use the computer's actual local network IP, not `10.0.2.2` (that special address is only for the Android Emulator). The phone and computer must be on a network that allows them to communicate. Save the address in the app; it persists between launches.

## Start the backend

Use Python 3.10 or 3.11. Install CPU dependencies:

```bash
pip install -r requirements.txt
```

Place authorized model files at the paths listed in `backend-server/.env.example`, then run from `backend-server`:

```bash
USE_CUDA=0 MODEL_PATH=./models/inswapper_128.onnx uvicorn server:app --host 0.0.0.0 --port 8000
```

For a compatible NVIDIA host, review `requirements-gpu.txt` and the CUDA/cuDNN compatibility requirements before using `USE_CUDA=1`.

**Important:** the model files are not bundled. InsightFace's code is MIT licensed, but its pretrained weights have separate restrictions; its current notice says public pretrained models are for non-commercial research and requests contacting InsightFace for InSwapper licensing. Do not use or distribute those weights until the required permission is obtained. See [the model catalog](docs/MODEL-CATALOG.md) and [third-party notices](THIRD-PARTY-NOTICES.md).

The API must be running and both model components must load for face swapping to work. The Android APK alone does not include AI inference. The current API is a development service; do not expose it publicly without HTTPS, authentication, rate limiting and privacy controls.

## Locally Uncensored integration status

The upstream project is AGPL-3.0-only and uses Tauri/Rust/React. This branch contains an architecture and license review only; it does not yet contain its modules or Android runtime. Any future source integration must preserve the AGPL obligations and separately clear all model and engine licenses.

## Responsible use

Only process images of people who have given permission. Clearly disclose manipulated output and do not use it to impersonate or mislead.
