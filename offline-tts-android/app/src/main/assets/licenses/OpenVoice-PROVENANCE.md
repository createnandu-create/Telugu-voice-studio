# PROVENANCE

| Field | Value |
|-------|-------|
| upstream_repo | https://github.com/myshell-ai/OpenVoice |
| upstream_ref | main |
| export_script_sha | 77f182db9f8c2c1904b059c8fd23163a1170f74d |
| export_date | 2026-06-11T14:35:47Z |
| torch_version | 2.12.0+cpu |
| onnx_version | 1.21.0 |
| platform | Linux-6.18.20-1-lts-x86_64-with-glibc2.43 |
| weights_hf_repo | myshell-ai/OpenVoiceV2 |
| sample_rate | 22050 |
| spec_channels | 513 |
| tone_embedding_dim | 256 |
| export_method | upstream SynthesizerTrn via legacy TorchScript ONNX exporter (dynamo=False) |
| strict_load | True -- 0 missing keys, 0 unexpected keys |
| ref_enc_parity | max_abs=8.64e-07  mean_abs=2.45e-07  PASS |
| vc_parity | worst_max_abs=1.08e-02  avg_mean_abs=1.26e-04  PASS |
| community_reference_onnx | https://github.com/nnWhisperer/OpenVoice_ONNX |
| community_reference_openvino | https://docs.openvino.ai/2024/notebooks/openvoice-with-output.html |

## Upstream licence

```
MIT License

Copyright (c) 2023 MyShell.ai

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---
Starting from April 2024, both V2 and V1 are released under MIT License.
Free for commercial use.
(Source: https://github.com/myshell-ai/OpenVoice README + HuggingFace model card)
```
