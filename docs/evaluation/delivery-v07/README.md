# v0.7 — four-voice duration, delivery and compact Android controls

This is an experimental offline test build, not a certified pronunciation repair or voice clone. The user identified `1794361867477` as **Padmavathi**. Their 82/68/72/50% readiness estimates are listening feedback, not measured naturalness scores. Personal recordings are not included in these public artifacts.

## Findings that constrain the fix

- The installed Piper male/female token tables are identical. Actual sherpa-onnx 1.12.26/eSpeak output keeps **శ → ɕ (55), ష → ʂ (95), స → s (31), ళ → ɭ (77)** distinct. `ఋ` is `rˈu`; `సఫలీకృతమైందో` contains `kru`; `కర్కశ` retains `r` and the intervening vowel; `శ్రమించే అభ్యాసకుడికి` has **no inserted r**. All **355 eSpeak data files** used by the host audit match the APK's phonemes.zip byte for byte. See [phoneme-audit.json](phoneme-audit.json). These results do not prove the acoustic models render those distinctions clearly.
- Indic Tiny has Telugu **character tokens**, not an IPA table. Neither engine's pretrained embeddings were assigned artificial IDs. No unvalidated schwa/IPA substitutions or hidden ZWNJ were injected. The existing explicit pronunciation dictionary remains speech-only and editable.
- Supplied exports include two 44.1 kHz stereo and two 24 kHz mono files. Format alone does not identify speaker gender. Only the user's explicit Padmavathi mapping is treated as confirmed. [Metadata](latest-upload-metadata.json) and [earlier signal measurements](user-metrics.json) are measurements, not listening/transcription judgments. In the Padmavathi upload, whole-file band energy did not establish an absence of bass; EQ is consequently optional.
- The screenshot exposed a separate identity problem: changing the selected voice could leave an earlier voice's completed audio visible. Completed audio now displays its **recorded voice/engine identity**, and export names use that identity. An asynchronous library refresh also preserves a manually selected audio version.

## Implemented controls

**Indic Tiny articulation:** after the learned duration predictor, words with more than four Telugu orthographic syllables receive a 1.10 duration multiplier. Simple medial మ/వ/గ CV spans get a minimum **7 mel frames = 74.67 ms**, the first whole-frame duration at or above the requested 65 ms (24 kHz, hop 256). This is an orthographic-span guard, not forced phoneme alignment. IDs, vowels, conjuncts, vocoder and speaker embeddings remain unchanged. The guard can be switched off for A/B. Identical-seed benchmark output increased from **24.81 to 25.86 seconds for female** and **21.52 to 22.53 seconds for male**, including the same pauses/padding. These numbers establish duration changes, not corrected words.

**Rule-based director:** welcome (+12% voiced pitch, 1.08 pace, +3 dB shelf at 140 Hz); instruction (1.02 pace, 110 ms buffer before matched action phrases, +2.5 dB phrase accent); wow/excitement (+20% voiced pitch, +2 dB presence at 3.5 kHz); empathy (−8% voiced pitch, .92 pace, softened highs, 320 ms pause); questions (final stable voiced region rises toward **+13%**, 220 ms pause). The question target follows the user's latest clarification, replacing the earlier +16% specification. Brand holds (1.15) and wow holds (1.30) use phrase-rate changes: they are **not vowel-only aligned duration edits**. Bare “కానీ” is not treated as proof of sadness. Emotion, pitch, pace and pause are editable. Neutral delivery disables pitch direction; Auto direction restores rules.

**Voiced-region DSP:** TD-PSOLA preserves PCM length and skips unvoiced/unstable regions. It does not infer exact final-two-syllable alignment. In the attached YouTuber fixture, **4 of 8 question tails passed the gate**; other tails retain the source model's prosody. Short wow segments can also be skipped. [Per-segment results](youtube-dsp.log) disclose this. A pitch control is not a neural emotion model or semantic understanding of a 20-minute script.

**Pauses and tone:** nonnumeric comma boundaries receive 180 ms of explicit silence; engine-generated pauses remain, so total perceived silence can exceed 180 ms. Optional female EQ uses +2 dB at 250 Hz and −2 dB at 4 kHz. Complex-clause careful pace slows by 8%. Native directed output is separate from the existing −14 LUFS / −1 dBTP Piper Studio master; it does not stack that mastering EQ or claim those loudness targets. Raw chunks and original audio are kept, with one final 150/100 ms head/tail guard.

**Text:** editor/project text stays intact. A speech-only cleaner removes parentheses, braces, square brackets, math delimiters and raw LaTeX backslashes, unwraps common text commands, and supports common spoken math forms. Meaningful unsupported content is not silently discarded; it is validated before neural inference and can be given an explicit spoken form in the dictionary. This is not a general LaTeX evaluator.

**Android UI/export:** 64 dp rose/blue voice cards; real selected-engine preview; fixed 48 dp Back/Continue row with 40/60 weights; actual PCM waveform; inline Play/WAV/MP3 buttons. Android 10+ exports through MediaStore into `Downloads/Telugu Voice Studio`, with pending-file publication, progress, cancel, retry and an in-row downloaded check. Android 8/9 retain the system folder picker. MP3 encoding remains local. Models remain under Settings and verified weights are reused.

**Recording:** foreground AudioRecord PCM capture, up to 60 active seconds, navigation-independent pause/resume, hardware NoiseSuppressor/AEC only when available and successfully enabled. An 80 Hz Butterworth high-pass and −40 dBFS / 10 ms RMS endpoint trim keep 50 ms speech guards. All-silent captures are retained. Exact trimmed duration comes from PCM frames. Original capture, existing AAC profiles and trim originals remain available; repeated trim names no longer accumulate suffixes. Device effect availability is shown honestly. Recording a reference still does **not** create a functioning clone: these checkpoints do not accept reference embeddings.

## Listening files

[samples/](samples/) contains 14 WAVs produced locally from public pretrained voices, no personal recordings:

- Indic Tiny male/female **baseline vs duration-guard**: same benchmark script and random seed.
- Padmavathi/Venkatesh **benchmark**: repaired v0.6 Piper input contract retained.
- All four voices **YouTuber before-DSP vs directed**: identical source chunks, pacing and boundaries; only directed DSP differs. See [youtuber-plan.tsv](youtuber-plan.tsv).

All are native-rate mono PCM16. [audio-metrics.json](audio-metrics.json) records durations, peaks, zero clipped samples and SHA-256. The pairs are not loudness-matched; compare at comfortable matched listening levels and judge pronunciation separately from loudness. Do not infer a quality percentage from file validity or plots.

## Reproduction and acceptance

Run `bash offline-tts-android/scripts/test-java.sh`, then `gradle --no-daemon --project-dir offline-tts-android :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. Tests cover immutable source text, actual pitch measurement with independent zero crossings, noise/voiced gating, exact comma buffers, duration spans, EQ/HPF response, recording trim and permissions, WAV/MP3, atomic MediaStore cancellation, identity, persistent settings and narrow Compose controls. UI images are Robolectric renders, **not physical-phone screenshots**.

Host fixture tools are in `offline-tts-android/tools/`; `DeliveryAudit` uses the actual Java Tiny/DSP implementations and `piper-delivery-audit.py` uses pinned sherpa with the repaired Piper models. Host timing logs are not Snapdragon benchmarks. Check the release's Actions run for the exact source commit and build result.

**Outstanding:** Nothing Phone (1) microphone/effect availability, APK install/playback in airplane mode, 10–20-minute heat/RAM/render tests, native-speaker A/B judgments for every audited word, semantic LLM inference, and genuine expressive cloning. The requested pronunciation and human-naturalness goals remain unproven. No paid API or online inference was added.
