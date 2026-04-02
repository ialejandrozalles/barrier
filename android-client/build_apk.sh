#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

echo "[Barrier Android Client] Building debug APK..."
gradle assembleDebug

echo "APK generated at:"
echo "$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
