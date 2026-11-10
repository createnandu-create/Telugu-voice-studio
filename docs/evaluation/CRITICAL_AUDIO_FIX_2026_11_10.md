# P0 critical audio pipeline investigation and guarded fix (2026-11-10)

Target: Nothing Phone (1), Android 15, Snapdragon 778G+, 8GB. Work branch: `telugu-voice-testing`.

## Corrections to the supplied diagnosis

1. **First /n/ in `నమస్కారం`**: eSpeak NG audit recorded `nˈamaskˌaːran`; initial /n/ exists in phonemizer output. User WAVs produced ~53-69 ms near-silence before first easily detectable waveform. AudioTrack startup latency is **not proven**: the app's Compose UI plays completed WAV files with Android `MediaPlayer`, NOT a manually started unbuffered `AudioTrack`. If a missing phoneme is missing in raw WAV, playback pre-roll cannot reconstruct it. On-phone live vs exported vs independent player A/B is essential.
2. **Vocoder**: Piper VITS Venkatesh and Padmavathi are already fully pretrained waveform-generating neural models. The app calls `sherpa-onnx OfflineTts.generate(...)` and receives PCM float samples. There is no Griffin-Lim step to replace in this pipeline. A separately trained quantized HiFi-GAN cannot be blindly appended; intermediate features, sample rate and architecture would need exact compatibility.
3. **Metallic sound**: low/high shelves can change spectral balance but cannot add real human prosody, recover absent phonemes or retrain poor acoustic representations. The +3 dB / 180Hz shelf may make some voices more boxy; this is opt-in for A/B only, not a validated quality fix. Peak clipping was not the main issue in user WAVs.
4. **Resampling and mastering**: native synthesis and WAV remain PCM16 mono at 22,050 Hz. Do not upsample to 44,100 Hz simply to claim more fidelity; a band-limited resampler is needed only at a specific output codec/device boundary, and MP3 encode is currently not part of the app. -14 LUFS / -1dBTP true-peak are targets to validate at final mastering, not effects of a hard clamp or sample-rate conversion. A mono voice track is appropriate.

## Implemented guardrails in current test branch

- `PcmWave.merge()` adds **150 ms head** and **100 ms tail** *only* to final WAV, in integer PCM sample frames (at 22050 Hz, 3308 head frames / 2205 tail frames). Original synthesized PCM chunks and exact per-sentence cached files remain byte-for-byte intact.
- `PronunciationLexicon.apply()` strips only initial BOM, ZWNJ, ZWSP and ZWJ (not characters inside words). Visible original script and in-word shaping untouched.
- `WarmthEq.java`: independent, completely local **optional** - not neural - RBJ low shelf +3 dB @180 Hz and high shelf -2.5 dB @6000 Hz, conservative headroom. It processes **final WAV only**; in app Settings → Audio Quality; preset is **OFF** by default. Raw PCM remains default and users can A/B.
- `SpeechSettings`, `StudioViewModel.kt`, `StudioActivity.kt`, `StudioJobService.java` preserve explicit EQ selection, passing choice to `PcmWave.merge(..., warmthEq)` and `metrics.json`.
- `OfflinePipelineTest` adds first 128 PCM byte preservation and exact head/tail length checks; `WarmthEqTest` exercises streaming block consistency, silence, spectral balance and sample headroom; test script includes new source and test.

## Verification status

- Java and Gradle builds for **these new changes are not yet independently verified** in this chat runtime. Android SDK/Gradle unavailable in this runtime; GitHub connector has not returned a workflow run associated with latest commit. Do not provide a new APK asset until CI confirms successful `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, signature and ZIP alignment checks.
- Experimental A/B output WAV files were generated **locally** from the user's two existing WAVs, native 22,050 Hz PCM16. Files have correct headers, source duration +250 ms and no sample-rate upconversion. Filtered outputs demonstrate only a deterministic EQ experiment.
- **/n/ audible repair and metallic artifact elimination have NOT been proven**. Requires user's original WAV and fresh native-device generation, separate listeners, documented perception scores and proper on-device inference testing.

## Acceptance tests on Nothing Phone (1)

1. Install a newly built APK; use one installed Piper voice, generate `నమస్కారం` alone, `నమస్కారం ఫ్రెండ్స్!`, and the user's full script with raw EQ OFF. Log exact normalized input, eSpeak phonemes, model revision/hash, raw chunk WAV and final padded WAV.
2. Compare raw chunk file vs padded final output in the same player and with Bluetooth, headphones, built-in speaker. If /n/ is already missing in raw chunk, this is a synthesis/phonemizer-model problem, not a DAC startup issue.
3. Compare same voice EQ OFF vs EQ ON with loudness-matched playback. Do not just compare differing volume. Do not conflate lower metallic resonance with emotional naturalness.
4. Run host A/B on pinned Piper Venkatesh/Padmavathi and Indic Tiny TTS Telugu; judge pronunciation by blinded human listening. Indic Tiny is not yet an integrated Android engine.
5. Check no drift, unwanted double-leading silence, correct WAV duration/content, playback, export, app restart, all existing voice and model data preserved.
6. Only if native generation actually loses /n/ and tested model improves, consider a vetted new model, pronunciation dictionary or compatible acoustic architecture. Avoid random DSP “miracle cures”.

Public upstream:
- https://github.com/k2-fsa/sherpa-onnx
- https://github.com/rhasspy/piper
- https://huggingface.co/sulabhkatiyar/indic-tiny-tts-telugu

**Do not claim a verified studio-grade voice or release until evidence is attached.**
