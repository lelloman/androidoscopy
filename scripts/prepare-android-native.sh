#!/usr/bin/env bash
set -euo pipefail

if ! command -v rustup >/dev/null 2>&1; then
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal
    export PATH="$HOME/.cargo/bin:$PATH"
fi

rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android

# Some build images (JitPack's "default") ship only the legacy SDK tools, whose
# sdkmanager needs JAXB and cannot run on the JDK 17 that the build requires.
# When the sdkmanager on PATH does not run, use pinned, verified cmdline-tools.
CMDLINE_TOOLS_ZIP=commandlinetools-linux-16111833_latest.zip
CMDLINE_TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8

sdkmanager=(sdkmanager)
if ! sdkmanager --version >/dev/null 2>&1; then
    sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -z "$sdk_root" ]] && command -v sdkmanager >/dev/null 2>&1; then
        # The legacy tool lives at <sdk>/tools/bin/sdkmanager.
        sdk_root=$(cd "$(dirname "$(readlink -f "$(command -v sdkmanager)")")/../.." && pwd)
    fi
    [[ -n "$sdk_root" ]] || { echo "Cannot locate the Android SDK; set ANDROID_HOME" >&2; exit 1; }
    tools=$(mktemp -d)
    curl --proto '=https' --tlsv1.2 -fsSLo "$tools/tools.zip" \
        "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
    echo "$CMDLINE_TOOLS_SHA256  $tools/tools.zip" | sha256sum -c -
    unzip -q "$tools/tools.zip" -d "$tools"
    sdkmanager=("$tools/cmdline-tools/bin/sdkmanager" "--sdk_root=$sdk_root")
fi

# Answer any licence prompt that this machine has not accepted yet.
(yes || true) | "${sdkmanager[@]}" --install 'ndk;27.0.12077973'
