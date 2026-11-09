# Telugu Voice Studio — native offline Android test build

Continue this module on `telugu-voice-testing`. See [research and limitations](../docs/research/OFFLINE_NEURAL_UPGRADE.md), [evidence](../docs/evaluation/README.md) and [licenses/source](NOTICE.md).

## Included

- English native Android Studio with Script → Voice → editable Direction → Generate/Preview/Export; bottom tabs for Voices, Models and Settings.
- ARM64 sherpa-onnx 1.12.26 runtime with eSpeak Telugu phonemes; separate checksum-verified Venkatesh and Padmavathi Piper downloads (63.5 MB each). No weights in the APK.
- Heuristic director retained; original factual text preserved in the new Studio. Decimal/version dots no longer split sentences; punctuation alone does not mark excitement. Rate and pauses are editable. Piper does not implement emotion, pitch, emphasis or cloning.
- Foreground local rendering, atomic chunk WAVs, per-chunk checksums, cancel at sentence boundaries, manual resume with exact-job identity, streaming final WAV and document-picker export.
- Actual render telemetry (elapsed time, sampled PSS, duration, Android thermal status, battery temperature), model storage controls, consented local voice-reference recording. Recording does not enable cloning.
- Existing Android system-TTS fallback accessible from Settings.

## Build and verify

JDK 17, Gradle 8.9, Android SDK platform/build-tools 35.0.0:

```sh
python3 offline-tts-android/scripts/verify-bundle.py
bash offline-tts-android/scripts/test-java.sh
gradle --no-daemon --project-dir offline-tts-android :app:assembleDebug
gradle --no-daemon --project-dir offline-tts-android :app:lintDebug
```

The test signing key is intentionally public and committed for repeatable update signatures; never use it as a production credential. The workflow publishes the successful APK, exact tracked source ZIP (without build/cache directories), and SHA256SUMS.txt to an immutable commit release plus `debug-latest`. The immutable release is the authoritative commit-to-artifact mapping; the old rolling tag's git target may predate its replaceable assets.

## Validation status

28 Java checks passed locally; actual Gradle assembly and lint were executed. Linux host inference produced 602.97 s male and 66.60 s female WAVs with the installed-model hashes. These are not Android or listening-quality benchmarks. Release workflow execution is verified separately in the final delivery report.

No physical Nothing Phone was connected. No semantic LLM, true expressive voice engine, on-device model training or voice cloning is integrated. The future semantic prompt/validation contract is not AI inference. The existing fallback remains a baseline with its older export restrictions. Read the device acceptance checklist before treating this as a finished narration product.
