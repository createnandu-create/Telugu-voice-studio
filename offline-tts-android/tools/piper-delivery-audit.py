"""Generate host-only fixed-speaker Piper fixtures; no download or server inference.

Requires sherpa-onnx==1.12.26, numpy, repaired v0.6 Piper ONNX files, and the
APK's extracted phonemes.zip. See docs/evaluation/delivery-v07.
"""
import argparse
import hashlib
import json
import pathlib
import time
import wave
import numpy as np
import sherpa_onnx

parser = argparse.ArgumentParser()
parser.add_argument("--models", type=pathlib.Path, required=True)
parser.add_argument("--espeak-dir", type=pathlib.Path, required=True)
parser.add_argument("--output", type=pathlib.Path, required=True)
parser.add_argument("--plan", type=pathlib.Path, required=True, help="TSV emitted by DeliveryAudit plan")
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
assets = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/assets"
rows = [line.split("\t") for line in args.plan.read_text().splitlines()]
records = []
for voice in ("venkatesh", "padmavathi"):
    model = args.models / f"{voice}-fixed.onnx"
    config = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=str(model),
            tokens=str(assets / f"{voice}-tokens.txt"), data_dir=str(args.espeak_dir)),
        num_threads=2, debug=False), max_num_sentences=1)
    engine = sherpa_onnx.OfflineTts(config)
    for index, row in enumerate(rows):
        start = time.monotonic()
        audio = engine.generate(row[4], sid=0, speed=float(row[0]))
        elapsed = time.monotonic() - start
        file = args.output / f"{voice}-youtube-{index}.wav"
        with wave.open(str(file), "wb") as out:
            out.setparams((1, 2, audio.sample_rate, 0, "NONE", ""))
            out.writeframes((np.clip(audio.samples, -1, 1) * 32767).astype("<i2").tobytes())
        records.append(dict(file=file.name, text=row[4], seconds=len(audio.samples)/audio.sample_rate,
            render_seconds=elapsed, sha256=hashlib.sha256(file.read_bytes()).hexdigest(),
            execution="Linux x86_64 host, not Android"))
    del engine
(args.output / "youtube-piper-metrics.json").write_text(json.dumps(records, ensure_ascii=False, indent=2))
