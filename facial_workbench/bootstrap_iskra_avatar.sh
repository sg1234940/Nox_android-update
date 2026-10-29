#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [ ! -f base_basic_pbr.glb ]; then
  echo "BLOCKED: skopiuj oryginalny base_basic_pbr.glb do: $ROOT/base_basic_pbr.glb" >&2
  exit 2
fi

actual_size=$(wc -c < base_basic_pbr.glb | tr -d ' ')
expected_size=21538764
if [ "$actual_size" != "$expected_size" ]; then
  echo "BLOCKED: base_basic_pbr.glb ma $actual_size bajtow, oczekiwano $expected_size" >&2
  exit 3
fi

python3 build_iskra_facial_rig.py
python3 test_iskra_facial_rig.py | tee TEST_RESULTS.txt
sha256sum base_basic_pbr.glb iskra_avatar_facial_rig.glb > SHA256SUMS.txt

echo
echo "Wynik: $ROOT/iskra_avatar_facial_rig.glb"
echo "Testy: $ROOT/TEST_RESULTS.txt"
echo "UWAGA: test Blender/Android pozostaje NIE SPRAWDZONY, dopoki nie zostanie uruchomiony osobno."
