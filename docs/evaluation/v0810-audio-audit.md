# v0.8.10: technical speech and native-tone audit — 20 November 2026

## Continuation verified

The previous technical Telugu work is on `telugu-voice-testing`, not stranded in a chat: commit `a9f887634f1756ead822a08b60f298bf95591eba`, successful Actions run [44670042515](https://github.com/createnandu-create/Telugu-voice-studio/actions/runs/44670042515), release `v0.8.9-a9f887634f17`. This update builds on that commit; no rollback to an earlier app.

## Uploaded recordings

The supplied Karthik, Indic-TTS Male and Srinivas WAVs are 44,100 Hz mono, respectively 93.302, 90.461 and 98.341 seconds. All peak at approximately -1.10 dBFS, with zero samples at or above 0.9999 full scale. That does **not** establish absence of audible distortion, but there is no evidence here of clipped PCM or a wrong WAV sample-rate header. The Hima MP3 is 24 kHz mono, 11.963 seconds, with no decoded full-scale clipping. [Aggregate measurements](v0810/upload-metrics.json). Private recordings are not redistributed.

Offline IndicConformer recognition of the uploaded files recovered the earlier literal `ఈజ్ టు` ratio reading approximately as `ఇస్టు` / `ఎజ్`. It also made errors on channel names, abbreviations, and conjuncts. This is consistent with literal reading; the audio alone cannot identify an APK version or the user's selected explanation toggle. v0.8.9 already defaults to `పదహారు భాగాలకు తొమ్మిది భాగాలు`, previews it in Direction, and keeps literal mode available. ASR disagreement is a diagnostic lead, not proof of what a Telugu listener hears.

## Reproduced implementation problems and changes

* Every generated chunk passed through RNNoise-derived spectral masking, tone EQ and tail reduction even when optional female EQ was off. The native engine output still existed in Projects, but the default master did not preserve its tone.
* Indic Tiny also altered the acoustic mel spectrum before Vocos; disabling the visible EQ did not disable this stage. Tiny Female selection silently enabled another EQ stage.
* Cleanup/tone processing is now an explicit **Narration cleanup + tone EQ** switch, off for new and legacy settings lacking this key. It controls all three stages; reference-recording enhancement remains separate. Tiny Female no longer re-enables EQ on voice selection. The queued settings and render identity include the choice. Existing projects and model downloads remain intact.
* With delivery effects and cleanup disabled, native PCM is copied exactly, avoiding another PCM quantization. Resampling, padding and final loudness/peak control remain separate export operations. This is not a claim that the entire mastered export is bit-identical to the model.
* Previously unsupported `°C`, `℃`, `°F`, `℉`, `cm²`, `m³`, `km/h`, `m/s`, `μs`/`µs` and `Ω` now have Telugu readings in technical explanation mode, including signed temperatures. Letter-number boundaries are retained (`V25` is **not** guessed to mean `√25`; `2x` is kept separate). Original editor text stays intact. This is a finite offline normalizer, not arbitrary scientific reasoning or translation.

## Controlled four-voice evidence

`tools/narration-audit/ToneComparison.java` renders a new test script with Indic-TTS Male/Female (FastPitch + HiFi-GAN, 22.05 kHz) and Indic Tiny Male/Female (Vocos, 24 kHz):

> దృశ్య నిష్పత్తి 9:16. √144 = 12. రిజల్యూషన్ 8K.

Every pair shares the same native acoustic take, speed 1.0, no question pitch manipulation and no ASR retries. Optional Tiny processing includes the former mel adjustment and female EQ. Native and optional MP3s are level-matched to -23 dBFS RMS using gain only, 44.1 kHz mono / 160 kbps. They are host CPU evidence, **not recordings from the phone**. Raw native-rate WAVs and the comparison data remain available in the audit workspace.

[Measured spectra and independent ASR transcripts](v0810/tone-comparison.json):

* The recognizer recovered the ratio operands and `144`/`12` in all four voices, both native and processed.
* Indic-TTS recovered `8K` as `ఎయిట్ కె`. Tiny Male lost part of `ఎయిట్`; Tiny Female did not reliably recover the final `కే`. Cleanup did not fix this. Word-error counts also include spacing (`దృశ్యనిష్పత్తి`) and equivalent spelling differences; they are not naturalness scores.
* FastPitch already has very little energy above 6 kHz in its **native** output (female 0.014%, male 0.058% of measured spectral energy on this script). Its tone cannot be attributed solely to the app filters.
* Old optional processing shifted FastPitch Male's 85–300 Hz share from 53.52% to 65.44%, and Female from 64.09% to 72.87%. More bass is not automatically clearer speech. Native Tiny Female is mid-heavy even before these filters. Removing forced processing restores user choice, not missing learned phonemes or guaranteed human timbre.

## Validation and remaining limits

Java regression coverage includes unseen numeric values, signed units, ratio/root distinction, variable boundaries, original-script preservation and literal-mode retention. Android tests cover unsupported-symbol rejection prevention, opt-in migration, queued-setting isolation, native PCM identity at both model rates, and female voice-switch defaults. Local v0.8.10 debug APK assembly passed; all 31 Android/JVM UI/integration tests passed, with zero failures/skips. All ten standalone Java test entry points passed, as did bundle verification, ICU regex checks and the native restoration checks. Final lint: zero errors, 15 warnings. GitHub Actions repeats these checks before publishing and verifies the package/certificate against v0.8.9.

Srinivas, Karthik and Bhavana still have different learned pronunciations. The app sends a shared normalized reading, but cannot guarantee identical intelligibility across models. No fabricated phoneme IDs, inferred voice training, changed model weights, speaker mixing, or new cloning claims are introduced. Remaining acceptance requires Telugu listening on the Nothing Phone (1), airplane-mode rendering and long-form checks; this host session cannot certify those. The `8K` finding remains open, not hidden by a successful build.
