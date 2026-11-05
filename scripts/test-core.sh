#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_binary="${TMPDIR:-/tmp}/morserunner-core-test-$$"
trap 'rm -f "$test_binary"' EXIT
"${CXX:-g++}" -std=c++17 -O2 -Wall -Wextra -Wpedantic -Werror core/engine.cpp tests/core_test.cpp -o "$test_binary"
"$test_binary" app/src/main/assets/calls/wpx.scp
python3 scripts/verify-data.py
