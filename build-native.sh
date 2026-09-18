#!/bin/bash
# Build a GraalVM native image for Beesight.
# Prerequisites: run 'bash dist/install-graalvm.sh' first.
set -euo pipefail

# FIXME: Fix the scala-native build of the project for CLI

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$PROJECT_DIR/dist"

export JAVA_HOME="$HOME/graalvm"
export GRAALVM_HOME="$HOME/graalvm"
export PATH="$HOME/graalvm/bin:$PATH"

echo "=== Beesight Native Image Build ==="
echo ""
echo "Java:         $(java --version 2>&1 | head -1)"
echo "native-image: $(native-image --version 2>&1 | head -1)"
echo ""

cd "$PROJECT_DIR"
sbt --no-colors nativeImage

echo ""
echo "=== Installing to dist/ ==="

cp target/graalvm-native-image/beesight "$DIST_DIR/beesight.bin"
chmod 0755 "$DIST_DIR/beesight.bin"

# Launcher script: 0755
chmod 0755 "$DIST_DIR/beesight"

echo ""
echo "Build complete: $DIST_DIR/beesight"
echo ""
echo "Run with:"
echo "  ./dist/beesight --help"
echo "  ./dist/beesight -i /path/to/flysight/data"
