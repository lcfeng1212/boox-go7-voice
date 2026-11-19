#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" \
  app/src/main/java/tw/boox/voicepage/CommandEngine.java \
  app/src/main/java/tw/boox/voicepage/GestureGeometry.java \
  app/src/main/java/tw/boox/voicepage/BallLayout.java \
  app/src/main/java/tw/boox/voicepage/ModelStore.java \
  app/src/main/java/tw/boox/voicepage/ListeningHealth.java \
  app/src/main/java/tw/boox/voicepage/RestartPolicy.java \
  tests/VoicePageTests.java tests/GrammarDump.java tests/ModelStoreTests.java
java -ea -cp "$out" VoicePageTests
java -ea -cp "$out" ModelStoreTests
# With a model directory argument, also verify every grammar token exists in the model vocabulary.
if [ "${1:-}" != "" ]; then
  java -cp "$out" GrammarDump | python3 scripts/check_grammar_vocab.py "$1"
fi
