#!/usr/bin/env bash
set -euo pipefail

if ! command -v rustup >/dev/null 2>&1; then
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal
    export PATH="$HOME/.cargo/bin:$PATH"
fi

rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
sdkmanager --install 'ndk;27.0.12077973'
"$(dirname "$0")/checkout-simple-server.sh"
