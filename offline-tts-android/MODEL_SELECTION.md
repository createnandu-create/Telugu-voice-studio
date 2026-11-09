# Current implementation note (2026-11-09)

Piper/sherpa integration now exists on the testing branch. Read [the current evaluation and limitations](../docs/research/OFFLINE_NEURAL_UPGRADE.md) for pinned model versions, measured host audio, and still-unverified Android execution. The proposal below is historical; its "candidate" and "no GPL code" statements describe the pre-upgrade baseline, not this release.

# November 2026: What can actually run on Nothing Phone (1) (Snapdragon 778G+, 8 GB RAM)?

## Proven public infrastructure vs Telugu capability

| Piece | Android support | Telugu | Emotional control | Zero-shot cloning | Actual status |
|---|---|---|---|---|---|
| Android platform TTS (offline installed voice) | Native Android | Depends on installed pack | Rate/pitch only | No | Implemented in this patch |
| Piper Telugu + sherpa-onnx | sherpa Android ARM64 supports TTS; Telugu phonemizer/ONNX compatibility needs work | Piper Telugu weights available | No direct emotion tags | No | Candidate to implement and test |
| LiteRT-LM + Qwen3.5-0.8B | Android Kotlin runtime; 2026 LiteRT conversion is downloadable | Multilingual capability must be validated on user scripts | Can generate suggested sentence tags, not audio | No | Not integrated; requires runtime & model |
| Indic-Mio 0.6B + MioCodec | No verified plug-and-play Android build | Official 22 Indic languages + English | Supports tags happy/sad/surprise etc. | Officially yes | Best research candidate, NOT ported |
| sherpa-onnx + ZipVoice / Pocket | Android cloning APIs exist | Available standard checkpoints primarily English/Chinese / other explicitly specified packs; Telugu not established | Checkpoint-dependent | Yes in covered languages | Not a Telugu cloning claim |

## Suggested two-stage architecture

1. **ScriptDirector**: Existing `NarrationDirector.java` is an offline deterministic fallback. Replace with a local model **only after** benchmarking Telugu script understanding and structured output validity.
2. **Optional semantic understanding model**: A community-converted `litert-community/Qwen3.5-0.8B` `.litertlm` bundle (2026-11-06, approx. 791 MB) is a concrete candidate for the on-device script director. It needs LiteRT-LM >=0.20.0 and validation on Android. Its 0.8B size does not guarantee deep Telugu understanding.
3. **Voice**: Piper with Android-capable ONNX runtime can provide base offline Telugu. It has no clone/emotion tags. For expressive cloning, research and port Indic-Mio end to end (including MioCodec). Don't claim real offline cloning until reference-audio voice identity and output have been verified on the actual Nothing Phone (1).
4. Generate sentence WAV chunks and merge with stable loudness, chosen pauses, no accidental tag speech. Review important sections before output.
5. Run inference modules **sequentially**, not all loaded simultaneously; monitor RAM, device temperature, and speed.

## Offline acceptance test

- Download model and runtime when online; do not mark 'Installed' unless all files and checksums match.
- Turn Wi-Fi/mobile data OFF, restart the app, generate a short Telugu sentence and save WAV.
- 20 test scripts of tutorial, review, safety notice, story, simple dialogue, code-mixed text.
- For cloning, a consenting speaker's 10–20-second clean reference + exact transcript; compare output with original voice and listen for Telugu pronunciation and emotional delivery.
- No fake pass: a reachable site or successful API initialization is **not** successful waveform generation.

## Source references (public)

- https://github.com/k2-fsa/sherpa-onnx
- https://github.com/HayaiApp/HayaiTTS
- https://huggingface.co/SPRINGLab/Indic-Mio
- https://huggingface.co/litert-community/Qwen3.5-0.8B
- https://developers.google.com/edge/litert/overview
- https://github.com/createnandu-create/Aiide/tree/telugu-voice-testing

## Legal & permissions

Clone only voices belonging to the user or speakers who explicitly consent. Check licenses of each model, codec, bundled dataset/voice, phonemizer, Android runtime and any third-party app code. The external HayaiTTS app is GPL-3.0, so do not copy its code into a closed-source/permissively licensed app without appropriate compliance. The patch itself does not include borrowed GPL code or weights.

## 2026-11-09 urgent update: small Telugu ONNX candidate and neural audio distinction

**NEW FIRST PROTOTYPE: [Indic Tiny TTS Telugu](https://huggingface.co/sulabhkatiyar/indic-tiny-tts-telugu)**. Publisher reports 22.5M parameters (~90 MB) and 24 kHz male/female synthesis; model files include THREE ONNX graphs (`text_encoder.onnx`, `flow_decoder.onnx`, `vocoder.onnx`) with a Vocos vocoder. The published ONNX example runs on CPU with no eSpeak dependency. The developer's own 100 Telugu FLORES-200 sentence test reports ~1.99% recognition CER vs ~2.97% for their measured Piper Telugu sample; **CER is not perceived naturalness**. Android integration is NOT done. Port graph preprocessing/tokenization, inference scheduling and vocoder to ONNX Runtime Android; validate on physical Nothing Phone (1). Acoustic weights CC-BY-4.0 (attribute), Vocos code/weights MIT. Latin-English words and complex numerals need normalization in Telugu script; it is a fixed-voice calm reader, NOT expressive voice cloning.

Other candidates: [Indic-Mio 0.6B](https://huggingface.co/SPRINGLab/Indic-Mio) explicitly supports Telugu, emotion tags and cloning with MioCodec (Apache-2.0 model) but the original vLLM+Python+codec path is not ready for 8 GB ARM64. [Chatterbox Telugu fine-tune](https://huggingface.co/shankarpandala/chatterbox-telugu) supports 6–15 second zero-shot reference audio (CC-BY-4.0 model); [Indic Parler-TTS](https://huggingface.co/ai4bharat/indic-parler-tts) can describe Telugu vocal delivery but is heavy and gated. [Rumik OSS 1](https://huggingface.co/rumik-ai/rumik-oss-1) has excellent concept-level expression tags in Telugu but 3B weights and CC-BY-NC-4.0; not suitable for monetized videos or this phone baseline. **[Supertonic 3](https://github.com/supertone-oss-archive/supertonic) lacks official Telugu** despite CPU ONNX and expression tags.

**Formats**: Master: uncompressed 48kHz/24bit WAV for video-editor interoperability after high-quality resampling, or native-rate FLAC for archiving. MP3 320k only for sharing. Never claim that converting native 24kHz audio to 48kHz increases synthesized fidelity. Remove perceived metallic timbre primarily through a better speech model/vocoder, not re-encoding or noise-suppression.

**Audio tuning libraries**:
- Built-in [Android DynamicsProcessing](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing) affects session playback only; not `synthesizeToFile` automatically.
- [FFmpeg loudnorm](https://ffmpeg.org/ffmpeg-filters.html) with libsoxr for high-quality resampling and two-pass loudness matching in final production.
- [Google Oboe](https://github.com/google/oboe) for on-device low latency PCM, not a voice quality enhancer.
- [Resemble Enhance](https://github.com/resemble-ai/resemble-enhance) (MIT) and [ClearerVoice Studio](https://github.com/modelscope/ClearerVoice-Studio) (Apache-2.0) for experimental neural audio restoration; **not** turnkey Android and can change synthetic vowel/consonant timbre or introduce hallucinated frequencies.
- [DeepFilterNet](https://github.com/Rikorose/DeepFilterNet) targets real microphone/background noise, expects 48kHz; use for recorded references only where actually noisy, not as a magic fix for generated robotic speech.
- [Rubber Band](https://breakfastquay.com/rubberband/) permits time stretching but is GPL/commercial licensed, not a permissive code dependency, and doesn't add expressive prosody.

**Next real acceptance**: create distinct offline WAV samples from stock Android TTS neutral, Android TTS tuned filter, Piper Telugu, Indic Tiny TTS Telugu ONNX. Use original user Telugu script, listening comparison (loudness-matched), CER for words, model runtime, memory and waveform analysis. Prefer the audible model, not the one with the largest parameter count. Only THEN approach local semantic direction with LiteRT-LM, and local consented Indic-Mio cloning after a real native ARM64 port.
