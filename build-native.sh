#!/bin/bash
# Build a GraalVM native image for Beesight.
# Prerequisites: GraalVM with native-image installed; override its location with GRAALVM_HOME.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$PROJECT_DIR/dist"

if [ -n "${GRAALVM_HOME:-}" ]; then
  if [ ! -x "$GRAALVM_HOME/bin/native-image" ]; then
    echo "error: GRAALVM_HOME=$GRAALVM_HOME has no executable bin/native-image" >&2
    echo "Point GRAALVM_HOME at a Linux GraalVM installation." >&2
    echo "A Windows-side GraalVM (e.g. under /mnt/c) cannot build a Linux image from WSL." >&2
    exit 1
  fi
  RESOLVED="$GRAALVM_HOME"
else
  RESOLVED=""
  for candidate in "$HOME/graalvm" /home/*/graalvm; do
    if [ -x "$candidate/bin/native-image" ]; then
      RESOLVED="$candidate"
      break
    fi
  done
  if [ -z "$RESOLVED" ]; then
    echo "error: no Linux GraalVM with an executable bin/native-image found" >&2
    echo "  probed: \$HOME/graalvm ($HOME/graalvm) and /home/*/graalvm" >&2
    echo "Set one explicitly: GRAALVM_HOME=/path/to/graalvm bash build-native.sh" >&2
    exit 1
  fi
fi

export GRAALVM_HOME="$RESOLVED"
export JAVA_HOME="$GRAALVM_HOME"
export PATH="$GRAALVM_HOME/bin:$PATH"

echo "=== Beesight Native Image Build ==="
echo ""
echo "GraalVM:      $GRAALVM_HOME"
echo "Java:         $(java --version 2>&1 | head -1)"
if ! NATIVE_IMAGE_VERSION="$(native-image --version 2>&1)"; then
  echo "error: failed to run $GRAALVM_HOME/bin/native-image" >&2
  exit 1
fi
echo "native-image: ${NATIVE_IMAGE_VERSION%%$'\n'*}"
echo ""

cd "$PROJECT_DIR"
sbt --no-colors "; test; set graalVMNativeImageCommand := \"$GRAALVM_HOME/bin/native-image\"; nativeImage"

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
