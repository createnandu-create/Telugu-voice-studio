# Telugu Voice Studio — Offline Android Narrator

**Main downloads hub for Nothing Phone (1) / Android 15**

This repository is building an **on-device Telugu YouTube narrator** that understands whole 10–20-minute scripts, plans meaningful expressive delivery, and ultimately supports permission-based voice cloning. **Current code is an offline Android system-TTS narrator with heuristic emotion tags, not a neural voice clone.**

## Downloads

| Download | Link | Status |
|---|---|---|
| **Debug APK** | [TeluguVoiceStudio-debug.apk](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/debug-latest/TeluguVoiceStudio-debug.apk) | **Only works after a successful GitHub Actions build and Release upload** |
| **Fresh Install APK (keep old app)** | [TeluguVoiceStudio-fresh-install.apk](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/debug-latest/TeluguVoiceStudio-fresh-install.apk) | Separate application ID; published after 0.7 build; old app/data stays |
| **Source ZIP (no build needed)** | [Download entire testing-branch ZIP](https://github.com/createnandu-create/Telugu-voice-studio/archive/refs/heads/telugu-voice-testing.zip) | Repository source is available now |
| APK + Source ZIP as Release assets | [Open Downloads / Releases](https://github.com/createnandu-create/Telugu-voice-studio/releases) | Available once CI succeeds |
| Build logs and manual build | [Open GitHub Actions](https://github.com/createnandu-create/Telugu-voice-studio/actions/workflows/offline-tts-debug.yml) | Choose `telugu-voice-testing` and Run workflow |
| Android code | [Open offline-tts-android](https://github.com/createnandu-create/Telugu-voice-studio/tree/telugu-voice-testing/offline-tts-android) | Source |
| Project skill | [Offline YouTube narrator SKILL.md](https://github.com/createnandu-create/Telugu-voice-studio/blob/telugu-voice-testing/.agents/skills/telugu-youtube-narrator/SKILL.md) | Full product/agent instructions |
| GPT-6 Astra prompt | [Copy the Astra handoff prompt](https://github.com/createnandu-create/Telugu-voice-studio/blob/telugu-voice-testing/docs/ASTRA6_HANDOFF.md) | Full detailed handoff |

**If the APK link returns 404, a debug APK has NOT been published yet.** A source ZIP is not an APK. Builds use `gradle --no-daemon --project-dir offline-tts-android :app:assembleDebug` on the test branch and upload APK + source ZIP only on success.

### Build from GitHub (without Android Studio on the phone)
Open **Actions → Build and publish Telugu Voice Studio debug downloads → Run workflow → Branch: telugu-voice-testing → Run workflow**. When the workflow succeeds, open **Releases** to download the APK and the source ZIP.

Development branch: [telugu-voice-testing](https://github.com/createnandu-create/Telugu-voice-studio/tree/telugu-voice-testing). `main` is kept minimal; the Android code lives on the test branch.

### Quality target (not yet achieved)
Meaning-aware Telugu narration with genuine neural expressiveness, steady identity, emotionally appropriate hooks/tutorials/stories, consented speaker cloning, 10–20 minute WAV export, and no internet required after model installation. The currently committed implementation must be tested on the physical phone before calling it ready.

**Installation troubleshooting:** If Android says “App not installed as package appears to be invalid”, try the **Fresh Install APK** after GitHub Actions publishes version 0.7. It installs alongside the older app with a separate `.fresh` ID; do not uninstall your old app just to test. Old voice recordings/models stay in the old app and are not automatically shared with the new install. This is a compatibility experiment; if both fail, preserve the downloaded APK and capture the exact Android installer error rather than clearing old app data.
