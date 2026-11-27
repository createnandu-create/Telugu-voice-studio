# Telugu Voice Studio — Android Offline Telugu Narrator

**Android 15 · Nothing Phone (1) · 8 GB RAM · Snapdragon 778G+**

An actively developed offline Telugu YouTube voice narrator prototype. The testing branch now integrates **downloadable Telugu Piper neural voices with a Kotlin Compose / Material 3 English Android Studio and resumable local WAV rendering**. An experimental OpenVoice V2 tone-conversion path now requires a real local preview before selecting a personal narrator. Human-level expression, voice similarity and an on-device semantic LLM remain unverified or unavailable.

## v0.8.12 downloads

[Android APK](https://github.com/createnandu-create/Telugu-voice-studio/releases/latest/download/TeluguVoiceStudio-v0.8.12.apk) · [Source ZIP](https://github.com/createnandu-create/Telugu-voice-studio/releases/latest/download/TeluguVoiceStudio-v0.8.12-source.zip) · [English notation listening comparison](https://github.com/createnandu-create/Telugu-voice-studio/releases/latest/download/English-Notation-Comparison.zip)

Direction → **Read technical notation in English** reads ratios, numeric roots, equalities, percentages, dimensional labels and video resolution aloud in English within a Telugu sentence. The option defaults to on for new renders. Commas and full stops add no fixed silence in this mode. Models can still pause naturally. Turn it off to use the previous Telugu explanation or Telugu literal settings. Saved scripts and previously queued jobs retain their content.

## v0.8.11 downloads

[APK](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.11-c421132c93be/TeluguVoiceStudio-v0.8.11.apk) · [Source ZIP](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.11-c421132c93be/TeluguVoiceStudio-v0.8.11-source.zip)

Use Reset & back during a render to submit another narrator. Each request saves its script and voice settings. One foreground worker processes requests in order to bound memory use. With notifications enabled, progress and completion are visible outside the app. Android force-stop and foreground service time limits can interrupt rendering.

## v0.8.10 downloads

[Unified APK](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.10-6d6308db05bc/TeluguVoiceStudio-v0.8.10.apk) · [Source ZIP](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.10-6d6308db05bc/TeluguVoiceStudio-v0.8.10-source.zip) · [Checksums](https://github.com/createnandu-create/Telugu-voice-studio/releases/latest/download/SHA256SUMS.txt)

Narration now preserves the native model tone by default. **Narration cleanup + tone EQ** is an explicit opt-in in Direction; it also controls Indic Tiny's mel contrast adjustment. Female EQ stays off when switching voices. Recording enhancement remains available separately. Final loudness normalization and export remain available; disabling cleanup does not claim to repair a model's pronunciation or make it human-like.

New technical readings cover signed temperatures, squared/cubed dimensions, speed, microseconds and resistance. `V25` stays a letter-number label, distinct from `√25`. Written scripts and saved projects are preserved. See the [audio audit and limitations](docs/evaluation/v0810-audio-audit.md).

## v0.8.9 downloads

[Unified APK](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.9-a9f887634f17/TeluguVoiceStudio-v0.8.9.apk) · [Source ZIP](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.9-a9f887634f17/TeluguVoiceStudio-v0.8.9-source.zip) · [Three technical narration samples](https://github.com/createnandu-create/Telugu-voice-studio/releases/latest/download/Technical-Telugu-Comparison.zip)

In Direction, **Explain English technical terms and maths in Telugu** previews the spoken form of supported expressions. The offline rules cover ratios, numeric square roots, measurements, common English technical phrases and simple arithmetic questions. Turn the switch off for the earlier reading. Studio retains the written script and stores the switch with each queued job.

## v0.8.8 downloads

[Unified APK](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.8-7c9b52bdae97/TeluguVoiceStudio-v0.8.8.apk) · [Source ZIP](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.8-7c9b52bdae97/TeluguVoiceStudio-v0.8.8-source.zip) · [Narrator comparisons](https://github.com/createnandu-create/Telugu-voice-studio/releases/latest/download/YouTube-Voice-Comparison.zip)

Hear the full editor script with [Karthik](https://github.com/createnandu-create/Telugu-voice-studio/releases/latest/download/karthik-full-demo.mp3), [Srinivas](https://github.com/createnandu-create/Telugu-voice-studio/releases/latest/download/srinivas-full-demo.mp3) or [Bhavana](https://github.com/createnandu-create/Telugu-voice-studio/releases/latest/download/bhavana-full-demo.mp3).

For pronunciation checking, install the optional Telugu recognizer in **Settings → Offline models**. Once downloaded, the switch enables local checking for new renders. It compares an eligible phrase with what the engine generated and can choose a clearer native take. The checks can increase generation time. For a personal Hima voice, install Hima cloning in Settings and select a recording in Voices.

## v0.8.7 downloads

[Unified APK](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.7-34570eefc9a9/TeluguVoiceStudio-v0.8.7.apk) · [Source ZIP](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.7-34570eefc9a9/TeluguVoiceStudio-v0.8.7-source.zip) · [Eight alternative narrator comparisons](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.7-34570eefc9a9/YouTube-Voice-Comparison.zip)

The editor opens the AiTechApp pronunciation script on a new install or when upgrading the old default. It retains custom drafts. After generation, **Reset & back** returns to that script and releases the active take. Completed audio stays in Projects.

Listen to the complete script with [Srinivas MP3](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.7-34570eefc9a9/srinivas-full-demo.mp3) or [Bhavana MP3](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.7-34570eefc9a9/bhavana-full-demo.mp3). Lakshmi, Sravani, Karthik and Pranay use the already-installed Hima pack. Both Indic-TTS voices and the Hima cloning encoder now read the exact graph assets verified inside the signed APK.

On the three supplied mono 44.1 kHz phone exports, integrated loudness measured −17.0 LUFS and true peak measured −1.1 dBTP. The subscribe clause occurs near 1:24 in the original Srinivas recording and 1:21 in Bhavana. In the new full-script CPU renders, independent Telugu recognition recovered సబ్స్క్రైబ్ and గగనాన for both. Recognition of ఎక్స్‌ప్లనేషన్ still varied. Speaker naturalness on the phone requires listening.

## Previous v0.8.6 downloads

[Unified Android APK](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.6-f910a7050d62/TeluguVoiceStudio-v0.8.6.apk) · [Source ZIP](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.6-f910a7050d62/TeluguVoiceStudio-v0.8.6-source.zip) · [Four new voices reading the same tech script](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.6-f910a7050d62/YouTube-Voice-Comparison.zip) · [All listening samples](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.6-f910a7050d62/TeluguVoiceStudio-v0.8.6-audio.zip)

Download a speaker pack once in Settings → Offline models. Hima Nano includes Srinivas and Bhavana. Indic-TTS includes male and female FastPitch voices with HiFi-GAN. For a personal voice, open Settings → Voice cloning → Download Hima cloning, then use Create my Hima voice with a recording you have permission to use.

The comparison ZIP contains the final MP3s, source script, loudness measurements and independent Telugu speech-recognition results. On this mixed Telugu and English script the recognizer made errors on names and abbreviations. Physical Nothing Phone (1) playback, audible ringing and similarity to the recorded speaker still need a listening check.

## Previous v0.8.5 downloads

[Unified APK](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.5-8d151ede31a2/TeluguVoiceStudio-v0.8.5.apk) · [Source ZIP](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.5-8d151ede31a2/TeluguVoiceStudio-v0.8.5-source.zip) · [Matched audio comparisons](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.5-8d151ede31a2/TeluguVoiceStudio-v0.8.5-audio.zip)

[Indic Tiny Female](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.5-8d151ede31a2/Indic_Tiny_Female_Clean.mp3) · [Indic Tiny Male](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.5-8d151ede31a2/Indic_Tiny_Male_Clean.mp3) · [Venkatesh](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.5-8d151ede31a2/Venkatesh_Clean.mp3) · [Padmavathi](https://github.com/createnandu-create/Telugu-voice-studio/releases/download/v0.8.5-8d151ede31a2/Padmavathi_Clean.mp3)

Changing narrator loads that speaker’s recommended controls. Manual adjustments apply to the current selection. The native warmth switch produces a separate comparison file. The default master uses conversational model timing and the original pitch contour.

The Indic Tiny acoustic stage uses separate male and female sampling temperatures and local mel-frequency contrast calibration before Vocos. Coefficients were fitted with 36 paired recordings per speaker from AI4Bharat Rasa (CC BY 4.0, prepared dataset `sulabhkatiyar/indic-tts-mini-24k`, revision `8fe16c93456540d567f4ae952adb18ab173912b3`). On 12 held-out pairs per speaker, the mean absolute error of local log-mel crest and valley contrast fell from 0.133 to 0.078 for female and from 0.139 to 0.069 for male. Measurements used 100 HTK mel bands at 24 kHz, a 1024-sample Hann window, a 256-sample hop, local smoothing of three mel channels, and 12th/88th percentiles in ten frequency groups. These scores measure an acoustic feature. Naturalness and pronunciation still need Telugu listener ratings on a physical handset.

The listening ZIP includes four base narrators and a public-reference OpenVoice conversion. Its additional Tiny Native/Calibrated WAV pairs use identical acoustic input and timing so listeners can isolate the pre-vocoder change.

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
