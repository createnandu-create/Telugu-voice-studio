#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(realpath -m "$1")"
mkdir -p "$out"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
assets="$(realpath app/src/main/assets)"
src=app/src/main/java
mkdir "$work/classes"
fetch() { curl --fail --location --silent --show-error --retry 3 "$1" -o "$work/$2"; }
for v in venkatesh padmavathi; do
  fetch "https://huggingface.co/rhasspy/piper-voices/resolve/cf910fb59f1d79ab3b625a63993a108751c6c25d/te/te_IN/$v/medium/te_IN-$v-medium.onnx" "$v.onnx"
done
for graph in text_encoder flow_decoder vocoder; do
  fetch "https://huggingface.co/sulabhkatiyar/indic-tiny-tts-telugu/resolve/55cfc683fafb55326a0a01932a3b2bb4374c4fc0/onnx/$graph.onnx" "$graph.onnx"
done
for graph in tone_ref_encoder_q8 tone_converter_q8; do
  fetch "https://huggingface.co/Q-P1NG/openvoice-v2-onnx/resolve/9383bf8b636a05319f94a4bb181777f32f6f8dfd/$graph.onnx" "$graph.onnx"
done
(cd "$work" && sha256sum -c - <<'SUMS'
728d9c2529e00d30de480c598369fad24919f83cd0ac44994a64724bbd3e3839  text_encoder.onnx
a523c39fe6b5df3a8cc76953fce9572ea6b7d3b8677d9232a4d5968fec75c40d  flow_decoder.onnx
44d36238a761750447038895ab194e2378f6f4ba2c2d089a4766fa52810ef353  vocoder.onnx
8e46097e46a68a2137acf105b58bc67cf686ec0d811c1c45ada28557a608c0e3  tone_ref_encoder_q8.onnx
54ea73764c46cdbb74af2124e30cce42007045f1f5a60bd7520472f155eb6f4c  tone_converter_q8.onnx
SUMS
)
fetch https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.31.0/onnxruntime-1.31.0.jar ort.jar
bash native/rnnoise/build.sh host "$work/librnnoise_offline.so"
javac -encoding UTF-8 -sourcepath "$src" -cp "$work/ort.jar" -d "$work/classes" tools/ReleaseAudio.java
run=(java -Djava.library.path="$work" -cp "$work/classes:$work/ort.jar" in.aitechapp.teluguoffline.ReleaseAudio)
"${run[@]}" prepare "$work" "$assets" "$out"
unzip -q "$assets/phonemes.zip" -d "$work/phones"
phones="$(find "$work/phones" -name phontab -printf '%h\n' -quit)"
test -n "$phones"
python3 -m venv "$work/venv"
"$work/venv/bin/pip" install -q sherpa-onnx==1.12.26 numpy
"$work/venv/bin/python" tools/piper-delivery-audit.py --models "$work" --espeak-dir "$phones" --output "$work" --plan "$work/plan.tsv"
"${run[@]}" retime "$work" "$assets" "$out"
"$work/venv/bin/python" tools/piper-delivery-audit.py --models "$work" --espeak-dir "$phones" --output "$work" --plan "$work/plan.tsv" --retry
"${run[@]}" render "$work" "$assets" "$out"
fetch https://repo.maven.apache.org/maven2/de/sciss/jump3r/1.0.5/jump3r-1.0.5.jar jump3r.jar
javac -encoding UTF-8 -sourcepath "$src" -cp "$work/classes:$work/jump3r.jar" -d "$work/classes" tools/ReleaseMp3.java
java -cp "$work/classes:$work/jump3r.jar" in.aitechapp.teluguoffline.ReleaseMp3 "$out"
"$work/venv/bin/pip" install -q imageio-ffmpeg==0.6.0
"$work/venv/bin/python" tools/check-mp3.py "$out"
(cd "$out" && sha256sum *.wav *.mp3 > SHA256SUMS.txt)
