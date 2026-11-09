#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
src=app/src/main/java/in/aitechapp/teluguoffline
javac -encoding UTF-8 -d "$out" "$src/NarrationDirector.java" "$src/SemanticNarrationPrompt.java" "$src/DirectionContract.java" "$src/ModelCatalog.java" "$src/FileIntegrity.java" "$src/PcmWave.java" "$src/RenderJournal.java" tests/*.java
java -cp "$out" in.aitechapp.teluguoffline.NarrationDirectorTest
java -cp "$out" in.aitechapp.teluguoffline.OfflinePipelineTest
