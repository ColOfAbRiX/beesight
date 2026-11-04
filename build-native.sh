#!/bin/bash
# Build a GraalVM native image for Beesight.
# Prerequisites: GraalVM with native-image installed; override its location with GRAALVM_HOME.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$PROJECT_DIR/dist"

export GRAALVM_HOME="${GRAALVM_HOME:-$HOME/graalvm}"
export JAVA_HOME="$GRAALVM_HOME"
export PATH="$GRAALVM_HOME/bin:$PATH"

echo "=== Beesight Native Image Build ==="
echo ""
echo "Java:         $(java --version 2>&1 | head -1)"
echo "native-image: $(native-image --version 2>&1 | head -1)"
echo ""

cd "$PROJECT_DIR"
sbt --no-colors "; set graalVMNativeImageCommand := \"$GRAALVM_HOME/bin/native-image\"; nativeImage"

echo ""
echo "=== Installing to dist/ ==="

BINARY="$(find "$PROJECT_DIR/target" -type f -path '*/graalvm-native-image/beesight' -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2- || true)"
if [ -z "$BINARY" ]; then
  echo "No native image found under target/ — did 'sbt nativeImage' succeed?" >&2
  exit 1
fi

cp "$BINARY" "$DIST_DIR/beesight.bin"
chmod 0755 "$DIST_DIR/beesight.bin"

# Launcher script: 0755
chmod 0755 "$DIST_DIR/beesight"

echo ""
echo "Build complete: $DIST_DIR/beesight"
echo ""
echo "Run with:"
echo "  ./dist/beesight --help"
echo "  ./dist/beesight -i /path/to/flysight/data"
