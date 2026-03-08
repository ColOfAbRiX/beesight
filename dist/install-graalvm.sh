#!/bin/bash
set -euo pipefail

GRAALVM_VERSION="23.0.1"
GRAALVM_DIR="$HOME/graalvm"
TARBALL="/tmp/graalvm-ce-${GRAALVM_VERSION}.tar.gz"
URL="https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}/graalvm-community-jdk-${GRAALVM_VERSION}_linux-x64_bin.tar.gz"

echo "=== GraalVM CE ${GRAALVM_VERSION} Installer for WSL Ubuntu ==="

# Check if already installed at correct version
if [ -x "${GRAALVM_DIR}/bin/native-image" ]; then
  INSTALLED_VERSION=$("${GRAALVM_DIR}/bin/native-image" --version 2>&1 | head -1 || true)
  if echo "$INSTALLED_VERSION" | grep -q "${GRAALVM_VERSION}"; then
    echo "GraalVM ${GRAALVM_VERSION} is already installed at ${GRAALVM_DIR}"
    echo "$INSTALLED_VERSION"
    echo ""
    echo "To reinstall, remove ${GRAALVM_DIR} first:"
    echo "  rm -rf ${GRAALVM_DIR}"
    exit 0
  else
    echo "Found older GraalVM: $INSTALLED_VERSION"
    echo "Upgrading to ${GRAALVM_VERSION}..."
    rm -rf "${GRAALVM_DIR}"
  fi
fi

# Install build dependencies needed by native-image
echo "[1/4] Installing build dependencies (gcc, zlib)..."
sudo apt-get update -qq
sudo apt-get install -y -qq gcc zlib1g-dev

# Download GraalVM
if [ -f "${TARBALL}" ]; then
  echo "[2/4] Using cached download at ${TARBALL}"
else
  echo "[2/4] Downloading GraalVM CE ${GRAALVM_VERSION}..."
  curl -sL "${URL}" -o "${TARBALL}"
fi

# Extract
echo "[3/4] Extracting to ${GRAALVM_DIR}..."
mkdir -p "${GRAALVM_DIR}"
tar -xzf "${TARBALL}" -C "${GRAALVM_DIR}" --strip-components=1

# Verify
echo "[4/4] Verifying installation..."
"${GRAALVM_DIR}/bin/java" --version
"${GRAALVM_DIR}/bin/native-image" --version

echo ""
echo "=== Installation complete ==="
echo ""
echo "GraalVM installed at: ${GRAALVM_DIR}"
echo ""
echo "To use it, add to your shell profile (~/.bashrc or ~/.zshrc):"
echo ""
echo '  export JAVA_HOME="$HOME/graalvm"'
echo '  export GRAALVM_HOME="$HOME/graalvm"'
echo '  export PATH="$HOME/graalvm/bin:$PATH"'
echo ""
echo "Then build the native image with:"
echo "  ./build-native.sh"
