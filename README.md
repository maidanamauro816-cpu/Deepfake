# Deepfake — Android client + face-swap API

This repository contains a native Android client and a separate FastAPI inference service.

## Build the Android APK

Every push to `main` that changes `android-app/**` starts the **Android APK** GitHub Actions workflow. You can also start it manually from **Actions → Android APK → Run workflow**.

When the workflow succeeds, open its run and download the `deepfake-debug-apk` artifact. The APK is a debug build for testing, not a Play Store release.

The Android project uses:
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Kotlin 2.0.21
- JDK 17
- Android SDK 35
- OkHttp 4.12.0

## Connect the Android app to the API

The app currently targets `http://10.0.2.2:8000`, which is the host machine from the Android Emulator. On a physical Android phone, change `BASE_URL` in `MainActivity.kt` to the reachable LAN address of the computer running the API, then rebuild.

Cleartext HTTP is enabled only for local testing. Use HTTPS and authentication before exposing the API outside a trusted development network.

## Start the backend

Install the packages in `backend-server/requirements.txt`, place a lawfully obtained and license-compatible `inswapper_128.onnx` file at `backend-server/models/inswapper_128.onnx`, then run from `backend-server`:

```bash
USE_CUDA=0 MODEL_PATH=./models/inswapper_128.onnx uvicorn server:app --host 0.0.0.0 --port 8000
```

The model weights are intentionally not included. InsightFace model licensing and usage restrictions must be reviewed separately. The backend must be running and the model installed for face-swap processing to work; building the Android APK alone does not provide on-device AI inference.

Only use images of people who have given permission. Clearly disclose manipulated output and do not use it to impersonate or mislead.
