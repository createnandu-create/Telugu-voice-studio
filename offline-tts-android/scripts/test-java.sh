#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
src=app/src/main/java/in/aitechapp/teluguoffline
javac -encoding UTF-8 -d "$out" "$src/AudioPolish.java" "$src/StudioMastering.java" "$src/WarmthEq.java" "$src/NarrationDirector.java" "$src/SemanticNarrationPrompt.java" "$src/DirectionContract.java" "$src/ModelCatalog.java" "$src/FileIntegrity.java" "$src/PcmWave.java" "$src/RenderJournal.java" "$src/PronunciationLexicon.java" "$src/RecordingClock.java" "$src/InstallReceipt.java" "$src/ResumableDownload.java" tests/*.java
java -cp "$out" in.aitechapp.teluguoffline.NarrationDirectorTest
java -cp "$out" in.aitechapp.teluguoffline.OfflinePipelineTest
java -cp "$out" in.aitechapp.teluguoffline.AudioPolishTest
java -cp "$out" in.aitechapp.teluguoffline.WarmthEqTest
java -cp "$out" in.aitechapp.teluguoffline.LifecycleTest

java -cp "$out" in.aitechapp.teluguoffline.MasteringTest
