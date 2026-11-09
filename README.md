# Telugu Voice Studio — Android Offline Telugu Narrator

**Android 15 · Nothing Phone (1) · 8 GB RAM · Snapdragon 778G+**

An actively developed offline Telugu YouTube voice narrator prototype. The testing branch now integrates **downloadable Telugu Piper neural voices with a native English Android Studio and resumable local WAV rendering**. It does **not** yet include human-like neural expressive voice cloning or an on-device LLM.

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

Use the `telugu-voice-testing` branch. A successful CI run uploads an APK and source ZIP and publishes the same as downloadable GitHub Release assets. To test offline mode, install a voice from **Models** in the app, turn off Wi-Fi and mobile data, reopen Studio, generate, preview and export WAV. Physical-phone acceptance is still pending.

The requested roadmap is **real Telugu human-like neural expressiveness, topic-aware pacing and consented voice cloning for 10–20 minute YouTube narration**. Those AI engines remain research/integration tasks, not existing production features.

## Neural upgrade evidence

- [Implementation, semantic-model evaluation and Indic-Mio port limitations](docs/research/OFFLINE_NEURAL_UPGRADE.md)
- [Actual host WAV measurements and phone acceptance checklist](docs/evaluation/README.md)
- [Android source and build instructions](offline-tts-android/README.md)
- [GPL application license, voice attribution and corresponding native source](offline-tts-android/NOTICE.md)

Piper applies speed and pauses; it does not synthesize the displayed emotion notes or clone voices. The APK is ARM64, approximately 30 MB; each model downloads separately. No paid inference or cloud API is required. The app is still a test build, with no proven human-naturalness score.
