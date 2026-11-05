#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
javac -d "$test_dir" \
  app/src/main/java/org/morserunner/tablet/NativeEngine.java \
  app/src/main/java/org/morserunner/tablet/CallsignInfo.java \
  app/src/main/java/org/morserunner/tablet/TrainingProfile.java \
  tests/java/org/morserunner/tablet/CallsignInfoTest.java \
  tests/java/org/morserunner/tablet/TrainingProfileTest.java
java -cp "$test_dir" org.morserunner.tablet.TrainingProfileTest
java -cp "$test_dir" org.morserunner.tablet.CallsignInfoTest app/src/main/assets/ARRL.LIST
