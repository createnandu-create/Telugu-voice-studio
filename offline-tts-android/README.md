# Telugu Expressive Narrator — Android 15 trial overlay

Created as a drop-in **test-branch** overlay for the existing \`createnandu-create/Aiide\` repository's \`offline-tts-android/\` folder, \`telugu-voice-testing\` branch.

## What is implemented

- Offline **heuristic** script director class (\`NarrationDirector.java\`) choosing guidance, excitement, surprise, warning, etc. per sentence.
- Narration-category selection (\`AUTO\`, \`TUTORIAL\`, \`STORY\`, \`NEWS\`, \`REVIEW\`) and on-screen segment plan.
- Sequential local playback (not enqueue-all) to apply rate and pitch separately to each segment, with pauses.
- Native Android TTS only, requiring **preinstalled Telugu OFFLINE voice** (no cloud fallback, no API key, no Internet permission).
- WAV export to \`Music/TeluguVoiceStudio\` with PCM segments and appropriate inter-sentence pauses.
- Java-only tests; 11 checks passed with \`javac\` on the script director and the semantic model prompt contract.
- \`SemanticNarrationPrompt.java\` is an integration **contract for a future local LLM**; does not claim installed AI inference.

## Not implemented / not verified

- **NO neural voice-cloning model** or Indic-Mio weights / codec are bundled; this is not 90%-Google-quality offline expressive Telugu.
- Heuristics do not truly understand the full topic, and Android TTS pitch/rate modulation does NOT equal a neural expressive TTS model.
- Android APK build and Nothing Phone (1) hardware playback were **not executed** in this environment (Android SDK/Gradle unavailable).
- GPL or non-commercial library code has not been copied.

## How to merge

Overlay this project's \`offline-tts-android/\` onto the SAME folder on the \`telugu-voice-testing\` branch, not \`main\`. You can replace the old standalone Android test module. Preserve the existing \`voice-testing/\` website, \`api/\`, and git workflows without changes. The existing GitHub Action \`.github/workflows/offline-tts-debug.yml\` targets this module. Run workflow and install debug APK after the build succeeds.

## Model/Library rationale

See \`MODEL_SELECTION.md\` for the actual 2026 model choices, LiteRT-LM / Qwen3.5 offline semantic analysis candidate, Indic-Mio port caveats and acceptance tests.

## Better neural voice follow-up

1. Keep \`NarrationDirector.Segment\` as the common interface for an optional local LLM Script Director (LiteRT-LM or llama.cpp). An LLM must output structured, user-reviewable emotion, emphasis and pause tags.
2. For a proven neural offline Android TTS, integrate sherpa-onnx and a phonemization-compatible **Telugu** voice (e.g. Piper Telugu, after conversion/testing). This voice is not cloned and does not inherently support emotion tags.
3. For expressive cloning, benchmark Indic-Mio plus MioCodec on ARM64 **before** wiring it into app. Its existing reference implementation is Python/GPU, not an available Android .aar. If the port fails, show the feature as unavailable, not as falsely working.
4. ZipVoice/Pocket voice cloning can be driven by sherpa-onnx on Android, but the widely published checkpoints don't establish fluent Telugu; never assume language support.
5. Obtain permission for voice samples and comply with model/voice licenses, including attribution where required.

## Build command on an Android CI machine

\`gradle --no-daemon --project-dir offline-tts-android :app:assembleDebug\`

## Run pure Java tests

\`javac -encoding UTF-8 -d /tmp/narration-tests offline-tts-android/app/src/main/java/in/aitechapp/teluguoffline/NarrationDirector.java offline-tts-android/tests/NarrationDirectorTest.java\`

\`java -cp /tmp/narration-tests in.aitechapp.teluguoffline.NarrationDirectorTest\`
