---
name: telugu-youtube-offline-narrator
description: Work on the user's Telugu Voice Studio Android app. Prioritize fully offline, human-sounding Telugu 10–20-minute YouTube narration, meaningful emotion direction, consented voice cloning, reproducible APK builds, and honest testing.
---

# Telugu YouTube Offline Narrator — project skill (v1, 2026-11-09)

## Source of truth and scope
Repository: `createnandu-create/Telugu-voice-studio`
Development branch: `telugu-voice-testing`
Application: `offline-tts-android/`
Device: Nothing Phone (1), Snapdragon 778G+, ARM64, Android 15, 8 GB physical RAM, 256 GB storage.
Read `AGENTS.md`, `docs/ASTRA6_HANDOFF.md`, `offline-tts-android/MODEL_SELECTION.md` and the current implementation BEFORE editing.
Do not change `main` or other user projects without explicit request; operate on test branch. Prefer small verified commits and a downloadable debug APK.

## User intent — not generic text to speech
Make an Android app that accepts the complete Telugu script for a 10–20 minute YouTube video, understands the topic and the purpose of each segment, and produces natural **Telugu narration** as a skilled YouTuber would deliver it. The user specifically wants meaningful variation: confident introductory hook, clear teaching tone for "ఇక్కడ క్లిక్ చేయండి", excited surprise for "వావ్!", appropriate seriousness for warnings, slower pacing for important details, sustained engagement without robotic/overacted delivery, logical pauses and emphasis. Keep every factual word from the script intact unless the user explicitly requests edits.

**Audio and meaning are separate tasks.** Build a Script Director that outputs verified sentence/paragraph plans, then an expressive neural speech renderer. A faster pitch/rate filter is not equivalent to genuine neural expressive speech. A phone system voice is a baseline fallback only.

## Absolute constraints
- Audio synthesis and script analysis must work with mobile data/Wi-Fi switched **off** after models are installed. No online API, inference server, shared free GPU or Google Cloud calls in offline mode.
- Optional one-time download of redistributable weights is acceptable, with model size, SHA-256, version and license recorded; thereafter no network.
- Nothing Phone (1): prioritize memory-efficient sequential execution, avoid loading the LLM and speech model together if RAM becomes high; no unverified speed/quality claims.
- Voice cloning only with the speaker's own sample or explicit consent. Do not embed third-party voices without permission or violate model/codec licenses.
- Voice consistency, correct Telugu phonemes, numbers, mixed Telugu/English, long-script continuity and readable WAV/optional MP3 outputs are essential.
- Output user-visible files and a real APK download. No false “Build successful”, “90% human”, “offline cloning works”, or fake download URLs.

## Required architecture
1. `ScriptInput`: accept Telugu text, paragraphs, categories (tutorial, product demo, storytelling, educational, news/serious), user style choice and optional user override. Do not hallucinate words.
2. `OfflineSemanticDirector`: full-script context summary and JSON segment plan with `text`, `emotion`, `emphasis`, `speed`, `pitch`, `energy`, `pause_ms`, `reason_te`. Tags: neutral, welcome, guide, curiosity, surprise, excited, empathy, serious, call-to-action. Allow manual correction. Fallback: deterministic `NarrationDirector.java`.
3. `OfflineNeuralTTS`: Telugu ONNX voice baseline (e.g., Piper) with correct phonemization on Android; benchmark offline in flight mode. Piper does NOT clone voice or natively generate emotional speech.
4. `OfflineExpressiveClone` (research / conditional feature): investigate Indic-Mio 0.6B + MioCodec end-to-end ARM64/CPU quantization and licensing. Do NOT call it implemented until a clone generated on the user's device is played and exported. Check other Android voice-cloning libraries only if checkpoints demonstrably support Telugu; API support != Telugu voice support.
5. `RenderQueue`: generate 1–3-sentence audio chunks, preserve narrator timbre and loudness, preserve original script order, stitch silence without gaps/clicks, save incrementally with resumability/cancel, export playable WAV to Android MediaStore. Report actual elapsed render time, memory and output duration.
6. `DownloadBuild`: GitHub Actions debug build on branch; Publish APK and source ZIP to GitHub Releases after successful build, link prominently from root README.

## Narration behaviors and examples
- "నమస్కారం ఫ్రెండ్స్!": natural friendly opening, not shouting.
- "ఇప్పుడు ఇక్కడ క్లిక్ చేయండి.": clearly and deliberately guide; soften speed, keep precise pronunciation.
- "వావ్! ఎంత అద్భుతం జరిగింది!": brief believable surprise, raised energy then return to baseline.
- "ఈ సెట్టింగ్ తప్పుగా మారితే జాగ్రత్త.": lower pace and more serious, no cheerful tone.
- Ask questions with genuine curiosity, state step-by-step facts plainly; CTA should be concise, not obnoxious.
- Paragraph-level emotional arc, not a random new mood on each sentence. Context rules beat punctuation alone. Allow override if director misunderstands intent.

## Quality target and rigorous acceptance
“90% like a real human/Google expressive TTS” is a **user goal, not a measured achievement**. Evaluate blind-listening Telugu samples (at least 20 scripts covering hooks, steps, stories, warnings, numbers, code mixing, long-form), naturalness, word correctness, emotion fit, speaker consistency and continuity. Compare to reference audio only if lawful and available. Log objective results and ask a human reviewer to score naturalness. Do not claim success based on one successful WAV.
- Build must pass Java tests + Android `:app:assembleDebug`.
- Install on Nothing Phone (1), airplane mode ON, close/reopen app, play + save WAV; no network traffic or cloud fallback.
- At least one 10–20 minute original Telugu script rendered, with stable memory/temperature and a playable final audio file.
- Voice-cloning label stays experimental until a consenting voice sample leads to intelligible Telugu audio fully offline on physical phone.

## Repository execution checklist
Read code; inspect workflows, GitHub permissions and existing tests. Keep the previous working behavior. Work in small commits on `telugu-voice-testing`; run the tests, build, collect real logs, repair failures, and attach APK + source ZIP. Present release/Actions links only when confirmed. Provide concise Telugu updates explaining exactly what is verified vs still experimental. Never suggest a demo webpage is equivalent to native, offline cloning.

## Voice quality regression blocker: reproduced from user's WAV (2026-11-09)
BEFORE claiming success, read `docs/AUDIO_QUALITY_DIAGNOSIS.md`. The user played two bit-for-bit identical exported WAVs (12.315 sec, PCM16 mono 24kHz). They sound hollow/metallic/robotic and lack authentic human expressions. WAV file format is valid, and ~0.008% near-clipped samples alone does not account for the whole timbre. Main suspect: underlying Android system Telugu voice, aggravated by pitch/speed modulation and selecting the first available offline voice. Do not blame compression or promise a simple EQ can fix a low-quality model.

The test branch now has a voice quality selector, default natural baseline pitch/rate 1.0, optional gentle modulation, and an experimental clarity EQ checkbox OFF by default. Perform same-script A/B: raw neutral; same neutral + EQ; mild modulation raw; different offline voice raw. Loudness-match and listen with headphones and phone speakers; compare live and exported WAV. Record real voice ID, Android engine ID, sample rate, sample peak/clipped samples, and user listening verdict. The optional PCM filter requires real APK compilation and device listening before claiming it works.

**Priority fix is genuine neural Telugu voice and phonemizer** (Piper ONNX as a baseline, not a clone). Expressive Telugu voice cloning with Indic-Mio/MioCodec remains experimental and requires actual ARM64 device inference and consented reference. Preserve the user's human-level 10–20 minute YouTube narration goal, do not substitute another simple system TTS demo.

## 2026-11-09 model-priority update: avoid endless audio filters
As of this research snapshot, prioritize *Indic Tiny TTS Telugu* ONNX (22.5M parameters; ~90 MB; male/female; three graphs including Vocos vocoder, CC-BY-4.0 acoustic model, MIT code) as the smallest credible alternative to current metallic Android system voice. Its small acoustic and vocoder graphs are publicly available, but a real Android inference pipeline is NOT yet implemented or verified. Also A/B compare Piper Telugu. Do not mistake measurable Telugu text intelligibility (CER) for human-quality voice.

Prepare script meaning/emotional plan separately; actual genuine Telugu emotions and consenting voice cloning need an expressive model such as Indic-Mio+MioCodec, which still lacks proven Android 8GB port. [Indic Parler-TTS] has text-described voice style but is heavy. Rumik OSS 1 demonstrates emotional Tamil/Telugu samples but its 3B noncommercial weights are not a responsible default for YouTube. Supertonic-3 lacks Telugu.
Audio exports should be WAV PCM 48 kHz/24 bit (when needed by a video editor), lossless FLAC for archives, MP3 320kbps only for sharing. Never tell users format changes fix robotic source timbre; compare audio samples with matched loudness. Current experimental AudioPolish is mild PCM EQ only, not true speech reconstruction. See `offline-tts-android/MODEL_SELECTION.md`.

## Current targeted execution handoff (2026-11-09)
Read and execute [`docs/ASTRA6_FIX_UI_PRONUNCIATION_MODELS.md`](../../../docs/ASTRA6_FIX_UI_PRONUNCIATION_MODELS.md) for the user's current screenshot-10 UI reference, one-minute recording failure, model-cache re-download bug, and `నమస్కారం` pronunciation loss. This is the current highest-priority fix plan; avoid re-auditing unrelated model experiments before completing P0–P3. User's screenshots and WAVs are attachments, not available through plain text alone.

## P0 actual waveform pipeline patch (2026-11-10)
Read `docs/evaluation/CRITICAL_AUDIO_FIX_2026_11_10.md` before changing DSP, phonemizer, Kotlin playback or vocoders. Fixed model is Piper VITS with its own neural waveform generator, **not Griffin-Lim**; first /n/ loss has NOT been proven to be AudioTrack startup. Test branch now includes experimental 150ms head/100ms tail PCM guard for the FINAL WAV, optional user-controlled 180Hz/6kHz WarmthEq (default OFF), and stripping only *leading* Unicode formatting marks. These cannot reconstruct an absent consonant or create realistic expressions. New test classes exist but real Android CI/device success must be confirmed before declaring a successful build.

Version 0.5 integration: see `docs/evaluation/dsp-v05/README.md`. Native-rate optional WarmthEq is retained as an extra comparison. The default Studio master is a separately verified 44.1 kHz stereo DSP output sourced through `PcmWave.mergeRaw()`, so neither padding nor shelves are doubled. Raw export remains selectable. These DSP measurements still do not establish corrected /n/, human timbre or voice cloning.

## v0.6 pronunciation repair
Read `docs/evaluation/pronunciation-v06/README.md`. Piper sherpa 1.12.26 omitted PAD after BOS; pinned ONNX input adapters now restore it without weight edits. Do not double-pad if replacing the frontend. Indic Tiny now has a real Java/ARM64 path and verified host audio, but phone/naturalness acceptance is outstanding. WAV/MP3 export and model verification have dedicated UI states. Preserve local model migration backups and cached original audio.
