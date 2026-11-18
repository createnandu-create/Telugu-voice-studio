# Telugu Voice Studio — Android Offline Telugu Narrator

**Android 15 · Nothing Phone (1) · 8 GB RAM · Snapdragon 778G+**

An actively developed offline Telugu YouTube voice narrator prototype. The testing branch now integrates **downloadable Telugu Piper neural voices with a Kotlin Compose / Material 3 English Android Studio and resumable local WAV rendering**. An experimental OpenVoice V2 tone-conversion path now requires a real local preview before selecting a personal narrator. Human-level expression, voice similarity and an on-device semantic LLM remain unverified or unavailable.

## v0.8.3 downloads

[Unified Android APK](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.3-e102ee25db83/TeluguVoiceStudio-v0.8.3.apk) · [Source ZIP](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.3-e102ee25db83/TeluguVoiceStudio-v0.8.3-source.zip) · [Five before/after audio comparisons](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.3-e102ee25db83/TeluguVoiceStudio-v0.8.3-audio.zip)

Install over v0.8.2. In Voices, open a saved recording labeled Male or Female and create its preview. The dialog displays the base narrator before conversion. A preview previously made with a different label needs to be recreated once from the saved recording.

The recorder displays elapsed captured PCM and microphone peak level. Saving preserves the full take and levels the playback copy. The original capture remains available. The regression fixture contains 49 seconds of quiet speech-like tone and 11 seconds of silence and saves as a 60-second WAV.

The studio master uses offline RNNoise estimates to attenuate noise while preserving the phase of the original synthesis. The ZIP pairs the same takes before and after this stage for Venkatesh, Padmavathi, Indic Tiny Male, Indic Tiny Female and a public-reference OpenVoice conversion. The renders ran on a host CPU. Telugu listening quality, personal voice similarity and physical handset capture need device evaluation.

## v0.8.0 workspace, clarity and personal voice previews

Fresh Studio sessions clear stale playback; switching an installed voice queues a new render. Both neural rates master to 44.1 kHz with 150 ms head padding. English/math inputs are verbalized in Telugu before token lookup. Padmavathi avoids pitch/time DSP by default. Projects and exports share descriptive timestamps; compact cards, pill controls and in-place recording cleanup preserve originals.

**Experimental local cloning:** Settings → Voice cloning installs the 41.1 MiB OpenVoice V2 pack. Voices → Create voice preview requires consent and 10+ seconds of clear reference speech. A real device-generated preview unlocks the cloned tile. It uses OpenVoice's matching **256-D** tone encoder, not an incompatible ECAPA fingerprint. Host Telugu conversion is verified; Nothing Phone quality, speed and speaker similarity still need testing. No zero-echo guarantee.

[New four-voice mixed-script WAVs](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/debug-latest/TeluguVoiceStudio-four-voices.zip) · [v0.8.0 audio evidence](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/debug-latest/TeluguVoiceStudio-v080-audio.zip) · [Implementation and measured limits](docs/evaluation/v080)

Your existing `0.7.0-…-fresh` installation should be updated with **TeluguVoiceStudio-fresh-install.apk**; the normal APK updates the separate standard package. Both now show **0.8.0** (fresh has a suffix) and distinct app labels. Do not uninstall either app to update it: their recordings and models are stored separately.

## Historical v57 recording restoration and speaker fingerprints

**Fixes the Android `SpeechMarkup` generation crash.** Voices → **Enhance** creates a noise-reduced copy using bundled RNNoise, with optional experimental WPE room-tail reduction. Originals remain intact. Settings → Offline models → Cloning offers a separate downloadable **ECAPA 192-D speaker encoder**; consented enrollment stays private on the phone. A fingerprint is **not** a working cloned narrator.

[Restoration A/B audio ZIP](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/debug-latest/TeluguVoiceStudio-restoration-AB.zip) · [Measurements, implementation and limitations](docs/evaluation/restoration-v57). Host synthetic fixtures measured 19.7 dB noise-region attenuation and 5.1 dB echo-tail reduction. Zero echo, arbitrary-environment recovery, genuine cloning and Nothing Phone performance are not verified.

## Version 0.7.1 articulation and compact controls

Four offline voices now share editable rule-based delivery controls, +13% gated question endings and 180 ms comma buffers. Indic Tiny adds an optional CV-duration guard; female softening is optional. Speech-only bracket/LaTeX cleanup keeps the original script intact. **These controls do not prove corrected pronunciation, neural emotions or cloning.** [Audit and 14 real host-generated comparison WAVs](docs/evaluation/delivery-v07).

Compact 64 dp voice cards, fixed Back/Continue controls, a waveform mini player and one-tap **WAV / MP3 → Downloads** replace the oversized save flow on Android 10+. Completed audio names its actual voice. New PCM references support hardware noise effects when available, high-pass filtering, silence trim and original-capture retention. Existing models and personal files remain intact.

## Version 0.6 pronunciation and saving update

**Piper start-token repair:** restore the missing blank after BOS without changing learned weights or your script. Existing models migrate locally, with a rollback copy; no re-download. Tap **Generate again** to avoid old audio. **Check నమస్కారం pronunciation** generates three word positions with your selected voice. [Reproducible evidence and listening limitations](docs/evaluation/pronunciation-v06).

**Indic Tiny Telugu** is now an experimental second offline engine: one 91 MB shared male/female download under **Settings → Offline models → Telugu Narration**. Java host audio is verified; physical-phone pronunciation and performance are not. No expressive clone is claimed.

**Save audio → Save WAV / Save MP3** offers offline 320 kbps MP3, progress/cancel, a named saved file and Open/Share. Model verification has visible progress and a timestamped result. Existing voices, recordings, projects and exports are preserved.

## Version 0.5 DSP update

New **Studio master / Raw voice** preview/export: local 150/100 ms padding, sinc 44.1 kHz stereo resampling, warmth EQ, measured −14 LUFS normalization and ≤ −1 dBTP limiting. Original WAVs remain available. [Independent measurements and limitations](docs/evaluation/dsp-v05). **Generate / Resume again** to master an earlier narration; models do not need another download. DSP does not establish correct pronunciation or voice cloning.

## Version 0.4 testing changes

Compact horizontal controls, animated navigation, persistent named reference recordings, pause/resume timer, verified model reuse and resumable downloads. Speech-only dictionary leaves the displayed script intact. See [implementation evidence and remaining blockers](docs/evaluation/COMPOSE_V04.md) and [16 raw pronunciation comparisons](docs/evaluation/pronunciation-v04). These changes do **not** establish corrected pronunciation, expressive cloning or Nothing Phone acceptance.

## Astra/AI instructions

- [**Project skill: fully offline Telugu YouTube narrator**](.agents/skills/telugu-youtube-narrator/SKILL.md)
- [**Single handoff prompt for GPT-6 Astra**](docs/ASTRA6_HANDOFF.md)
- [Agent instructions](AGENTS.md)
- [Model selection / real limitations](offline-tts-android/MODEL_SELECTION.md)

## Test instructions

Build command:

```sh
gradle --no-daemon --project-dir offline-tts-android :app:assembleDebug
```

Use the `telugu-voice-testing` branch. A successful CI run uploads an APK and source ZIP and publishes the same as downloadable GitHub Release assets. To test offline mode, install a voice from **Settings → Offline models** in the app, turn off Wi-Fi and mobile data, reopen Studio, generate, preview and export WAV. Physical-phone acceptance is still pending.

The requested roadmap is **real Telugu human-like neural expressiveness, topic-aware pacing and consented voice cloning for 10–20 minute YouTube narration**. Those AI engines remain research/integration tasks, not existing production features.

## Neural upgrade evidence

- [Implementation, semantic-model evaluation and Indic-Mio port limitations](docs/research/OFFLINE_NEURAL_UPGRADE.md)
- [Actual host WAV measurements and phone acceptance checklist](docs/evaluation/README.md)
- [Android source and build instructions](offline-tts-android/README.md)
- [GPL application license, voice attribution and corresponding native source](offline-tts-android/NOTICE.md)

Piper/Indic Tiny use fixed pretrained speakers. Optional rule-based DSP is separate from neural expressive synthesis and voice cloning. The APK is ARM64 with two isolated runtimes; voice weights download separately. No paid inference or cloud API is required. The app is still a test build, with no proven human-naturalness score.

**Installation troubleshooting:** If Android says “App not installed as package appears to be invalid”, try the **Fresh Install APK** after GitHub Actions publishes version 0.7. It installs alongside the older app with a separate `.fresh` ID; do not uninstall your old app just to test. Old voice recordings/models stay in the old app and are not automatically shared with the new install. This is a compatibility experiment; if both fail, preserve the downloaded APK and capture the exact Android installer error rather than clearing old app data.
