#!/usr/bin/env bash
# Build V2rayNG/app/libs/libv2ray.aar from source: the AndroidLibXrayLite
# submodule (Xray core) and ./cfscan, bound together by ONE gomobile bind.
#
# Why one bind: every gomobile AAR embeds its own Go runtime and its own go.*
# Java classes. Two of them in one APK clash at class-load time, so the scanner
# cannot ship as a second AAR. See docs/geekvpn.md.
#
# Requirements: Go (the toolchain named in AndroidLibXrayLite/go.mod is fetched
# automatically), gomobile + gobind on PATH, jq, curl, and ANDROID_HOME plus
# ANDROID_NDK_HOME pointing at an installed SDK and NDK.
#
# The submodule checkout is never modified: the build runs in a scratch copy.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${OUT:-$ROOT/V2rayNG/app/libs/libv2ray.aar}"
CFSCAN_MODULE="github.com/amirhosseintowfighi/geekvpn-android/cfscan"

: "${ANDROID_HOME:?ANDROID_HOME must point at the Android SDK}"
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must point at the Android NDK}"
command -v gomobile >/dev/null || { echo "gomobile is not on PATH" >&2; exit 1; }

if [[ ! -f "$ROOT/AndroidLibXrayLite/go.mod" ]]; then
    echo "AndroidLibXrayLite is empty; run: git submodule update --init --recursive" >&2
    exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cp -a "$ROOT/AndroidLibXrayLite/." "$WORK/"
rm -rf "$WORK/.git"
cd "$WORK"

# Same geo data step as upstream AndroidLibXrayLite's release workflow.
mkdir -p assets data
bash gen_assets.sh download
cp -v data/*.dat assets/

# Upstream's own step, run before cfscan is added: tidy would otherwise drop
# cfscan again, because no package of the core module imports it.
go mod tidy

# cfscan joins the core's module graph through a local replace, so both
# packages resolve against one set of dependency versions.
go mod edit -replace="$CFSCAN_MODULE=$ROOT/cfscan"
go get "$CFSCAN_MODULE@v0.0.0"

gomobile init
mkdir -p "$(dirname "$OUT")"
gomobile bind -v -target=android -androidapi 24 -trimpath \
    -ldflags='-s -w -buildid= -checklinkname=0' \
    -o "$OUT" ./ "$CFSCAN_MODULE"

# The app puts every *.jar in libs/ on its classpath; the sources jar is not code.
rm -f "${OUT%.aar}-sources.jar"

echo "Built $OUT"
