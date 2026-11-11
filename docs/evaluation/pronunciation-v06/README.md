# v0.6: repair the speech input, then compare voices

The user still hears నమస్కారం as అమస్కారం after v0.5 mastering. We did **not** label that complaint solved by padding or EQ. No personal recordings are published in this directory.

## Confirmed Piper interoperability defect

Sherpa-onnx **v1.12.26** [`PiperPhonemesToIdsVits`](https://github.com/k2-fsa/sherpa-onnx/blob/v1.12.26/sherpa-onnx/csrc/piper-phonemize-lexicon.cc) emits `BOS, n, PAD, …, EOS`. The original [Piper phoneme encoder](https://github.com/rhasspy/piper-phonemize/blob/master/src/phoneme_ids.cpp) inserts **PAD immediately after BOS**: `BOS, PAD, n, PAD, …, EOS`. The first `n` is ID 26; it was present, but its input context differed from Piper's tokenizer. No phoneme-ID file or Telugu spelling change can supply this missing position through sherpa's single-ID token map.

`PiperInputRepair.java` adds a four-op ONNX input adapter: Slice BOS/rest, Concat a zero PAD, and Add 1 to `input_lengths`. Learned weights, native rate, metadata, Piper runtime binaries and original user text remain unchanged; existing graph inputs are rewired through the adapter. The app already uses `max_num_sentences=1`, so each inference begins at one BOS. Do not use this adapter with a frontend that already supplies BOS+PAD, or with concatenated multi-sentence token arrays.

Both pinned models passed ONNX checking. All **350 original weight initializers** in each model are byte-identical. The repaired model receiving legacy sherpa IDs produced **sample-identical** output to the original model receiving the correct Piper IDs (zero inference noise for the deterministic test): max absolute difference **0.0**, male 15,616 samples, female 21,760 samples. See `piper-repair-equivalence.json`; reproduce with `scripts/verify-piper-repair.py` and the local original/repaired models. Actual sherpa inference with the repaired files also generated the six `piper-repaired-*.wav` fixtures at production noise settings.

Existing verified Piper files migrate **locally without downloading**; migration retains a `.pre-bos-pad.onnx` backup. A receipt pins the new checksum. Repeat opens reuse the repaired file. New downloads get the same adapter after the existing metadata conversion. Model checksum changes invalidate the old render-cache identity, so **Generate again** creates new audio instead of reusing old chunks. Scripts, projects, personal references and old outputs are retained. Explicit model deletion removes its model backup too. The production streaming repair passed on both full 63.5 MB models with a **128 MB Java heap cap on Linux**; this is not a phone RAM benchmark.

This is a confirmed input-contract fix, **not proof that all Telugu pronunciation or the perceived initial /n/ is now correct**. The legacy duration inspection in `piper-duration-audit.json` gave `n` nonzero model duration (male 2 frames; female 2–5 frames across five random runs). Those alignments are model predictions, not a human phonetic transcription. The newly supplied v0.5 raw WAV starts above a 32/32768 threshold at 4.67 ms; its mastered counterpart at 150.27 ms. Padding existed but the user still heard the error. DAC startup therefore remains an unproved explanation.

## An actual second neural path

Indic Tiny Telugu revision `55cfc683fafb55326a0a01932a3b2bb4374c4fc0` now has an Android-callable Java pipeline: character IDs → text encoder → length regulation → four-step flow → Vocos → periodic-Hann ISTFT. Three verified graphs total **91,011,543 bytes**, shared by male/female. This bypasses eSpeak for a meaningful comparison. Its known character sequence for నమస్కారం is `[0,45,0,50,0,59,0,74,0,26,0,61,0,52,0,11,0]`; the initial Telugu న is preserved. Unknown text is rejected with a dictionary hint, never silently discarded. Supported input is Telugu script and the pinned punctuation vocabulary; this port does not reproduce the entire upstream multilingual normalizer.

The **same Java inference code** generated all `male-*.wav` and `female-*.wav` here on a Linux x86_64 CPU without network access. A controlled zero-noise నమస్కారం run was sample-identical to the upstream Python implementation: **17,152 samples, maximum difference 0.0**. Production uses deterministic per-segment Gaussian noise for resume stability; Java and NumPy random seeds are not interchangeable. Graph stages are cancellable between calls. Native output is 24 kHz PCM16 mono; playback export adds only a 150/100 ms silent guard, without resampling/EQ. Original chunks are kept. Existing Piper studio mastering is retained separately.

ORT 1.31 is isolated as `libtinyort.so`; the original Piper runtime is untouched. The reproducible library-name changes, licenses and LGPL encoder source are documented in `offline-tts-android/third-party/README.md`. Host Java inference also passed with the older sherpa runtime preloaded. Android ARM64 inference, thermal behavior, 10–20-minute Tiny performance and human listening quality remain **unverified**. The UI marks Tiny experimental. No trained semantic director, emotional clone or 90% naturalness claim is made.

## Files and UI

- `piper-repaired-VOICE-0.wav`: నమస్కారం; `-1`: నమస్కారం ఫ్రెండ్స్!; `-2`: మీ అందరికీ నమస్కారం.
- `male-N.wav` / `female-N.wav`: the same three cases through Java Indic Tiny; original 24 kHz output.
- `Namaskaram-Piper-Male.wav` and `…Female.wav`: repaired single-word samples with a native-rate 150/100 ms playback guard. These are public pretrained model voices, **not personal recordings or cloned voices**.
- `male-0.mp3`: real local Jump3r/LAME 320 kbps MP3, independently decoded with FFmpeg (44.1 kHz mono). MP3 adds normal encoder delay/padding; it does not repair speech.

The app's **Check నమస్కారం pronunciation** action synthesizes the three contexts with the selected installed voice at neutral speed, without replacing the editor script. Save audio now offers WAV or genuine offline MP3, progress/cancel, the saved filename, Open and Share. Save remains on screen at 360 dp. Model verification shows an active state and a timestamped SHA-256 result in the model card. Advanced original/processed choices are described under Audio options. Generic “Studio update / Got it” dialogs have been removed.

Host tests and builds are reported for the exact published commit. UI captures are Robolectric native-graphics renders, not screenshots of a physical Nothing Phone (1). Human Telugu listening and actual phone validation remain the next acceptance checks.

## Local release validation

The actual requested Android debug assembly passed. All **12 Android/JUnit/UI tests**, six standalone Java suites and bundle integrity checks passed. Lint: **0 errors, 14 warnings**. APK signature and 16 KB ZIP alignment passed; the signing certificate is unchanged from v0.5, allowing an in-place update. `local-validation.json` records local artifact details; use the release's SHA256SUMS for the CI artifact. Native UI renders are in `../ui-v06`. No physical phone was connected.
