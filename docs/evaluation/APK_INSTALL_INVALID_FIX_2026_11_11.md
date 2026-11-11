# Android installer rejected APK — compatibility investigation (2026-11-11)

## User-reported Android error
Nothing Phone (1), Android 15, downloads view (screen capture): Telugu Voice Studio Installer says **"App not installed as package appears to be invalid."** Download shown as approximately **73.93 MB**, matching the official v0.6 APK asset of **73,931,596 bytes** from GitHub Release tag `testing-bc334a930323` / `debug-latest`.

Authoritative v0.6 release asset SHA-256:
`859c14b96811002c5b562859d57be11e10c91b5e250b9b556983c781212288c5`.
CI run https://github.com/createnandu-create/Telugu-voice-studio/actions/runs/42933549269 was successful including `apksigner verify`, `zipalign -c -P 16 4`, Android Gradle compile, unit tests and lint. **This does not prove installation on a real Nothing Phone**. The screenshot does not reveal the underlying `PackageManager INSTALL_FAILED_*` code; a package-signature conflict, corrupt phone copy, native-library extraction, or another device-specific cause has not been ruled in/out.

## Version 0.7 targeted compatibility experiment
- Keep the original package identity `in.aitechapp.teluguoffline` and the existing public test signing key, bump `versionCode` to 7.
- Use extracted JNI library layout (`packaging.jniLibs.useLegacyPackaging=true`) to avoid uncompressed native library layout sensitivities.
- Also create `app-fresh.apk` with independent package identity `in.aitechapp.teluguoffline.fresh`. This APK is for **side-by-side install** if an old signer/package prevents a normal update; the old app and old data stay untouched. The fresh app gets isolated storage and will not automatically import old recordings or models.
- The workflow builds both APKs, validates manifest package IDs with aapt2, verifies signatures with apksigner, tests ZIP integrity and 16KB zipalign, then publishes immutable and rolling release assets. Uploaded APKs are downloaded back and compared byte-by-byte against CI files.
- These changes are install-compatibility **hypotheses**, not yet validated on the user's phone. Release publication alone is not a physical install acceptance.

## Safe device test (no destructive steps)
1. Do **not** uninstall, clear storage or delete any old app; preserve recordings/models.
2. After the 0.7 Release appears, download `TeluguVoiceStudio-fresh-install.apk` from `debug-latest` once through Chrome/Files. Make sure download size matches the asset metadata, and launch installer from Files.
3. If fresh install succeeds, it should appear as a second Telugu Voice Studio app. Test launch, model download, voice, recording on that new app. The old app remains on the phone.
4. If fresh install fails with the same error, capture the exact Android installer status with an authorized Android device diagnostic (e.g. `adb install /path/to/TeluguVoiceStudio-fresh-install.apk` and note the `INSTALL_FAILED_...` code) **or upload the actual downloaded APK** for checksum, ZIP, manifest and signature inspection.
5. A **different app package** cannot reuse inaccessible old app-private recordings/models. Export old data first if moving to fresh app is ever requested.

## Do not misdiagnose
A valid 73.93 MB CI APK and a failed phone install can coexist. Without the exact rejected APK bytes and Android install failure code, the cause is uncertain. Never tell the user the new APK is proven fixed until the same phone installs and opens it.

## Release links (only valid once v0.7 GitHub Actions succeeds)
- Standard APK: https://github.com/createnandu-create/Telugu-voice-studio/releases/download/debug-latest/TeluguVoiceStudio-debug.apk
- Fresh APK: https://github.com/createnandu-create/Telugu-voice-studio/releases/download/debug-latest/TeluguVoiceStudio-fresh-install.apk
- Build workflow: https://github.com/createnandu-create/Telugu-voice-studio/actions/workflows/offline-tts-debug.yml
