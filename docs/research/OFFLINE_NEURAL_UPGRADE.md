# Offline neural upgrade — 2026-11-09

## Decision and actual boundaries

Integrate **Piper Telugu with sherpa-onnx 1.12.26** as the next executable baseline. It has real Telugu checkpoints and an ARM64 native runtime. This build adds an English native Studio, download/repair/remove controls, a checksum-verified model store, editable heuristic rate/pause plans, a foreground sentence renderer, atomic chunk receipts, cancellation/resume, WAV preview/export and measured render telemetry. The older Android system-TTS baseline remains accessible in Settings.

This does **not** solve the requested expressive quality yet. Piper does not accept emotion tags, speaker references, emphasis, pitch or energy instructions. The UI says so. Direction labels are review notes; only speed and pause affect this renderer. Nothing is trained in this change. No hosted inference, paid API, API key or semantic LLM is used.

The same converted models produced genuine offline **Linux x86_64 CPU** WAVs. Android JNI compilation is distinct from successful phone inference. No physical Nothing Phone (1) was connected. Android flight-mode synthesis, playback, permissions, recording, service lifecycle, download interruption, thermal limits and UI appearance still require device validation. Do not use the host numbers to predict Snapdragon speed.

## Model installation and integrity

The ARM64-only APK contains runtime libraries, phoneme data, tiny token tables and 141 bytes of ONNX metadata (no weights). Each voice downloads 63,516,050 bytes from a pinned Hugging Face **file** URL. SHA-256 is checked before any conversion; metadata is appended to the ONNX protobuf without altering tensors; the resulting whole file is checked against a second pinned SHA-256. A verified file is atomically promoted. An interrupted download leaves no installed marker; the next Download starts again. Phoneme files also have individual SHA-256 values.

The converted byte stream matches the ONNX-library conversion used for host synthesis. Model installation and generation are separate paths. `VoiceModelStore.install()` is the sole network entry point. Rendering uses files only. The app requests INTERNET for explicit installs, not for synthesis. No voice APK is installed or silently granted permissions; voice weights are ordinary app-private data.

Source files and exact licenses are in `offline-tts-android/NOTICE.md`. Voice model attribution is PravalX / AI4Bharat IndicVoices-R, CC BY 4.0. Because eSpeak is GPL, the Android app is distributed as GPL-3.0-or-later with upstream corresponding-source archives in the Source ZIP.

## Local semantic director evaluation

Primary sources:
- https://github.com/google-ai-edge/LiteRT-LM (Android Kotlin runtime documented)
- https://huggingface.co/litert-community/Qwen3.5-0.8B
- Pinned file sizes/revisions: [inventory](model-inventory-2026-11-09.json)

The candidate `Qwen3.5-0.8B_dynamic_wi8_afp32.litertlm` is **790,659,072 bytes**. The model card explicitly requires LiteRT-LM **0.20.0 / main** for this file. Other similarly named files have different runtime requirements. It reports Android GPU crashes for external-weight files on 0.18.x; these are upstream reports, not our measurements. Do not substitute an arbitrary .litertlm model or runtime based on filename alone.

The small weight file does not establish Telugu semantic accuracy or long-context handling. A 10–20-minute Telugu script may exceed the converted model's usable context; global summary + paragraph windows + original sentence IDs would need evaluation. Before enabling this model: pin runtime/artifact hashes, run CPU first on the target phone, measure peak PSS and prompt/output tokens, validate exactly preserved sentence text, enforce finite bounded controls, test hallucinations and contradictory instructions, and unload the director before loading speech. Current `DirectionContract` rejects changed facts, word spacing, order, missing chunks and invalid controls. It is a guard for future inference, not inference itself. `SemanticNarrationPrompt` remains a future contract.

## Indic-Mio + MioCodec feasibility

Primary sources:
- https://huggingface.co/SPRINGLab/Indic-Mio (Apache-2.0)
- https://huggingface.co/Aratako/MioCodec-25Hz-24kHz (MIT)
- https://github.com/Aratako/MioTTS-Inference

The Indic-Mio card lists 22 scheduled Indian languages (including Telugu), code mixing, zero-shot cloning and end-of-sentence emotion tags. Its reference path uses vLLM or PyTorch bfloat16 with `device_map="cuda"`. It is **not** an Android ONNX checkpoint. Listed weights: **1,217,825,224 bytes**; MioCodec: **523,087,956 bytes**. File storage is not peak RAM; activations, token caches, speaker encoding and codec decoding add memory. No automatic quality-preserving 4× compression is demonstrated here.

A concrete inconsistency needs resolution before porting: the Indic-Mio example loads `MioCodec-25Hz-24kHz` but writes WAV at **44,100 Hz**; the codec card specifies **24,000 Hz** and names a separate 44.1 kHz model. Using an incorrect header changes duration and pitch. Its simplified decoder example also does not establish complete reference-speaker embedding handling. Follow the codec's actual config and inference implementation, not the example blindly.

A plausible research path is quantized causal-model generation (e.g. a proven compatible llama.cpp/ExecuTorch export), speech-token validation, exported global speaker encoder and codec decoder, then JNI integration. Each component must first generate the same Telugu waveform in an isolated ARM64 test. iSTFT/complex operations, tokenizer offsets, reference embeddings and quantization error must be checked. No end-to-end Android port was completed or benchmarked in this work. No clone controls are falsely enabled; the consented local recording feature explicitly states it cannot yet clone.

Google/ElevenLabs hosted voices are not downloadable offline model weights. Their service quality is a listening target, not a source from which we can infer proprietary architecture or train a compatible local clone merely by copying rate/pitch settings.

## Long-form behavior and remaining risks

The new renderer has no 220-sentence limit. It uses capped sentence chunks, stable voice identity, fixed model noise settings, user-editable pauses and streaming PCM16 WAV assembly. Chunks are reused only when model/engine/text/speed/pause identities and per-chunk SHA-256 match. Whole-result replacement is atomic. A foreground service and timed wake lock allow screen-off work; severe Android thermal status stops at a chunk boundary and keeps progress. Cancellation waits for the current native call; a native crash can still kill the process, with completed chunk receipts retained.

A 60,000-character per-job limit is explicit and rejects oversized renders without truncating the script. Native calls run two CPU threads. This build samples process PSS, battery temperature (not CPU temperature), thermal status, elapsed time, output duration and reused chunks. No MOS/voice similarity is fabricated. Resuming is manual, and model downloads restart rather than resume byte ranges. Installed voices use ~63.5 MB each; shared phonemes and render chunks consume extra storage.

The heuristic director is not full-script understanding. Correct pronunciation, convincing emotion, speaker similarity, code mixing, abbreviation handling, prosodic continuity and physical-phone performance remain acceptance gates. The original system-TTS fallback retains its older 220-sentence export limit; the new neural path removes that restriction. WAV export in the new Studio uses Android's document picker and works without broad storage access.

## Concurrent audio-quality fixes preserved

Before publishing, the remote branch had advanced through `014c2a113a3af2db65d64cfda2801606490c6661`. The incoming AudioPolish implementation/tests, quality-sorted system-voice picker, neutral defaults and diagnosis were merged intact. Read [the supplied WAV diagnosis](../AUDIO_QUALITY_DIAGNOSIS.md). Clarity EQ remains opt-in in the system-TTS fallback; it is not silently added to the neural renderer or claimed to fix model timbre. No user-supplied audio was published.
