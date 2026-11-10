# Version 0.4 implementation and evidence

Target: Nothing Phone (1), Android 15, ARM64, 8 GB. Testing branch only. This is an incremental UI/lifecycle release, **not completion of expressive Telugu cloning**.

## Visible changes

The old programmatic `StudioActivity.java` is replaced by Kotlin Compose + Material 3. Studio, Voices, Projects and Settings use compact English controls, a moving/flip active navigation indicator, haptics and reduced-motion handling. Models are under Settings. Generate / Play / Export are a horizontal group; sentence text and editing controls share compact rows. Controls keep 48 dp touch targets and scroll on narrow screens. Manrope and DM Serif Display use bundled OFL fonts. Material outlined icons are a small Apache-2.0 source subset instead of the large extended-icons dependency.

The November 10 ZIP contains **18 screenshots of earlier versions**, including the System TTS fallback. They show the reported oversized controls and broken recording flow; there is no distinct new design mockup in that ZIP. The implementation follows the requested compact native hierarchy and white/yellow/vermilion palette; no claim is made of matching an unseen reference or of having created a Figma file. [Six actual Compose screen captures](ui-v04) were generated with Robolectric native graphics (API 34, 393×873 dp). These are host-rendered Android views, not physical-phone screenshots.

## Model reuse and recording

- Existing `files/neural-v1` model paths remain unchanged. A one-time local integrity check migrates old installs to receipts with revision, SHA-256, byte count, path, mtime and verified date. Normal readiness checks use local metadata. Verified Install returns before opening a network connection. Explicit Verify checks every byte; metadata-preserving corruption requires this explicit check.
- Downloads retain `.download` bytes and identity/ETag sidecars, request HTTP Range, validate resume responses and final SHA, and install atomically after verification. HTTP 200 instead of 206 restarts that asset safely. Failed/rejected downloads leave installed weights and other personal data untouched. Check Updates is explicit metadata-only; unreviewed upstream revisions are never installed automatically.
- A microphone foreground service owns MediaRecorder independently of Activity navigation. An active-time clock, 60-second limit, pause/resume/stop, notification Stop action and interrupted-file recovery replace `onPause()` termination. Named profiles use unique AAC files and metadata; select, rename, playback, trim-to-copy, export and explicit delete are exposed. Original legacy recordings are retained during migration. A saved reference **is not a trained voice**, and never replaces the chosen Piper speaker.
- Physical microphone behavior, interruptions from real phone calls and airplane-mode rendering on Nothing Phone remain untested. JVM clock/persistence checks do not establish those hardware results.

## Pronunciation investigation: unresolved listening gate

The three uploaded user WAVs were inspected locally. They are mono PCM16 / 22,050 Hz, with no clipped samples. The newest `Telugu-narration-1794272231611.wav` is 7.394875 seconds, peak 0.690704. No private reference audio is committed or uploaded; metrics and hashes only are included.

Exact NFC text and eSpeak-ng output were recorded through the same sherpa shared library. `నమస్కారం` produced **`nˈamaskˌaːran`**: the initial `n` is present at this phonemizer stage. `phoneme-audit.json` maps each returned codepoint to the original Piper vocabulary. It does **not** pretend to be the final internal BOS/EOS/punctuation tensor dump. This evidence rules out a simplistic claim that normalization deleted the initial letter; it does not establish the exact cause of the audible complaint or correct Telugu articulation. No EQ, leading silence or spelling hack is presented as a fix.

Sixteen real, unprocessed Linux host WAVs compare the word alone and three short scripts, male/female independently:

1. `నమస్కారం`
2. `నమస్కారం ఫ్రెండ్స్!`
3. `ఇప్పుడు ఇక్కడ క్లిక్ చేయండి.`
4. `ఇది చాలా ముఖ్యమైన విషయం. జాగ్రత్తగా వినండి.`

Piper Venkatesh / Padmavathi use the existing pinned models and sherpa-onnx 1.12.26. Indic Tiny male / female use [sulabhkatiyar/indic-tiny-tts-telugu](https://huggingface.co/sulabhkatiyar/indic-tiny-tts-telugu/tree/55cfc683fafb55326a0a01932a3b2bb4374c4fc0), revision `55cfc683fafb55326a0a01932a3b2bb4374c4fc0`, ONNX Runtime 1.31, seed 0, two host threads. Its three graphs total 91,011,543 bytes (text encoder 17,692,254; flow decoder 19,169,253; vocoder 54,150,036). The character tokenizer offers a different frontend from Piper/eSpeak. CC BY 4.0 acoustic weights / AI4Bharat Rasa attribution and MIT vocoder/code notices accompany the samples.

Files, hashes, duration and host render time are in [pronunciation-v04](pronunciation-v04). Four greeting samples are playable in Settings → Offline models → Compare, explicitly labeled **fixed-script host audio**. Indic Tiny is **not** an installed Android generation engine. Listening preference and naturalness are not scored by these objective metrics; no human listening verdict or 90% benchmark is claimed. The user still reports poor pronunciation.

The editable speech-only lexicon preserves the original script. NFC, exact word boundaries, longest phrases and explicit opt-in digit reading are tested. Original/spoken inputs are saved per render, and spoken text enters the cache identity so edited pronunciation cannot silently reuse old speech. User-authored mappings can improve individual terms but are not a model-quality fix.

Reproduction: `offline-tts-android/scripts/pronunciation-ab.py` requires local models and Python dependencies as documented in its header. It performs no hosted inference or uploads. Unchanged source models can be obtained from the pinned links; existing conversion metadata/token tables are in the Android assets. Unpack `phonemes.zip` for `ESPEAK_PARENT/espeak-ng-data`.

## Still not delivered

Correct pronunciation and engaging naturalness need Telugu listening review and a verified better phone model. No semantic LLM, expressive conditioning, voice-clone inference, Android Indic Tiny port, physical-device RAM/temperature measurements or 10–20-minute phone acceptance is claimed. Indic-Mio + MioCodec remains research. Changing rate/pause is not true emotion synthesis. Saved voice references must never be labeled cloned.

## Verification

Java tests cover exact Telugu strings, dictionary boundaries, active recording time/interruption, model receipts, Range resume/no-range fallback, changed ETag, unavailable server and checksum/cancellation behavior. Robolectric covers native UI navigation/consent and persistent model/profile state. APK build, lint, signature and 16 KB ZIP alignment are release gates. The immutable GitHub Release notes link the exact commit and passing Actions run; do not infer a new release from a prior rolling APK.

Local verification (2026-11-10): exact `:app:assembleDebug` succeeded; `:app:testDebugUnitTest :app:lintDebug` succeeded with 6/6 Robolectric tests, 0 lint errors / 12 advisory warnings. Java suites passed 11 director + 17 pipeline + 24 lifecycle checks and AudioPolish cases. Bundle verification covered 23 assets and 355 phoneme files. APK v2 signature and 16 KB ZIP alignment passed. The recording service test uses a shadow microphone and explicitly does not certify hardware audio.
