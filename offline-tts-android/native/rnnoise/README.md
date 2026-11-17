# Bundled RNNoise

Xiph RNNoise 0.1.1, commit `6cbfd53eb348a8d394e0757b4025c6ded34eb2b6`, BSD-3-Clause (COPYING).
Pinned source archive SHA-256: `5900832175a507e1ee5d4d596ff2c2b3a4eb66c59d2a12d33095c79e673e953d`.
Source: https://github.com/xiph/rnnoise/tree/6cbfd53eb348a8d394e0757b4025c6ded34eb2b6
The archive contains the upstream built-in recurrent weights; no download, API or additional model installation is needed for cleanup.

Rebuild ARM64 with `ANDROID_NDK_HOME=<NDK r27c> bash build.sh` (API 26, 16 KiB ELF segment alignment).
Host parity tests: `JAVA_HOME=<JDK> bash build.sh host /absolute/path/librnnoise_offline.so`.
The Java pipeline uses 48 kHz/480-sample frames in PCM16-scaled floats, compensates 480 samples of analysis latency, flushes the final frame and uses antialiased sinc resampling around the native stage.
This is RNNoise, not DeepFilterNet3 or an Adobe model. It does not reconstruct missing speech or guarantee any particular attenuation.
