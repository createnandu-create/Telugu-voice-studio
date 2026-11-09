# Evidence and acceptance status

2026-11-09 host inference: sherpa-onnx 1.12.26, Linux x86_64 CPU, two threads, no inference service. Exact model hashes and raw measurements are in `host-metrics-2026-11-09.json`. The male fixture loops 10 original tutorial passages to stress duration; it is **not** an independent 10-minute semantic comprehension test. `long-form-stress-script.txt` records the exact repeated input. Female covers the 10 distinct passages once.

| Voice | Actual audio | Host render time | Chunks | Cumulative process peak RSS |
|---|---:|---:|---:|---:|
| Venkatesh | 602.97 s | 49.74 s | 114 | 413,704 KiB |
| Padmavathi | 66.60 s | 9.68 s | 10 | 413,704 KiB |

RSS is the process lifetime maximum, so the second row is not a separate female peak measurement. Model load time is recorded separately. WAVs are mono PCM16 at 22,050 Hz. `*-host-preview.wav` are the first 15 seconds of the generated WAVs, not edited examples of expressive cloning. These samples prove host waveform generation only. Native Android output, pronunciation, perceived naturalness and emotion were not established by these measurements.

Reproduce using `scripts/host-benchmark.py --original-models <directory> --phonemes <espeak-ng-data> --output <directory>`, with `sherpa-onnx==1.12.26` and NumPy. Original models must be arranged as `<directory>/<voice>/te_IN-<voice>-medium.onnx`; installer provenance is in ModelCatalog. Extract the bundled phonemes ZIP for the phoneme directory. The benchmark uses fresh synthesis; exact audio hashes can vary with sampling/runtime.

## Physical phone acceptance — NOT EXECUTED

1. Install the ARM64 APK on Nothing Phone (1). Download one voice in Models; verify installed status. Deny microphone permission and check narration still works.
2. Enable airplane mode, close/reopen, paste a short Telugu sentence, generate, play, export via document picker. Confirm no network dependency.
3. Listen to “నమస్కారం”, numerals/decimals, Telugu-English mixed words and the user's reference scripts. Record errors verbatim; no automatic 90% label.
4. Render a real 10–20-minute script. Switch off the screen; reopen, cancel, restart the app and Resume. Ensure order/identity remain stable, no partial WAV becomes final, and changed voice/text invalidates cached chunks.
5. Check sampled PSS, output time, thermal status and battery temperature in View render measurements. Compare cold and warm runs. Test low storage and interrupted model installation.
6. Blind review at least 20 original scripts across tutorials, product reviews, numbers, warnings and emotional stories. Use separate 1–5 ratings for pronunciation, naturalness, emotion relevance, continuity and listening fatigue. Speaker similarity is N/A for these fixed voices; cloning remains unavailable.

A successful Gradle build and host WAV do not satisfy these phone checks. The final release remains a test build.
