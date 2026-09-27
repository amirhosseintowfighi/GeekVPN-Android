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

# GeekVPN's patches to the Xray core (scripts/xray-patches/README.md): the
# module at the version go.mod pins is copied, patched, tested and swapped in.
XRAY_MODULE="github.com/xtls/xray-core"
XRAY_SRC="$(go mod download -json "$XRAY_MODULE" | jq -r .Dir)"
cp -a "$XRAY_SRC" "$WORK/xray-core"
chmod -R u+w "$WORK/xray-core"
for p in "$ROOT"/scripts/xray-patches/*.patch; do
    patch -d "$WORK/xray-core" -p1 --forward < "$p"
done
cp "$ROOT"/scripts/xray-patches/*_test.go "$WORK/xray-core/infra/conf/"
(cd "$WORK/xray-core" && go test ./infra/conf/ -run 'TestGeekVPN')
go mod edit -replace="$XRAY_MODULE=./xray-core"

# The geo files ship inside the AAR; v2rayNG's routing fails to build without
# any of them (geoip-only-cn-private.dat backs every geoip:private rule), and
# gen_assets.sh's curl does not fail on an HTTP error.
for dat in geosite.dat geoip.dat geoip-only-cn-private.dat; do
    if [[ ! -s "assets/$dat" ]] || [[ "$(stat -c %s "assets/$dat")" -lt 10000 ]]; then
        echo "assets/$dat is missing or too small" >&2
        exit 1
    fi
done

# cfscan joins the core's module graph through a local replace, so both
# packages resolve against one set of dependency versions.
go mod edit -replace="$CFSCAN_MODULE=$ROOT/cfscan"
go get "$CFSCAN_MODULE@v0.0.0"

gomobile init
mkdir -p "$(dirname "$OUT")"
gomobile bind -v -target=android -androidapi 24 -trimpath \
    -ldflags='-s -w -buildid= -checklinkname=0' \
    -o "$OUT" ./ "$CFSCAN_MODULE"

for dat in geosite.dat geoip.dat geoip-only-cn-private.dat; do
    unzip -l "$OUT" "assets/$dat" >/dev/null || { echo "$OUT lacks assets/$dat" >&2; exit 1; }
done

# The app puts every *.jar in libs/ on its classpath; the sources jar is not code.
rm -f "${OUT%.aar}-sources.jar"

echo "Built $OUT"
