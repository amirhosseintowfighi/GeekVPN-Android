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

- `device-smoke`: همان APK `universal` را روی Emulator با API 24، 30 و 35 نصب و
  اجرا می‌کند (`scripts/ci/device-smoke.sh`). اگر اپ crash کند، یا صفحه‌ی «درباره»
  نسخه‌ی `cfscan` را نشان ندهد، job قرمز می‌شود.
- `publish-screenshots`: اسکرین‌شات‌ها، dump صفحه و logcat هر API را در branch
  `ci/screenshots` زیر پوشه‌ای به اسم branch منبع می‌گذارد (`/` به `_` تبدیل
  می‌شود) و با هر اجرا جایگزینشان می‌کند. `RUN.txt` می‌گوید از کدام commit و run آمده‌اند.

امضای release در فاز ۹ اضافه می‌شود.

## ورود و حساب (`com.geekvpn.auth`، `com.geekvpn.account`)

- ورودی لانچر `com.geekvpn.ui.login.LaunchActivity` است، نه `MainActivity`. اگر
  کاربر وارد شده باشد یا «شروع سریع» را زده باشد، مستقیم `HomeActivity` را باز
  می‌کند؛ وگرنه صفحه‌ی ورود (`Main.html`) را نشان می‌دهد.
- ورود با تلگرام: `POST /api/app/auth/link/start` یک لینک `t.me` می‌دهد، اپ تلگرام
  را با `tg://resolve` باز می‌کند و `link/poll` را long-poll می‌کند تا کاربر در ربات
  «تایید و اتصال» را بزند. «ساخت حساب» همین مسیر است، چون ربات حساب را با `/start`
  می‌سازد.
- «نام کاربری»: کاربر نام کاربری و رمز را در ربات می‌سازد (پروفایل ← «ورود به اپ با
  نام کاربری») و اپ `POST /api/app/auth/password` را صدا می‌زند. جواب همان شکل
  `link/poll` تأییدشده را دارد و بقیه‌ی مسیر (ذخیره‌ی توکن، sync) یکی است.
- توکن‌ها با کلید AES-GCM داخل Android Keystore رمز می‌شوند و در یک MMKV جدا زیر
  `files/geek_mmkv` می‌مانند. جدا بودن عمدی است: بکاپ v2rayNG
  (`MMKV.backupAllToDirectory`) هر چه در پوشه‌ی پیش‌فرض MMKV باشد را zip می‌کند.
- فقط پروسه‌ی اصلی اپ API را صدا می‌زند. refresh token با هر استفاده عوض می‌شود و
  بک‌اند استفاده‌ی دوباره را سرقت حساب می‌کند؛ دو پروسه با هم refresh کنند، کاربر
  بیرون می‌افتد.
- هر سرویس حساب یک subscription در v2rayNG می‌شود با GUID `geek-<subscriptionId>`
  (`SubscriptionPlan`). سرویس منقضی غیرفعال می‌شود، سرویس حذف‌شده پاک می‌شود و
  لینک‌هایی که کاربر دستی اضافه کرده دست نمی‌خورند. به‌روزرسانی خودکار خود v2rayNG
  سرورها را تازه نگه می‌دارد. اگر سرور refresh را رد کند (مثلاً دستگاه از ربات
  قطع شده)، سرویس‌های حساب از گوشی پاک می‌شوند و کاربر خارج می‌شود.
- smoke test روی Emulator دکمه‌ی «ورود با تلگرام» را هم می‌زند. API از رانرهای
  GitHub (خارج از ایران) در دسترس نیست و 502 یا خطای شبکه برمی‌گرداند، پس این
  تست فقط ثابت می‌کند که مسیر خطا crash نمی‌کند. صفحه‌ی انتظار و «در حال دریافت
  سرویس‌ها» را `ScreenPreviewActivity` (فقط debug) با داده‌ی نمونه نشان می‌دهد و
  اسکرین‌شاتشان از همان‌جا می‌آید. تست اتصال واقعی به API باید از شبکه‌ی ایران
  باشد (رانر `iran` یا گوشی واقعی).

## صفحه‌های اصلی (`com.geekvpn.ui.home` و بقیه)

- `HomeActivity` چهار تب طراحی را دارد: خانه، سرویس‌ها، فروشگاه (تا فاز ۶ فقط
  راهنما به ربات) و حساب. صفحه‌ی «سرورها» و sheet «مسیر ترافیک» روی همین activity
  باز می‌شوند. صفحه‌های v2rayNG از «حساب ← تنظیمات پیشرفته» (`MainActivity`) و
  «پروفایل‌ها» (`SubSettingActivity`) در دسترس‌اند.
- اتصال فقط از `LauncherManager` شروع و قطع می‌شود و وضعیت daemon از
  `MainRepository` خود v2rayNG می‌آید؛ سرویس VPN دست نخورده است.
  `ConnectionLogic` تصمیم می‌گیرد هر پیام daemon وضعیت را به کجا ببرد.
- «سرور: خودکار» قبل از اتصال همان real-delay test خود v2rayNG را روی سرورهای
  سرویس فعال اجرا می‌کند و سریع‌ترین را انتخاب می‌کند. failover در فاز ۸ است.
- سرعت دانلود و آپلود از `TrafficStats` برای UID خود اپ خوانده می‌شود: سوکت‌های
  هسته مال همین UID است و daemon راهی برای فرستادن آمار به UI ندارد.
- IP خروجی از `check-host.net/ip` می‌آید؛ وقتی وصل است از proxy محلی هسته، وقتی
  قطع است مستقیم (خود اپ از VPN مستثناست). کشور از صفحه‌ی `ip-info` همان سایت
  خوانده می‌شود و اگر پیدا نشد فقط IP نشان داده می‌شود.
- «مسیر»: هوشمند = preset `WHITE_IRAN` خود v2rayNG، سراسری = `GLOBAL`، مستقیم =
  یک ruleset که همه چیز را direct می‌فرستد. قانون‌های قفل‌شده‌ی کاربر می‌مانند.
- `GEEK_BOT_USERNAME` (property یا repository variable در CI، بدون @) لینک
  پشتیبانی، کیف پول و فروشگاه را به ربات وصل می‌کند؛ خالی باشد این دکمه‌ها پنهان‌اند.
- `ScreenPreviewActivity` (فقط debug) همه‌ی تب‌ها را با داده‌ی نمونه نشان می‌دهد
  (`--es screen home-on` و …) تا smoke test از آن‌ها اسکرین‌شات بگیرد.

## رانرها

- همه‌ی بیلدها و Emulatorها روی رانرهای GitHub (`ubuntu-latest`) اجرا می‌شوند.
- رانر self-hosted با برچسب `iran` روی یک سرور داخل ایران است و فقط برای تست‌هایی
  است که باید از شبکه‌ی ایران انجام شوند: اسکن IP تمیز، اتصال واقعی و دسترسی به API.
  روی آن Android SDK و KVM نیست.
- **هیچ workflow ای که روی `[self-hosted, iran]` اجرا می‌شود نباید trigger
  `pull_request` یا `pull_request_target` داشته باشد.** فقط `push` به branchهای
  خود ریپو، `workflow_dispatch` یا `schedule` مجاز است. دلیلش این است که کد یک PR
  از فورک یا Dependabot نباید روی آن سرور اجرا شود.

## برند

`design/` مرجع طراحی است (`png/` اسکرین‌شات‌ها، `screens/` سورس HTML دقیق،
`assets/logo.svg`). آیکن لانچر، بنر TV و آیکن‌های نوتیفیکیشن را این دستور
می‌سازد:

```bash
pip install cairosvg pillow
python3 branding/gen_icons.py
```
