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

## Extra independent spectrum check — user suspects missing Android filter (2026-11-09)

Analysed original WAV via Welch PSD over full 12.315s, including pauses. Fractions are relative to its total spectral power and do **not** by themselves prove a unique root cause (speech naturally has much low-frequency power):
- 85–250 Hz: ~36.4%
- **250–650 Hz: ~46.2%**
- 1–2.5 kHz: ~10.6%
- **2.5–5 kHz: ~1.2%**
- 5–8 kHz: ~0.1%

This skew **supports testing** a mild cut in 250–650 Hz boxy/muddy speech plus gentle vocal presence. It is not evidence that a mandatory Kotlin/plugin/library is missing. The original and user-resubmitted WAV are byte-identical. A previous mild clarity test WAV (local working artifact) changed the broad-band balance and increased peak headroom; any audible improvement requires direct user A/B listening.

**Do not insert a microphone noise suppressor into synthesized speech just because output is robotic.** RNNoise, WebRTC AudioProcessing and DeepFilterNet remove recorded background noise / microphone artifacts; a clean but synthetic or low-fidelity TTS voice may be harmed, and they do not create missing human prosody. DeepFilterNet's readily documented CLI expects 48 kHz WAV; the sample is 24 kHz and would need correctly bandlimited resampling. DeepFilterNet license is MIT/Apache 2.0, but Android packaging is a separate engineering task.

**Android media API distinction**: `android.media.audiofx.DynamicsProcessing` can attach EQ/compression/limiter to `AudioTrack` or `MediaPlayer` audio sessions for **playback**. It is not magically embedded into `TextToSpeech.synthesizeToFile()` output; file exports require explicit raw PCM processing, which the opt-in `AudioPolish` now provides. The real question is whether *live TTS*, *exported WAV*, and *same-file alternate-player playback* all show the metallic tone.

**Missing-library selection**:
- Postprocessing: existing `AudioPolish.java` is a native Java/Android-compatible no-network PCM16 DSP library already in this repository, no external Kotlin plugin needed. It processes export only and is OFF by default for diagnostics.
- Live preview audio EQ: Android DynamicsProcessing on an app-owned AudioTrack/MediaPlayer session; if using Android TTS `speak()`, that session may not be owned by this app. Instead preview exported WAV through a player with a configurable audio session.
- Neural denoiser: optionally trial DeepFilterNet after validated 48 kHz resampling on **noisy recorded microphone** input; not default on synthetic output.
- Core timbre upgrade: select a higher-quality installed offline Telugu voice and A/B neutral pitch/rate; if still bad, replace the acoustic TTS model, not just filters. Genuine on-device Telugu neural model inference (ONNX/sherpa-onnx with correct Telugu phonemization) is the priority.

### Exact next check
On Nothing Phone (1) run **one sentence** through:
A. native live Android TTS speaker at pitch/rate 1.0;
B. raw exported WAV at pitch/rate 1.0;
C. the same WAV with optional clarity EQ;
D. another installed offline Telugu TTS voice if available.
Compare B on headphones, phone speaker and an independent music player. Loudness-match A/B/C before judging. If live A sounds clean but WAV B has metal rattles, isolate export, sample conversion and stitching. If A and B are equally robotic, replace voice model. If B is clean on headphones but not speaker, investigate device playback processing. Document the voice **engine package and voice name**. Record no confidential audio in public repositories.

References:
- https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing
- https://developer.android.com/reference/android/speech/tts/TextToSpeech
- https://github.com/Rikorose/DeepFilterNet
