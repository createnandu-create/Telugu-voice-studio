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
