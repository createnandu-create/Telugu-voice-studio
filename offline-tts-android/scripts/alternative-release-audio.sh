#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(realpath -m "$1")"
assets="$(realpath app/src/main/assets)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp"/{hima,clone,ecapa,indic,classes} "$out"
fetch() {
  local url="$1" dest="$2" mirror="https://maven-central.storage-download.googleapis.com/maven2/"
  if curl --fail --location --silent --show-error --retry 3 "$url" -o "$dest"; then return; fi
  if [[ "$url" != "$mirror"* ]]; then return 1; fi
  curl --fail --location --silent --show-error --retry 3 "https://repo.maven.apache.org/maven2/${url#"$mirror"}" -o "$dest"
}
hf=https://huggingface.co
hima="$hf/Prahaladha/hima-tts-nano-te-ONNX/resolve/8fd92e3956163815892f6adcf7e3aae91e70272b"
for p in prefix.safetensors tokenizer.model voices/voices.safetensors onnx/decoder_int8.onnx onnx/voices/step_int8.onnx onnx/voices/frame_int8.onnx; do
  fetch "$hima/$p" "$tmp/hima/$(basename "$p")"
done
for p in step_int8.onnx frame_int8.onnx; do fetch "$hima/onnx/base/$p" "$tmp/clone/$p"; done
fetch "$hf/Prahaladha/hima-tts-nano-te/resolve/af61ec03fa408d48effdaa4ee0d5ad9e54e6e4fd/codec.safetensors" "$tmp/clone/codec.safetensors"
for p in ecapa-speaker-v1.onnx fbank-80x201-f32.bin; do
  fetch "$hf/vedk00/ecapa-voxceleb-speaker-embedding-onnx/resolve/a9cb9321b07b4ee5b0ea47fdd25242d9cacd824a/model/$p" "$tmp/ecapa/$p"
done
fetch "$hf/helo-ayush/itantra-models/resolve/78f9e2ce6fc3a508e4d68a67146579adfd7add42/te-IN-1.0.0.itantra" "$tmp/te.zip"
(cd "$tmp" && echo '79fa256cac907c2e86a8093f9c1d1bcab14d9ee1c7cc1f0b4b578386c07bf68b  te.zip' | sha256sum -c -)
unzip -j -q "$tmp/te.zip" tts/fastpitch.onnx tts/hifigan.onnx -d "$tmp/indic"
gzip -dc "$assets/fastpitch-pace.pb.gz" > "$tmp/indic/fastpitch-pace.pb"
gzip -dc "$assets/hima-encoder.pb.gz" > "$tmp/clone/encoder.pb"
maven=https://maven-central.storage-download.googleapis.com/maven2
fetch "$maven/com/microsoft/onnxruntime/onnxruntime/1.31.0/onnxruntime-1.31.0.jar" "$tmp/ort.jar"
fetch "$maven/org/json/json/20240303/json-20240303.jar" "$tmp/json.jar"
bash native/rnnoise/build.sh host "$tmp/librnnoise_offline.so"
javac -encoding UTF-8 -sourcepath app/src/main/java -cp "$tmp/ort.jar:$tmp/json.jar" -d "$tmp/classes" tools/AlternativeReleaseAudio.java
java -Djava.library.path="$tmp" -cp "$tmp/classes:$tmp/ort.jar:$tmp/json.jar" in.aitechapp.teluguoffline.AlternativeReleaseAudio "$tmp" "$out"

mkdir -p "$tmp/audition"
unzip -q "$tmp/te.zip" stt/indicconformer_int8.onnx stt/vocab.json -d "$tmp"
javac -encoding UTF-8 -sourcepath app/src/main/java -cp "$tmp/classes:$tmp/ort.jar:$tmp/json.jar" -d "$tmp/classes" ../tools/narration-audit/NarratorComparison.java
java -Djava.library.path="$tmp" -cp "$tmp/classes:$tmp/ort.jar:$tmp/json.jar" in.aitechapp.teluguoffline.NarratorComparison "$tmp" "$assets" "$tmp/audition"
python3 -m venv "$tmp/venv"
"$tmp/venv/bin/pip" -q install 'onnxruntime>=1.23,<2' scipy soundfile numpy imageio-ffmpeg
"$tmp/venv/bin/python" ../tools/narration-audit/transcribe.py "$tmp" "$tmp/audition"
(cd "$tmp/audition" && sha256sum *.mp3 > SHA256SUMS.txt && zip -q "$out/YouTube-Voice-Comparison.zip" *.mp3 *.txt *.json)
cp "$tmp/audition/recognition.json" "$out/narration-recognition.json"
