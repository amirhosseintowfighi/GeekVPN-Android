#!/usr/bin/env bash
# Smoke test on a running emulator: install the APK, launch it, prove the
# native core and cfscan load, and leave screenshots + logcat in $OUT.
#
#   scripts/ci/device-smoke.sh <apk> <out-dir>
#
# Fails when the app process dies, when the log shows a crash from it, when
# a fresh install does not open the Persian login screen, or when the About
# screen does not report a cfscan version (i.e. the AAR was built without it
# or its native library failed to load).

set -euo pipefail

APK="$1"
OUT="$2"
PKG="com.geekvpn.app"
mkdir -p "$OUT"

adb wait-for-device
# Google APIs images allow root; needed to open non-exported activities.
adb root >/dev/null 2>&1 || true
adb wait-for-device

# The emulator's own launcher ANRs after boot on API 35, and its dialog then
# covers every screen, over and over. Nothing here goes through a launcher
# (every screen is started by component), so switch it off.
for launcher in com.google.android.apps.nexuslauncher com.android.launcher3; do
    adb shell pm disable-user --user 0 "$launcher" >/dev/null 2>&1 || true
done

adb install -r -g "$APK"
adb logcat -c

# The emulator's own launcher sometimes ANRs right after boot, and its
# "isn't responding" dialog then covers whatever we screenshot. Tap "Wait".
dismiss_system_anr() {
    local dump
    dump=$(adb exec-out uiautomator dump /dev/tty 2>/dev/null || true)
    grep -q "isn't responding" <<<"$dump" || return 1
    local bounds
    bounds=$(grep -o 'text="Wait"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' <<<"$dump" \
        | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1)
    if [[ -n "$bounds" ]]; then
        read -r x1 y1 x2 y2 <<<"$(tr '[],' '   ' <<<"$bounds")"
        adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
    else
        adb shell input keyevent KEYCODE_BACK
    fi
    echo "dismissed a system ANR dialog"
    sleep 2
}

shot() {
    sleep "${2:-6}"
    for _ in 1 2 3; do dismiss_system_anr || break; done
    adb exec-out screencap -p > "$OUT/$1.png"
    adb exec-out uiautomator dump /dev/tty 2>/dev/null > "$OUT/$1.xml" || true
}

alive() {
    if [[ -z "$(adb shell pidof "$PKG" | tr -d '\r')" ]]; then
        echo "::error::$PKG is not running after $1"
        # Keep the evidence: the log is otherwise only saved at the end.
        adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
        grep -A30 -E "FATAL EXCEPTION|AndroidRuntime" "$OUT/logcat.txt" | head -80 || true
        return 1
    fi
}

# Centre of the first node whose text is exactly $2, from uiautomator dump $1.
center_of() {
    local bounds
    bounds=$(grep -o "text=\"$2\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$1" \
        | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1) || true
    [[ -n "$bounds" ]] || return 1
    read -r x1 y1 x2 y2 <<<"$(tr '[],' '   ' <<<"$bounds")"
    echo "$(((x1 + x2) / 2)) $(((y1 + y2) / 2))"
}

night() { adb shell cmd uimode night "$1" >/dev/null 2>&1 || true; }

# The launcher entry, the way a launcher starts it: a fresh install has no
# account, so this is the login screen (Main.html).
LAUNCH="$PKG/com.geekvpn.ui.login.LaunchActivity"
launch() {
    adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
        -f 0x10200000 -n "$LAUNCH" >/dev/null
}
night no
launch
shot login 10
alive "launch"

# Exactly one icon on the phone. A debug build once added the catalog as a
# second GeekVPN icon, and a tester opened that instead of the app.
launcher_list=$(adb shell cmd package query-activities --brief -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER 2>/dev/null | tr -d '\r' || true)
launchers=$(grep -c "$PKG/" <<<"$launcher_list" || true)
# Older images may not answer query-activities at all; only a real answer counts.
if [[ -n "$launcher_list" && "$launchers" != "1" ]]; then
    echo "::error::$PKG has $launchers launcher entries, want 1"
    LOGIN_FAILED=1
fi
if ! grep -q "ورود با تلگرام" "$OUT/login.xml"; then
    echo "::error::a fresh install did not open the login screen in Persian"
    LOGIN_FAILED=1
fi
night yes
shot login-dark 4
night no

# "ورود با تلگرام": asks the API for a link and hands it to Telegram (here, a
# browser or nothing). Back in the app it is the waiting screen, or the login
# screen with an error toast when this emulator cannot reach the API.
if xy=$(center_of "$OUT/login.xml" "ورود با تلگرام"); then
    adb shell input tap $xy
    sleep 8
    launch
    shot waiting 4
    night yes
    shot waiting-dark 4
    night no
    alive "starting Telegram sign-in"
fi
adb shell am force-stop "$PKG"

# "شروع سریع بدون ثبت‌نام": guest mode, the real home screen with no account.
launch
sleep 6
adb exec-out uiautomator dump /dev/tty 2>/dev/null > "$OUT/login-again.xml" || true
if xy=$(center_of "$OUT/login-again.xml" "شروع سریع بدون ثبت‌نام"); then
    adb shell input tap $xy
    shot home-guest 6
    alive "entering guest mode"
    if ! grep -q "قطع است" "$OUT/home-guest.xml"; then
        echo "::error::guest mode did not open the home screen"
        LOGIN_FAILED=1
    fi

    # The gateway's way back in: the link opens the shop tab, which for a
    # guest asks them to sign in (no account, so nothing to refresh).
    adb shell am start -W -a android.intent.action.VIEW -d "geekvpn://payment/result?payment=0&result=ok" >/dev/null
    shot payment-return 6
    alive "opening the payment return link"
    if ! grep -q "وارد حسابت شو" "$OUT/payment-return.xml"; then
        echo "::error::the payment return link did not open the shop"
        LOGIN_FAILED=1
    fi
fi
adb shell am force-stop "$PKG"

# The waiting and syncing screens with sample data (debug builds): the real
# flow above only gets there when this emulator can reach the API.
PREVIEW="$PKG/com.geekvpn.ui.catalog.ScreenPreviewActivity"
# Probed by starting it: without an intent-filter it is not in `pm dump`'s
# resolver tables, and release builds do not have it at all.
probe=$(adb shell am start -W -n "$PREVIEW" 2>&1 || true)
if [[ "$probe" != *Error* ]]; then
    adb shell am force-stop "$PKG"
    preview() { # screen, dark
        local name="preview-$1"; [[ "$2" == true ]] && name="$name-dark"
        adb shell am start -W -n "$PREVIEW" --es screen "$1" --ez dark "$2" >/dev/null
        shot "$name" 4
        alive "previewing $1"
        adb shell am force-stop "$PKG"
    }
    for screen in waiting create syncing username home-off home-on services account shop wallet deposit scanner servers route checkout; do
        preview "$screen" false
        preview "$screen" true
    done
    for screen in home-empty shop-guest scanner-running home-finding-ip home-attempt; do
        preview "$screen" false
    done
    # A name the preview activity does not know falls back to the waiting screen.
    for screen in home-finding-ip home-attempt; do
        if ! grep -q "دوباره دکمه را بزن" "$OUT/preview-$screen.xml"; then
            echo "::error::preview $screen did not show the smart connect stage"
            LOGIN_FAILED=1
        fi
    done
fi

# By component: the main screen is behind the login gate.
adb shell am start -W -n "$PKG/com.v2ray.ang.ui.main.MainActivity" >/dev/null
shot main 10
alive "launch"

# A fresh install must start in Persian (AppLocaleManager's default).
adb shell cmd locale get-app-locales "$PKG" > "$OUT/app-locales.txt" 2>&1 || true
if ! grep -q "متصل نیست" "$OUT/main.xml"; then
    echo "::error::fresh install did not start in Persian; app locales: $(cat "$OUT/app-locales.txt")"
    LOCALE_FAILED=1
fi

adb shell am start -W -n "$PKG/com.v2ray.ang.ui.AboutActivity" >/dev/null
shot about
alive "opening About"

# Scroll by most of the screen height, whatever the emulator's resolution.
read -r W H < <(adb shell wm size | tr -d '\r' | tail -1 | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/')
scroll() { adb shell input swipe $((W / 2)) $((H * 8 / 10)) $((W / 2)) $((H * 2 / 10)) 400; }

# Debug builds carry the component catalog (adb only); shoot it in both themes.
CATALOG="$PKG/com.geekvpn.ui.catalog.CatalogActivity"
# Probed by starting it, like the previews: it has no intent-filter.
catalog_probe=$(adb shell am start -W -n "$CATALOG" 2>&1 || true)
adb shell am force-stop "$PKG"
if [[ "$catalog_probe" != *Error* ]]; then
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

# Accessibility (44dp touch targets, a label on everything tappable) on
# GeekVPN's own screens; v2rayNG's screens (main, About) and the component
# catalog are not ours to hold to it.
DPI=$(adb shell wm density | tr -d '\r' | tail -1 | grep -Eo '[0-9]+$')
shopt -s nullglob
A11Y_DUMPS=("$OUT"/login*.xml "$OUT"/home-guest.xml "$OUT"/payment-return.xml "$OUT"/preview-*.xml)
shopt -u nullglob
if ! python3 "$(dirname "$0")/a11y-check.py" "$DPI" "$PKG" "${A11Y_DUMPS[@]}" > "$OUT/a11y.txt"; then
    echo "::error::accessibility problems, see a11y.txt"
    cat "$OUT/a11y.txt"
    A11Y_FAILED=1
fi

if [[ -n "${LOCALE_FAILED:-}" || -n "${LOGIN_FAILED:-}" || -n "${A11Y_FAILED:-}" ]]; then
    exit 1
fi

echo "Smoke test passed"
