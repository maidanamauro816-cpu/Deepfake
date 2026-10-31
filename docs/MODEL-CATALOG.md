# Model catalog and licensing gate

This repository does not redistribute pretrained model weights.

| Component | Role | Current status | License / action |
|---|---|---|---|
| InsightFace Python package | Face detection and analysis code | Dependency only | Upstream code is MIT; this does not grant rights to pretrained weights. |
| `buffalo_l` | Face detection/recognition weights used by `FaceAnalysis` | Required by current backend, not bundled | InsightFace says pretrained models are for non-commercial research and directs users to request model licensing. Do not deploy commercially without written permission. |
| `inswapper_128.onnx` | Face-swap weights | Not bundled; required by current endpoint | InsightFace's current notice says to contact them for licensing. Obtain written authorization before distribution or commercial use. |
| Locally Uncensored | Reference AI-studio project, not a model | Evaluation only; no upstream source copied into this app | Upstream project is AGPL-3.0-only. Its code license does not license model weights or third-party engines. |

## Required before enabling production inference

1. Obtain the exact model files from their rights holder or an authorized source.
2. Save the model license/permission and model version alongside deployment records.
3. Verify the license covers the intended use, territory, redistribution, hosting and number of users.
4. Store the files outside Git (for example, a private server volume); never commit model weights or secrets.
5. Set `MODEL_PATH` and run the API health check after both models load.
6. Do not mark the model as approved until the written license has been reviewed.

Official references:
- InsightFace license and model notice: https://github.com/deepinsight/insightface
- InSwapper example and model asset: https://github.com/deepinsight/insightface/tree/master/examples/in_swapper
- InsightFace licensing: https://www.insightface.ai/

**Current state:** the Android client can connect to a configurable API URL, but no model license has been obtained or verified by this repository. Face swapping is therefore not production-ready.
