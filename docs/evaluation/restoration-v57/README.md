# v57 offline recording restoration and speaker enrollment

## Implemented

- Fix the generation blocker shown as `in.aitechapp.teluguoffline.SpeechMarkup`: Android ICU rejects the unescaped closing braces in the static fraction regex. Both closing braces are escaped. A native ICU regression checks the actual shipped regex literals and rejects the old expression. Host JVM/Robolectric alone did not catch this platform difference. Failed jobs now log the cause and show an actionable error instead of a bare class name; error status is no longer a green success icon.
- **Voices → Enhance** creates a new private recording. The original capture/profile is retained. Existing PCM and legacy AAC references can be decoded. The pipeline runs in the foreground with visible stages/progress and cancellation. A journal survives process exit; interrupted cleanup retries from the preserved original, not from partially processed audio.
- Bundled ARM64 **RNNoise 0.1.1**, BSD-3-Clause, native C/JNI. 24 kHz references are sinc-resampled to 48 kHz, filtered with a fourth-order Butterworth 85 Hz high-pass (Q=.541196/1.306563), processed in 480-sample frames, and sinc-resampled back. One-frame latency is compensated and the tail is flushed. Original sample count is retained. The cutoff is fixed, not an adaptive wind estimator. RNNoise is a learned spectral suppressor, not DeepFilterNet3 or an Adobe implementation.
- Optional **WPE room-tail reduction**: single-channel STFT512/hop128, 3-frame prediction delay, 12 taps, two weighted iterations and diagonal regularization. It runs before denoising, is OFF by default, and cannot remove all room reflections. No artificial harmonic reconstruction or speech hallucination is added.
- **Settings → Offline models → Cloning → Speaker fingerprint**: optional pinned ECAPA-TDNN ONNX encoder, 83,540,359 bytes including filterbank, Apache-2.0. Download is explicit, resumable and SHA-256 checked; verified files are reused offline. Explicit Verify and Remove actions are provided.
- **Voices → Enroll speaker fingerprint**: speaker consent is required. At least 10 seconds of audio and 5 seconds above the conservative energy gate are required. A contiguous 10–15-second cleaned stem is selected; ECAPA emits an L2-normalized 192-float vector. `vocal_dna_enrolled.bin`, a cleaned reference and provenance manifest stay in an app-private per-profile enrollment directory. The original recording is not replaced. Deleting that profile deletes its private enrollment too; other profiles remain intact.

## Measured evidence (host, not a phone benchmark)

See `restoration-metrics.json` and the four WAVs. They are generated from **public pretrained synthetic Telugu speech**, with deterministic added noise/echo. They are not the user's personal recordings, and not demonstrations of human voice cloning.

| Test | Result |
|---|---|
| Noise-only region of public TTS + synthetic 60 Hz hum/Gaussian noise | 19.722 dB attenuation |
| Separate deterministic noise-only five-second fixture, after settling | 12.34 dB attenuation |
| WPE post-speech tail, deterministic delayed-echo fixture | 5.140 dB reduction |
| Audio duration | 20.179 seconds; input/output sample counts match |
| RNNoise elapsed time for this fixture | 0.419 s on this host; not Snapdragon performance |
| ECAPA output | 192 finite floats, L2 norm 1.0 |
| Java vs independent NumPy frontend maximum absolute difference | 3.814697265625e-6 |
| Java vs Python ONNX normalized embedding cosine | 1.0 for identical synthetic input; **not voice similarity or cloning accuracy** |

WPE with only five taps gave 0.475 dB on the same delayed-echo fixture; extending to 12 taps covers the first 50 ms reflection and improved this particular result. Neither figure predicts performance in arbitrary rooms. The audio and scripts allow repeatable comparison.

## Reproduce

```bash
python3 offline-tts-android/scripts/test-android-regex.py
bash offline-tts-android/scripts/test-java.sh
bash offline-tts-android/scripts/test-restoration.sh \
  "$PWD/docs/evaluation/delivery-v07/samples/tiny-female-youtube-directed.wav" \
  /tmp/restoration-check
# Download the two pinned ECAPA assets separately, then supply a host ONNX Runtime JAR:
python3 offline-tts-android/scripts/audit-speaker-encoder.py MODEL_DIR ORT_HOST_JAR /tmp/ecapa-check
# NumPy and onnxruntime are needed only for this independent development audit.
gradle --no-daemon --project-dir offline-tts-android :app:assembleDebug :app:assembleFresh :app:testDebugUnitTest :app:lintDebug
```

Encoder repository: `vedk00/ecapa-voxceleb-speaker-embedding-onnx`, revision `a9cb9321b07b4ee5b0ea47fdd25242d9cacd824a`. SHA-256 values live in `SpeakerModelStore.java`. Despite the file name `fbank-80x201-f32.bin`, its actual contiguous layout is **[201 frequency bins, 80 mel bands]**. Java uses the actual table layout, periodic Hamming400, 160-sample hop, centered zero padding, power spectrum, log10 mel with 80 dB floor and per-utterance mean subtraction. Independent output comparison verifies that contract.

## Not delivered as working features

- Absolute zero reverb / -infinity dB decay, guaranteed 35 dB suppression in all environments, recovery of clipped/masked speech, or condenser-microphone equivalence. These are not established by this pipeline.
- **Personal cloned narration.** A 192-D speaker-verification vector is not a compatible conditioning input to the installed fixed-speaker Piper or Indic Tiny graphs. No arbitrary latent injection, invented IDs, fake trained voice, or selectable “My Studio Voice (Cloned)” tile is added. The vector does not separately measure vocal-tract shape, chest anatomy or F0. A compatible Telugu synthesis checkpoint and real on-device clone output remain necessary.
- A new INT8 HiFi-GAN vocoder. Existing Piper already has its VITS waveform generator; Indic Tiny already uses Vocos. They are not Griffin–Lim synthesizers. Swapping an unrelated vocoder would not create a clone.
- Physical Nothing Phone enrollment/airplane-mode playback, real wind/motor/traffic restoration, 10–20-minute thermal/RAM measurements, and Telugu human listening acceptance. The build and host results are not substitutes for these checks.

All model downloads are explicit installation operations. Enhancement, enrollment, speech generation and exports use local computation; no paid API, remote inference or personal-audio upload is introduced.

## Local build verification, 2026-11-17

Actual Gradle `assembleDebug`, `assembleFresh`, `testDebugUnitTest`, and `lintDebug` completed successfully. JUnit/Robolectric: **21 tests, 0 failures, 0 errors**. Lint: **0 errors, 16 warnings** (dependency updates, ARM64-only ChromeOS warning, storage-cache API suggestions, existing UI/style warnings). The eight Java test suites, native RNNoise regression, seven ICU regex checks and bundle integrity checks passed. Both APKs passed `apksigner verify` and 16 KiB ZIP alignment verification; the new RNNoise ELF also has 16 KiB-aligned load segments. APK versionCode is 57 and the existing public debug certificate is unchanged. GitHub CI and immutable release links are recorded in the release itself.

Audio attribution: the source fixture is the public Indic Tiny Female synthesis in `../delivery-v07/samples/tiny-female-youtube-directed.wav`, derived from the CC-BY-4.0 Indic Tiny Telugu model by sulabhkatiyar (pinned revision in `TinyModelStore`). These new WAVs add deterministic synthetic corruption/restoration to that fixture. Model provenance and license are included in app assets; no user recording was used.
