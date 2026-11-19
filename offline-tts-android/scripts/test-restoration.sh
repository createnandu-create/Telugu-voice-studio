#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
bash native/rnnoise/build.sh host "$work/librnnoise_offline.so"
src=app/src/main/java/in/aitechapp/teluguoffline
javac -sourcepath app/src/main/java -encoding UTF-8 -d "$work" "$src/OfflineDenoiser.java" "$src/LateReverb.java" "$src/RenderCleanup.java" "$src/NarrationDirector.java" "$src/WarmthEq.java" "$src/PcmWave.java" "$src/FileIntegrity.java" tests/RestorationTest.java
java -Djava.library.path="$work" -cp "$work" in.aitechapp.teluguoffline.RestorationTest "$@"
