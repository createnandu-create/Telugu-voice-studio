#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
printf '%s  %s\n' 5900832175a507e1ee5d4d596ff2c2b3a4eb66c59d2a12d33095c79e673e953d upstream-0.1.1.tar.gz | sha256sum -c -
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
tar xzf upstream-0.1.1.tar.gz -C "$build"
src="$build/rnnoise-6cbfd53eb348a8d394e0757b4025c6ded34eb2b6"
if [ "${1:-}" = host ]; then
  cc=(gcc); flags=(-I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux")
  dest="${2:?host output path required}"
else
  llvm="${ANDROID_NDK_HOME:?Set NDK r27c}/toolchains/llvm/prebuilt/linux-x86_64"
  cc=("$llvm/bin/clang" --target=aarch64-linux-android26 --sysroot="$llvm/sysroot")
  flags=(-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
  dest=../../app/src/main/jniLibs/arm64-v8a/librnnoise_offline.so
fi
"${cc[@]}" -shared -fPIC -O2 -DTRAINING=0 -I"$src/include" -I"$src/src" "${flags[@]}" \
  bridge.c "$src/src/denoise.c" "$src/src/kiss_fft.c" "$src/src/pitch.c" "$src/src/celt_lpc.c" \
  "$src/src/rnn.c" "$src/src/rnn_data.c" "$src/src/rnn_reader.c" -Wl,--build-id=sha1 -lm -o "$dest"
