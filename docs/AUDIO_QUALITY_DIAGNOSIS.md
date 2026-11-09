# Audio quality blocker — uploaded Telugu narration WAVs (2026-11-09)

## User-observed symptom
The user's generated voice sounds like it is speaking inside a metal box: hollow / nasal / robotic, occasional rattly or click-like consonants, no believable human vibration, emotional delivery far below the desired YouTuber narrator.

The user supplied two WAVs from the Android app. Their SHA-256 hashes were **identical**:
`8a43dec210baaad3cf6fd3099c080e19e8bff464c9520c1168473b69a3e49b95`.
Do not upload the user's audio without explicit consent; analyze locally.

## Measured facts (Python soundfile/numpy)
- 24,000 Hz, single-channel, signed 16-bit PCM WAV.
- Exactly 295,560 samples = 12.315 seconds, 591,164 bytes.
- Peak amplitude ~0.99997, RMS ~0.1873 (-14.5 dBFS).
- ~24 samples at or above 0.999 full-scale (~0.0081% of samples). This is minor peaking; clipping is NOT shown to be the primary cause of robotic voice.
- Exactly zero-sample regions ~0.34, 0.39, 0.35, 0.44, and 0.50 seconds at phrase boundaries. These account for some long pauses, but starts/ends approach silence; do NOT assert proven WAV splice clicks.
- Both files play as ordinary WAVs. The audio format itself is not corrupted.
- These observations do **not** establish which Android TTS engine or speaker voice generated the audio. Get engine package, voice ID, reported quality and latency on device.

## Root-cause hypothesis, highest priority first
1. **Source voice timbre/model is insufficient.** Current app calls installed Android `TextToSpeech`. It loads no dedicated Telugu expressive neural TTS checkpoint; changing pitch/rate does not give it human emotion or new acoustic detail. High probability cause of robotic/boxy quality.
2. App previously selected the **first** available offline Telugu `Voice` from a set without ordering by quality; this could choose an inferior installed voice. Fix: list all, sort by `Voice.getQuality()` (higher better) and `getLatency()`, allow manual A/B. Quality integer is engine metadata, NOT a listening benchmark.
3. Strong `setPitch` changes (old range ~0.94–1.16) and `setSpeechRate` changes can make already-artificial voices sound worse. Fix: default **neutral pitch 1.0 / rate 1.0** and cap optional modulation to close to neutral.
4. WAV signal has peaks close to full-scale. Provide small headroom, mild de-box EQ and low-frequency rumble removal as **optional export controls**, never as a claim to restore true human pitch texture or regenerate missing harmonics.
5. Verify playback and WAV export independently. If both sound equally metallic, the source voice/model is suspect. If only the WAV sounds bad, inspect `synthesizeToFile`, PCM format/sample rate, byte boundaries, stitching, peak and scaling.

## Implemented diagnostic improvements (test branch)
- `MainActivity.java`: Offline Telugu voice spinner sorted by API-reported quality; selected voice name, quality and latency shown; no online fallback.
- Default **Natural baseline** checkbox: original voice, rate 1.0, pitch 1.0. Optional mild expression mode clamps rate to [0.94,1.06], pitch to [0.96,1.05]; this is still NOT neural expressiveness.
- Optional **WAV clarity EQ** checkbox (OFF by default to permit raw-vs-processed comparison).
- `AudioPolish.java`: fully local streaming mono/stereo PCM16 high-pass 85 Hz, bell -3 dB at 440 Hz (Q 0.8), bell +1.7 dB at 2.4 kHz (Q 0.7), gain ×0.86, hard guard rail ±0.89, 6 ms boundary fades. This is a controlled experiment: settings might improve or degrade this particular voice, so require listening tests; no heavy noise reduction, reverb or invented audio content.
- Java `AudioPolishTest` verifies streaming buffers, mono/stereo, fades, no artificial silence noise, and malformed frames; `NarrationDirectorTest` stays in the CI build gate.

## Mandatory verification on Nothing Phone (1)
1. Run the build; do not claim APK until `:app:assembleDebug` passes. Install with Wi-Fi + mobile data OFF (after downloading required system voice pack).
2. Record/export **same script** under these conditions, names in filenames:
   - A: chosen offline voice, Natural ON, EQ OFF (baseline)
   - B: same offline voice, Natural ON, EQ ON (only postprocessing)
   - C: chosen offline voice, Natural OFF, EQ OFF (near-neutral expression changes)
   - D: highest-quality other installed offline Telugu voice, Natural ON, EQ OFF
3. Listen through the phone speaker and headphones; compare audio length, pronunciation, metallic tone, sibilants, clarity, volume, bad boundaries. Do not let volume differences trick the listener: loudness-match for fair comparison.
4. Explicitly compare live speech playback vs exported WAV. Log engine package, voice name, `getQuality()`, `getLatency()`, Android build, sample rate/bit-depth/channels, peak and any clip samples.
5. For 10–20-minute YouTube clips verify stable speaker timbre across chapters, natural cues, pauses, clip joining, no loudness jumps, memory and phone heating.

## Next actual fix — replace source model, not just add more filters
- First deliver a reproducible **Piper Telugu / compatible ONNX** local synthesizer on Android ARM64, with tested Telugu phonemization, local model files, correct audio sample rate and licensing. This will be a neural TTS baseline (no zero-shot cloning, no emotion tags).
- For truly natural emotionally varied Telugu speech and a consenting user's own voice cloning, evaluate Android-ported Indic-Mio + MioCodec or another demonstrably Telugu-capable expressive checkpoint. Verify whole pipeline completely offline with phone 8 GB RAM. There is currently NO proven ready-to-install Telugu expressive cloned voice in this repository.
- Keep semantic script understanding (sentence emotions from full-topic context) separate from speech synthesis. A text emotion tag does not alter timbre unless the model supports conditioned expressive generation.
- Target natural spoken Telugu on 20 blind-rated test scripts (tutorial, product review, YouTube hook, surprise, caution, story, mixed Telugu-English, numeric values, 10–20 minute session). Never claim 90% Google-quality without actual blinded testing.

**Important**: The user is reporting a real, audible voice-quality bug. Do not answer “just use a denoiser” or “EQ makes it human”. Prove voice selection, WAV integrity and a native neural engine with actual generated sound.
