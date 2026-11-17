#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" \
  app/src/main/java/tw/boox/voicepage/CommandEngine.java \
  app/src/main/java/tw/boox/voicepage/GestureGeometry.java \
  app/src/main/java/tw/boox/voicepage/ListeningHealth.java \
  app/src/main/java/tw/boox/voicepage/RestartPolicy.java \
  tests/VoicePageTests.java
java -ea -cp "$out" VoicePageTests
