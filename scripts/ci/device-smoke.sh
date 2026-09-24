#!/usr/bin/env bash
# Smoke test on a running emulator: install the APK, launch it, prove the
# native core and cfscan load, and leave screenshots + logcat in $OUT.
#
#   scripts/ci/device-smoke.sh <apk> <out-dir>
#
# Fails when the app process dies, when the log shows a crash from it, or
# when the About screen does not report a cfscan version (i.e. the AAR was
# built without it or its native library failed to load).

set -euo pipefail

APK="$1"
OUT="$2"
PKG="com.geekvpn.app"
mkdir -p "$OUT"

adb wait-for-device
# Google APIs images allow root; needed to open non-exported activities.
adb root >/dev/null 2>&1 || true
adb wait-for-device

adb install -r -g "$APK"
adb logcat -c

shot() {
    sleep "${2:-6}"
    adb exec-out screencap -p > "$OUT/$1.png"
    adb exec-out uiautomator dump /dev/tty 2>/dev/null > "$OUT/$1.xml" || true
}

alive() {
    if [[ -z "$(adb shell pidof "$PKG" | tr -d '\r')" ]]; then
        echo "::error::$PKG is not running after $1"
        return 1
    fi
}

adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
shot main 10
alive "launch"

adb shell am start -W -n "$PKG/com.v2ray.ang.ui.AboutActivity" >/dev/null
shot about
alive "opening About"

# Scroll by most of the screen height, whatever the emulator's resolution.
read -r W H < <(adb shell wm size | tr -d '\r' | tail -1 | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
scroll() { adb shell input swipe $((W / 2)) $((H * 8 / 10)) $((W / 2)) $((H * 2 / 10)) 400; }

# Debug builds carry the component catalog; shoot it in both themes.
CATALOG="$PKG/com.geekvpn.ui.catalog.CatalogActivity"
if adb shell cmd package resolve-activity --brief -n "$CATALOG" 2>/dev/null | grep -q catalog \
    || adb shell pm dump "$PKG" | grep -q "com.geekvpn.ui.catalog.CatalogActivity"; then
    adb shell am start -W -n "$CATALOG" --ez dark false >/dev/null
    shot catalog-light
    alive "opening the catalog"
    scroll; shot catalog-light-2 2
    scroll; shot catalog-light-3 2
    adb shell am force-stop "$PKG"
    adb shell am start -W -n "$CATALOG" --ez dark true >/dev/null
    shot catalog-dark
    alive "opening the catalog in dark"
    scroll; shot catalog-dark-2 2
    scroll; shot catalog-dark-3 2
fi

adb logcat -d > "$OUT/logcat.txt"

if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
    echo "::error::crash in $PKG, see logcat.txt"
    grep -A20 "FATAL EXCEPTION" "$OUT/logcat.txt" || true
    exit 1
fi

# About shows "vX (Xray ..., cfscan N.N.N)"; "cfscan Unknown" means the
# scanner is missing from libv2ray.aar.
if ! grep -Eo 'cfscan [0-9]+\.[0-9]+\.[0-9]+' "$OUT/about.xml"; then
    echo "::error::About screen does not show a cfscan version"
    exit 1
fi

echo "Smoke test passed"
