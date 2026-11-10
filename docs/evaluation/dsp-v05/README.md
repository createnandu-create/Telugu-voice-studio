# Offline mastering v0.5 — measured changes, unresolved pronunciation

The two November 10 user uploads are byte-for-byte identical: SHA-256 `d84925b501383582beb89ef7faa1ad1b2caddb5c906dde38781518483e3865ec`, mono PCM16, 22,050 Hz, 163,057 frames (7.394875 s). Their different filenames do not establish a before/after change. The user's audio and processed copy stay private; only measurements/hashes are committed. The four WAVs in this folder were generated from the repository's public Piper fixtures, not a personal reference.

## Actual chain

`StudioMastering.java` runs locally after chunk assembly, with bounded page buffers and a temporary float file:

1. 32-tap Blackman-windowed **polyphase sinc 2× resampling**: 22,050 → 44,100 Hz. Integer phase preserves source samples; fractional phase reconstructs between them.
2. RBJ biquad low shelf **180 Hz / +3.0 dB / Q 0.707**, high shelf **6,000 Hz / −2.5 dB / Q 0.707**. These are the values in the user's detailed numbered specification; the introductory diagram's different values were not used.
3. BS.1770 K weighting and integrated loudness: 400 ms blocks, 100 ms hop, −70 LUFS absolute gate, −10 LU relative gate; both channels' energy included.
4. Feed-forward peak limiting using 4× sinc reconstruction peaks, 10 ms block lookahead / hold and 80 ms release. A final reconstruction check after PCM quantization applies a safety correction when necessary. Requested ceiling is **≤ −1.0 dBTP**; an internal −1.10 dB margin protects rounding/reconstruction differences.
5. Iterative loudness correction, accepted only within **0.03 LU of −14.0 LUFS**, while keeping the measured ceiling. If the target cannot be reached, fail clearly and keep raw audio; do not falsely label it mastered.
6. **Exactly 6,615 zero frames at the front (150 ms)** and **4,410 at the end (100 ms)** at 44,100 Hz. Padding is outside EQ/limiting, so it stays exactly zero. Apply it once per completed narration, not once per sentence. Final WAV is PCM16 **dual-mono stereo**; this does not create spatial content.

The original `narration.wav` and cached sentence PCM remain unchanged. The default new output is `narration-studio.wav`; **Studio master / Raw voice** controls allow preview and export of either. Press **Generate / Resume** to create a master for a previous render: cached raw chunks are reused but mastering runs again. No model re-download is necessary. A failed/cancelled mastering operation preserves the raw render and any previously completed master. MP3 encoding is **not implemented** in this build.

Speech-only normalization now removes leading BOM / ZWNJ / ZWJ / zero-width space / word-joiner characters before phonemization. Interior Telugu joiners and the user's displayed script are preserved.

## Independent measurements

`measurements.json` records Java results, source/output SHA-256 and separate FFmpeg `ebur128=peak=true` results. Six fixtures passed:

| Input | FFmpeg integrated loudness | FFmpeg true peak |
|---|---:|---:|
| Piper Venkatesh, నమస్కారం | −14.0 LUFS | −1.4 dBTP |
| Piper Venkatesh, greeting sentence | −14.0 LUFS | −1.8 dBTP |
| Piper Padmavathi, నమస్కారం | −14.0 LUFS | −2.6 dBTP |
| Piper Padmavathi, greeting sentence | −14.0 LUFS | −3.9 dBTP |
| User WAV, private output | −14.0 LUFS | −1.1 dBTP |
| Quiet 4.1 kHz tone + isolated loud transient | −14.0 LUFS | −1.1 dBTP |

For the user WAV Java measured **−13.9951 LUFS / −1.1003 dBTP**, 337,139 final stereo frames (7.644875 s). Exact padding, identical L/R channels and unchanged source bytes were checked. The isolated tone fixture's 17.95 kHz resampling image was **−101.49 dB relative to 4.1 kHz**, measured away from the transient; this is one objective fixture, not a universal perceptual quality claim.

Reproduce using Java 17: compile `StudioMastering`, `PcmWave`, `FileIntegrity`, `NarrationDirector` and `tests/MasteringTest.java` into a temporary class directory, then run:

```sh
java -cp "$classes" in.aitechapp.teluguoffline.MasteringTest input-22050-mono.wav output-stereo.wav
ffmpeg -hide_banner -i output-stereo.wav -af ebur128=peak=true -f null -
```

`bash offline-tts-android/scripts/test-java.sh` also checks loudness/peak, exact head/tail frame counts, dual-mono channels, cancellation, unchanged raw bytes and rejection of silent audio instead of an invented loudness value. Android tests include the recording consent dialog at 393 dp; its width is explicitly bounded to avoid repeated layout of a wrapping dialog.

## What these results do not prove

The DAC-startup diagnosis is **unverified**. Padding may help a player whose hardware opens late; it cannot restore /n/ if it is absent in generated PCM. The earlier phonemizer audit already contained the leading `n`. No human listening verdict confirms corrected `నమస్కారం` or the removal of all metallic timbre. EQ changes frequency balance; it does not synthesize missing chest resonance or human expressiveness. Resampling prevents conversion images, not artifacts already produced by a model.

The current engine is **Piper VITS through sherpa-onnx**, with an integrated learned waveform decoder. It is **not Griffin-Lim**. No compatible, separately conditioned INT8 HiFi-GAN checkpoint/Android path was verified, so no decoder replacement is claimed. Semantic LLM direction, expressive voice cloning and physical Nothing Phone DAC/microphone/thermal benchmarks remain unimplemented or untested as recorded in the previous audit. These files are a DSP upgrade, not a new neural voice model.

Attribution: Piper Venkatesh/Padmavathi model cards and CC BY 4.0 credits remain in `app/src/main/assets/licenses`; raw comparison samples are in `../pronunciation-v04`. Biquad equations follow the RBJ Audio EQ Cookbook; loudness weighting/gating follow ITU-R BS.1770 / EBU R128. FFmpeg is used for independent host validation, not bundled as an Android inference service.

Local build validation: requested `:app:assembleDebug` passed; 7/7 Robolectric Android/UI tests and lint passed (0 errors). Java suites include 26 lifecycle checks plus mastering tests. APK signature and 16 KB ZIP alignment passed. Physical-phone testing remains outstanding.

Parallel native-rate changes were merged without overwriting their code/tests/preferences. `PcmWave.merge()` retains the legacy padded/EQ comparison path; `mergeRaw()` provides byte-identical source PCM to StudioMastering. The Settings switch now explicitly creates an additional **native EQ comparison**, OFF by default. It does not apply EQ twice to the Studio master. A byte-for-byte single-chunk merge test guards this boundary.
