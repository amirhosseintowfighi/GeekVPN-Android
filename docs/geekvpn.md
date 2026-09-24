# GeekVPN-Android: راهنمای فورک

این ریپو فورک [v2rayNG](https://github.com/2dust/v2rayNG) است. قوانین upstream
(`AGENTS.md`) سر جایشان هستند. این فایل فقط چیزهایی را می‌گوید که GeekVPN اضافه
یا عوض کرده است.

## اصل کار

- هسته‌ی شبکه‌ی v2rayNG (`service/`، `fmt/`، `core/`) تا جای ممکن دست نمی‌خورد.
  کد GeekVPN زیر `V2rayNG/app/src/main/java/com/geekvpn/` زندگی می‌کند و از
  بالای کد v2rayNG صدایش می‌زند.
- `namespace` همان `com.v2ray.ang` مانده است (کلاس `R` و JNI کتابخانه‌ی hev به
  آن بسته‌اند). فقط `applicationId` عوض شده است: `com.geekvpn.app`
  (و `com.geekvpn.app.staging`).
- لایسنس GPL-3.0 است، مثل upstream.

## sync با upstream

```bash
git remote add upstream https://github.com/2dust/v2rayNG.git   # یک بار
git fetch upstream master
git merge upstream/master          # merge، نه rebase: تاریخچه‌ی upstream دست‌نخورده می‌ماند
git submodule update --init --recursive
```

جاهایی که conflict محتمل است، چون عمداً عوض شده‌اند:

| فایل | تغییر GeekVPN |
|---|---|
| `V2rayNG/app/build.gradle.kts` | applicationId، نسخه، flavorهای `staging`/`prod`، غیرفعال‌کردن F-Droid، `ndkVersion` از property |
| `.github/workflows/build.yml` | کاملاً بازنویسی شده (پایین را ببین) |
| `res/values*/strings.xml` | `app_name` و چند برچسب برند |
| آیکن‌ها (`mipmap-*`، `drawable-*dpi/ic_stat_*`) | خروجی `branding/gen_icons.py`؛ بعد از merge دوباره اجرایش کن |
| `res/xml/shortcuts.xml` | `targetPackage` |

بعد از merge، اگر `AndroidLibXrayLite` جلو رفته باشد، CI خودش AAR را دوباره می‌سازد
(کلید cache به gitlink submodule بسته است).

## flavorها و تنظیمات

دو بُعد flavor داریم: `distribution` (فقط `playstore`؛ `fdroid` تعریف شده ولی
variantهایش ساخته نمی‌شوند) و `env` (`staging`، `prod`). variantهای واقعی:
`playstoreStagingDebug`، `playstoreProdDebug` و نسخه‌های release آن‌ها.

آدرس API در `BuildConfig.API_BASE` است و از یکی از این‌ها خوانده می‌شود، به همین
ترتیب: `-P`، متغیر محیطی، یا `V2rayNG/local.properties`:

```properties
GEEK_API_BASE_STAGING=https://...
GEEK_API_BASE_PROD=https://...
```

اگر مقدار نباشد، یک host با پسوند `.invalid` جایش می‌نشیند. آدرسی که `https://`
نباشد بیلد را می‌شکند. هیچ آدرس واقعی در ریپو commit نمی‌شود. در CI همین دو اسم
GitHub Secret هستند.

## `libv2ray.aar` چطور ساخته می‌شود

upstream این AAR را از release‌های `2dust/AndroidLibXrayLite` دانلود می‌کند. ما
از سورس می‌سازیمش، چون اسکنر IP کلادفلر (`cfscan/`، Go) باید داخل همان AAR باشد:
هر AAR ساخته‌شده با gomobile runtime Go و کلاس‌های `go.*` خودش را دارد و دو تا از
آن‌ها در یک APK با هم تداخل می‌کنند.

`scripts/build-libv2ray.sh`:

1. یک کپی موقت از submodule `AndroidLibXrayLite` می‌گیرد (خود submodule دست نمی‌خورد).
2. داده‌های geo را مثل workflow خود upstream دانلود می‌کند (`gen_assets.sh`).
3. `go mod tidy` را اجرا می‌کند. بعد ماژول `cfscan` را با یک `replace` محلی به
   گراف ماژول هسته اضافه می‌کند تا هر دو با یک نسخه از وابستگی‌ها ساخته شوند.
4. `gomobile bind -androidapi 24 ./ <cfscan>` را اجرا می‌کند و خروجی را در
   `V2rayNG/app/libs/libv2ray.aar` می‌گذارد.

در کاتلین، هسته `libv2ray.Libv2ray` است و اسکنر `cfscan.Cfscan`. فقط
`com.geekvpn.scanner.CfScanNative` به `cfscan.*` دست می‌زند.

برای ساخت محلی به Go، `gomobile` و `gobind` با همان نسخه‌ی `golang.org/x/mobile`
که در `AndroidLibXrayLite/go.mod` آمده، `jq`، و متغیرهای `ANDROID_HOME` و
`ANDROID_NDK_HOME` نیاز داری. بعدش `libhevtun` را با `compile-hevtun.sh` بساز و
`libs/` را در `V2rayNG/app/` کپی کن، مثل CI.

## CI

`.github/workflows/build.yml` روی هر push اجرا می‌شود:

- `libv2ray`: AAR را می‌سازد (یا از cache برمی‌دارد) و تست‌های `cfscan` را اجرا می‌کند.
- `app`: `./gradlew lint test assembleDebug` را اجرا می‌کند و APKها را آپلود
  می‌کند: `arm64-v8a`، `armeabi-v7a` و `universal`.

امضای release در فاز ۹ اضافه می‌شود.

## برند

`design/` مرجع طراحی است (`png/` اسکرین‌شات‌ها، `screens/` سورس HTML دقیق،
`assets/logo.svg`). آیکن لانچر، بنر TV و آیکن‌های نوتیفیکیشن را این دستور
می‌سازد:

```bash
pip install cairosvg pillow
python3 branding/gen_icons.py
```
