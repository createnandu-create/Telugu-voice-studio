# Telugu Voice Studio — Android Offline Telugu Narrator

**Android 15 · Nothing Phone (1) · 8 GB RAM · Snapdragon 778G+**

An actively developed offline Telugu YouTube voice narrator prototype. The testing branch now integrates **downloadable Telugu Piper neural voices with a Kotlin Compose / Material 3 English Android Studio and resumable local WAV rendering**. It does **not** yet include human-like neural expressive voice cloning or an on-device LLM.

## Downloads

| File | Direct link | Availability |
|---|---|---|
| Android Debug APK | [TeluguVoiceStudio-debug.apk](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/debug-latest/TeluguVoiceStudio-debug.apk) | Created by GitHub Actions **only after a successful build** |
| Android Source ZIP + project skill | [TeluguVoiceStudio-source.zip](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/debug-latest/TeluguVoiceStudio-source.zip) | Created by successful build |
| Branch Source ZIP (works without build) | [Download current source ZIP](https://github.com/createnandu-create/Telugu-voice-studio/archive/refs/heads/telugu-voice-testing.zip) | Always reflects the branch |
| Source in browser | [offline-tts-android/](https://github.com/createnandu-create/Telugu-voice-studio/tree/telugu-voice-testing/offline-tts-android) | Browse files |
| GitHub Actions build logs | [Open builds](https://github.com/createnandu-create/Telugu-voice-studio/actions/workflows/offline-tts-debug.yml) | Build status |
| All Releases | [Releases page](https://github.com/createnandu-create/Telugu-voice-studio/releases) | APK appears here after CI succeeds |

**If the APK link returns 404, the release has not been published yet; check Actions. Do not treat a source ZIP as an installable APK.** The debug APK is for testing, not Google Play Store distribution.

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

Piper applies speed and pauses; it does not synthesize the displayed emotion notes or clone voices. The APK is ARM64, approximately 30 MB; each model downloads separately. No paid inference or cloud API is required. The app is still a test build, with no proven human-naturalness score.
