#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
"${CXX:-g++}" -std=c++17 -O2 -Wall -Wextra -Werror -shared -fPIC \
  -Icore -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  core/engine.cpp app/src/main/cpp/bridge.cpp -o "$test_dir/libmorserunner.so"
"$JAVA_HOME/bin/javac" -d "$test_dir" \
  app/src/main/java/org/morserunner/tablet/NativeEngine.java \
  tests/java/org/morserunner/tablet/NativeEngineIntegrationTest.java
"$JAVA_HOME/bin/java" -Djava.library.path="$test_dir" -cp "$test_dir" org.morserunner.tablet.NativeEngineIntegrationTest
