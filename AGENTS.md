# Repository agent instructions

This repository is for the user's fully offline Telugu YouTube narration Android app. When handling any Android, TTS, voice clone, model selection, APK, download or UI task here:

1. **Read** `.agents/skills/telugu-youtube-narrator/SKILL.md` and `docs/ASTRA6_HANDOFF.md` first.
2. Work on `telugu-voice-testing` unless the user explicitly requests another branch.
3. Never equate ordinary Android system TTS / rate-pitch changes with human-level expressive neural voice cloning.
4. Never invent successful build status, working APK, direct release URL, 90% voice similarity, on-device model benchmark or downloaded weights.
5. Target Nothing Phone (1), Android 15, Snapdragon 778G+, 8GB RAM; fully offline post-install.
6. Use consenting reference voices only. Honor component licenses.
7. Run Java tests, build a real Android debug APK, publish it and the source ZIP to GitHub Releases after success.

7. For the current UI, recording, model re-download and Telugu pronunciation blockers, FIRST read `docs/ASTRA6_FIX_UI_PRONUNCIATION_MODELS.md` (2026-11-09) and execute P0–P5. Use the user's first/10th screenshots and uploaded WAVs only when they are actually available to you.
