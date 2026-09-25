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
| `core/CoreConfigContextBuilder.kt` | یک خط: پروفایل قبل از ساخت کانفیگ از `IpOverrides.apply` رد می‌شود (اسکنر) |
| `service/RealPingWorkerService.kt` | یک خط: پیش‌تست TCP هم به IP تمیز (override) می‌رود، نه آدرس خود کانفیگ |
| `core/CoreServiceManager.kt` | `FailoverMonitor` بعد از شروع هسته ساخته و قبل از توقفش متوقف می‌شود (`startFailoverMonitor`) |

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
- `app`: `./gradlew lint test assembleDebug assembleRelease` را اجرا می‌کند و APKهای
  debug را آپلود می‌کند: `arm64-v8a`، `armeabi-v7a` و `universal`. اگر کلید
  release تنظیم شده باشد، APKهای امضاشده‌ی `prod` و `mapping.txt` هم زیر اسم
  `release` آپلود می‌شوند (بخش «release» پایین را ببین).

- `device-smoke`: روی Emulator با API 24، 30، 35 و 37 اول APK `universal` debug و
  بعد همان APK release (با R8) را نصب و اجرا می‌کند (`scripts/ci/device-smoke.sh`).
  نتیجه‌ی release در پوشه‌ی `apiNN-release` است. API 37 فعلاً غیرمسدودکننده است:
  image آن روی رانرهای GitHub در لایه‌ی گرافیک emulator (gfxstream) crash می‌کند و
  system_server را پایین می‌آورد، پس با `-gpu guest` اجرا می‌شود و قرمز شدنش کل
  action را قرمز نمی‌کند. job قرمز می‌شود اگر:
  - اپ crash کند؛
  - صفحه‌ی «درباره» نسخه‌ی `cfscan` را نشان ندهد؛
  - `scripts/ci/a11y-check.py` روی صفحه‌های GeekVPN چیزی پیدا کند: هر چیز
    قابل لمس باید حداقل ۴۴dp باشد و متن یا contentDescription داشته باشد.
    گزارشش `a11y.txt` است.
- `publish-screenshots`: اسکرین‌شات‌ها، dump صفحه و logcat هر API را در branch
  `ci/screenshots` زیر پوشه‌ای به اسم branch منبع می‌گذارد (`/` به `_` تبدیل
  می‌شود) و با هر اجرا جایگزینشان می‌کند. `RUN.txt` می‌گوید از کدام commit و run آمده‌اند.

## release

- build release با R8 کوچک و obfuscate می‌شود (`isMinifyEnabled`،
  `isShrinkResources`). قانون‌ها در `V2rayNG/app/proguard-rules.pro` هستند. کد
  v2rayNG، bindingهای gomobile (`go.*`، `libv2ray.*`، `cfscan.*`) و MMKV کامل نگه
  داشته می‌شوند. از کد GeekVPN فقط فیلدها و constructorها می‌مانند، چون همه‌ی
  مدل‌های API و MMKV با Gson خوانده می‌شوند و اسم فیلدها همان فرمت ذخیره است.
- `versionCode` شماره‌ی run در CI است و `versionName` اسم tag (بدون `v`).
- کلید امضا فقط از GitHub Secrets می‌آید، هیچ‌وقت از ریپو:

  | Secret | مقدار |
  |---|---|
  | `GEEK_RELEASE_KEYSTORE` | فایل `.jks` به base64 (`base64 -w0 release.jks`) |
  | `GEEK_RELEASE_STORE_PASSWORD` | رمز keystore |
  | `GEEK_RELEASE_KEY_ALIAS` | alias کلید |
  | `GEEK_RELEASE_KEY_PASSWORD` | رمز کلید |

  ساخت کلید (یک بار، روی سیستم خودت؛ فایل و رمزها را جای امن نگه دار، بدون آن‌ها
  آپدیت اپ ممکن نیست):

  ```bash
  keytool -genkeypair -v -keystore release.jks -alias geekvpn \
    -keyalg RSA -keysize 4096 -validity 10000
  ```

  برای بیلد محلی همین چهار اسم در `V2rayNG/local.properties` می‌آیند، با
  `GEEK_RELEASE_STORE_FILE=<مسیر فایل>` به‌جای base64.
- بدون کلید، release با کلید debug امضا می‌شود تا CI بتواند نسخه‌ی R8 را روی
  Emulator تست کند. این APK آپلود یا منتشر نمی‌شود.
- انتشار: یک tag `v*` push کن (مثلاً `git tag v1.0.0 && git push origin v1.0.0`).
  اگر کلید تنظیم شده باشد و همه‌ی تست‌ها سبز باشند، job `publish-release` یک
  GitHub Release با APKهای امضاشده‌ی `prod` می‌سازد.

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

- `HomeActivity` چهار تب طراحی را دارد: خانه، سرویس‌ها، فروشگاه و حساب. صفحه‌ی «سرورها» و sheet «مسیر ترافیک» روی همین activity
  باز می‌شوند.
- «حساب ← تنظیمات پیشرفته» دیگر خود v2rayNG (`MainActivity`) را باز نمی‌کند:
  `com.geekvpn.ui.advanced.AdvancedActivity` همان تنظیمات `SettingsActivity` v2rayNG
  را با طراحی GeekVPN نشان می‌دهد. کلیدهای MMKV، پیش‌فرض‌ها، شرط‌های فعال بودن و
  وابستگی‌ها (`AdvancedSettings`) عین v2rayNG است. گزینه‌های مخصوص ظاهر خود v2rayNG
  (چیدمان لیست، تم و رنگ پویا) حذف شده‌اند. بعد از merge با upstream، اگر
  `SettingsActivity` گزینه‌ی جدیدی گرفت، باید به `AdvancedSettings.sections` هم اضافه
  شود. هر تغییر `SettingsChangeManager` را خبر می‌کند و `HomeActivity` در `onStart`
  اتصال در حال اجرا را دوباره می‌سازد.
- بقیه‌ی صفحه‌هایی که قبلاً از v2rayNG باز می‌شدند هم طراحی GeekVPN دارند و منطق
  خود v2rayNG را صدا می‌زنند: «تونل تفکیکی برنامه‌ها» (`ui.perapp.PerAppActivity` روی
  `PerAppProxyViewModel`)، افزودن و ویرایش لینک اشتراک (`ui.links.LinkEditActivity`
  با همان بررسی‌های `SubEditActivity`؛ لینک جدید بلافاصله سرورهایش را می‌گیرد) و
  «درباره» (`ui.about.AboutActivity`، با نسخه‌ی هسته و cfscan و attribution
  پروژه‌های متن‌باز). ردیف «پروفایل‌ها» حذف شد؛ لینک‌های دستی در تب «سرویس‌ها» هستند
  و هر کدام دکمه‌ی ویرایش دارد. هیچ مسیری از UI به صفحه‌های خود v2rayNG نمی‌رسد.
- کاری که صفحه‌ی اصلی v2rayNG موقع باز شدن می‌کند، اینجا `HomeViewModel` انجام
  می‌دهد و هر اتصال منتظرش می‌ماند: کپی `geosite.dat` و `geoip.dat` (بدون آن‌ها Xray
  قانون‌های `geosite:ir` مسیر هوشمند را نمی‌سازد و بالا نمی‌آید) و زمان‌بندی
  به‌روزرسانی خودکار اشتراک‌ها.
- اتصال فقط از `LauncherManager` شروع و قطع می‌شود و وضعیت daemon از
  `MainRepository` خود v2rayNG می‌آید؛ سرویس VPN دست نخورده است.
  `ConnectionLogic` تصمیم می‌گیرد هر پیام daemon وضعیت را به کجا ببرد.
- «سرور: خودکار» یعنی اتصال هوشمند و failover (بخش پایین).
- سرعت دانلود و آپلود از `TrafficStats` برای UID خود اپ خوانده می‌شود: سوکت‌های
  هسته مال همین UID است و daemon راهی برای فرستادن آمار به UI ندارد.
- IP خروجی از `check-host.net/ip` می‌آید؛ وقتی وصل است از proxy محلی هسته، وقتی
  قطع است مستقیم (خود اپ از VPN مستثناست). کشور از صفحه‌ی `ip-info` همان سایت
  خوانده می‌شود و اگر پیدا نشد فقط IP نشان داده می‌شود.
- «مسیر»: هوشمند = preset `WHITE_IRAN` خود v2rayNG، سراسری = `GLOBAL`، مستقیم =
  یک ruleset که همه چیز را direct می‌فرستد. قانون‌های قفل‌شده‌ی کاربر می‌مانند.
- `GEEK_BOT_USERNAME` (property یا repository variable در CI، بدون @) لینک
  پشتیبانی را به ربات وصل می‌کند؛ خالی باشد این ردیف پنهان است.
- `ScreenPreviewActivity` (فقط debug) همه‌ی تب‌ها را با داده‌ی نمونه نشان می‌دهد
  (`--es screen home-on` و …) تا smoke test از آن‌ها اسکرین‌شات بگیرد.

## فروشگاه و کیف پول (`com.geekvpn.shop`، `com.geekvpn.ui.shop`)

- همه‌چیز از API مینی‌اپ می‌آید (`/api/miniapp/*`، با توکن اپ): `storefront`،
  `quote`، `coupon/preview`، `payment-methods`، `checkout/wallet|card|gateway`،
  `wallet/topup`، `wallet/transactions`، `payments/pending`. قیمت نهایی را فقط
  `quote` می‌گوید؛ اپ حساب نمی‌کند.
- سوییچ بالای کارت نوع سرویس است (مستقیم / تونل / ویژه). برای هر نوع، محصول
  featured آن نوع نشان داده می‌شود و «مدت» و «حجم» از پلن‌های همان محصول
  ساخته می‌شوند (`ShopCatalog`). حجم دلخواه نداریم، چون فروشگاه محصول ثابت دارد.
- «پرداخت» یک sheet روش پرداخت باز می‌کند: کیف پول، کارت به کارت و هر درگاهی
  که سرور برگرداند. کیف پول مستقیم سرویس را می‌سازد و تب «سرویس‌ها» باز می‌شود.
  «تمدید» روی کارت سرویس، خرید بعدی را `renewsSubscriptionId` همان سرویس می‌کند.
- کارت به کارت: کارت مقصد و مبلغ دقیق (با رقم‌های شناسایی) نشان داده می‌شود.
  «واریز کردم» photo picker سیستم را باز می‌کند (بدون مجوز). عکس تا ۱۶۰۰ پیکسل
  کوچک و JPEG می‌شود و به `payments/{id}/receipt-photo` می‌رود. بک‌اند آن را در چت
  ربات می‌فرستد تا ادمین مثل رسید ربات بررسی‌اش کند.
- درگاه آنلاین در Custom Tab باز می‌شود. صفحه‌ی برگشت بک‌اند، برای پرداختی که از
  اپ شروع شده، `geekvpn://payment/result?result=…` را باز می‌کند.
  `PaymentReturnActivity` (exported، بدون UI) فقط یک کلمه‌ی شناخته‌شده را به
  `HomeActivity` می‌دهد. برگشت با دکمه‌ی back هم همان refresh را اجرا می‌کند.
  نتیجه‌ی واقعی را همیشه سرور می‌گوید.
- تست رایگان (`/api/miniapp/trial`) فقط برای کاربر واردشده است و یک بار برای هر
  حساب داده می‌شود: ۵۰ مگابایت و ۲ روز، یک سرویس تونل و یک سرویس مستقیم.
- مهمان در فروشگاه فقط دکمه‌ی ورود را می‌بیند.

## اسکنر IP تمیز کلادفلر (`cfscan/`، `com.geekvpn.scanner`)

- موتور در Go است (`cfscan/`) و منطقش از cf-scanner (MIT) آمده است: ping،
  TCP، uTLS با fingerprint کروم، درخواست `GET /cdn-cgi/trace` با SNI و Host
  دامنه‌ی خود کانفیگ (جواب باید ۲۰۰ و `Server: cloudflare` باشد، `colo` از همین
  جواب خوانده می‌شود)، jitter و تست دانلود اختیاری. API برای gomobile ساخته شده
  است: `NewScanner()`، `Start(configJSON, ranges, Listener)`، `Stop()`.
- هر اسکن حداکثر ۳۰۰ IP را امتحان می‌کند که به‌صورت تصادفی و به نسبت اندازه‌ی
  رنج‌ها از `assets/cfscan/ipv4.txt` انتخاب می‌شوند. با پیدا شدن ۵ IP سالم تمام
  می‌شود. IPهایی که اسکن قبلی پیدا کرده اول دوباره تست می‌شوند.
- اگر ping روی دستگاه اجرا نشود، یا بعد از ۳۰ بار هیچ جوابی نیاید، ping برای بقیه‌ی
  اسکن کنار گذاشته می‌شود و فقط TCP و TLS می‌ماند.
- `protect()` لازم نیست: `CoreVpnService` همیشه پکیج خود اپ را از VPN بیرون
  می‌گذارد، پس سوکت‌های اسکنر مستقیم روی شبکه‌ی گوشی می‌روند، چه VPN وصل باشد
  چه نباشد.
- اسکن در یک foreground service کوتاه (`ScanService`، نوع `dataSync`) در پروسه‌ی
  اصلی اجرا می‌شود و از نوتیفیکیشن قابل توقف است. WorkManager خود v2rayNG در
  پروسه‌ی `:bg` است و پیشرفت اسکن را به صفحه نمی‌رساند.
- کارت و گزینه‌ی اسکنر فقط برای سرویس `direct` حساب یا لینک دستی نشان داده
  می‌شود، آن هم وقتی کانفیگ پشت CDN باشد (`CdnTarget`): transport یکی از ws،
  grpc، xhttp یا httpupgrade، امنیت TLS، و SNI و Host یک دامنه.
- شکل کانفیگ کافی نیست: قبل از هر اسکن و هر override، دامنه‌های SNI و Host resolve
  می‌شوند و باید در رنج‌های کلادفلر (`ipv4.txt`) باشند (`CloudflareCheck`). جواب
  `/cdn-cgi/trace` روی هر IP کلادفلر برای هر دامنه‌ای می‌آید، پس خود اسکن نمی‌تواند
  تشخیص بدهد. نتیجه در `GEEK_SCAN` ذخیره می‌شود و `IpOverrides.apply` فقط برای دامنه‌ی
  تأییدشده override را اعمال می‌کند. اگر DNS جواب خصوصی بدهد (دامنه‌ی فیلترشده)،
  حکمی ثبت نمی‌شود. اسکنی که خود کاربر زده و IP پیدا کرده، دامنه را تأییدشده حساب می‌کند.
  اتصال هوشمند فقط برای دامنه‌ی تأییدشده خودکار اسکن می‌کند.
- بهترین IP خودکار اعمال می‌شود، ولی نه با عوض کردن کانفیگ: یک override برای هر
  کانفیگ و هر شبکه در MMKV (`GEEK_SCAN`) ذخیره می‌شود. کلید کانفیگ از subscription،
  نام، آدرس و پورت اصلی ساخته می‌شود تا بعد از refresh اشتراک هم بماند. در
  `CoreConfigContextBuilder` آدرس با IP عوض می‌شود و SNI و Host دامنه می‌مانند. این
  برای اتصال و real-delay test یکسان است. «بازگشت به IP اصلی» override را پاک
  می‌کند. اگر وصل باشی، اتصال دوباره ساخته می‌شود.
- شبکه: موبایل با MCC+MNC (بدون مجوز). Wi-Fi با DNS و domain که DHCP داده است،
  چون SSID مجوز location می‌خواهد. نتیجه‌ها بعد از ۲۴ ساعت کهنه حساب می‌شوند.
- `CFSCAN_LIVE=1 go test ./...` (در CI روشن است) یک اسکن واقعی روی edge
  کلادفلر از رانر انجام می‌دهد.

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

## اتصال هوشمند و failover (`com.geekvpn.smartconnect`)

- کل منطق در `SmartConnectUseCase` است و از UI و سرویس VPN جداست. هر چیزی که
  لازم دارد از `SmartConnectPorts` می‌گیرد، پس با unit test پوشش داده می‌شود.
- دکمه‌ی اتصال با «سرور: خودکار» این کارها را انجام می‌دهد:
  1. برای کانفیگ‌هایی که اسکنر رویشان کار می‌کند، IPهای تمیز تازه‌ی همین شبکه را
     برمی‌دارد. اگر IP تازه‌ای نباشد، یک اسکن کوتاه ۲۰ ثانیه‌ای اجرا می‌کند
     (`ScanController.quickScan`، همان `ScanService`).
  2. real-delay test خود v2rayNG را اجرا می‌کند (`CoreTestService`، همروندی از
     `PREF_REAL_PING_CONCURRENCY`). دور اول همه‌ی کانفیگ‌ها با آدرس خودشان تست
     می‌شوند، بعد برای هر IP تمیز یک دور، حداکثر ۳ IP. override برای هر کانفیگ است،
     پس هر دور فقط با یک IP تست می‌شود. IP تمیز فقط جایی می‌ماند که از آدرس خود
     کانفیگ بهتر جواب داده باشد.
  3. با بهترین گزینه وصل می‌شود. اگر هسته بالا نیاید یا تست تأخیر خود v2rayNG
     روی اتصال زنده (`MSG_MEASURE_DELAY`) جواب ندهد، گزینه‌ی بعدی را امتحان
     می‌کند. حداکثر ۳ تلاش انجام می‌شود و بعد پیام فارسی نشان داده می‌شود.
- هر مرحله روی Home نوشته می‌شود و با زدن دوباره‌ی دکمه لغو می‌شود. لغو، اسکن و
  تست را متوقف می‌کند و اگر اتصالی شروع شده بود آن را قطع می‌کند.
- با «سرور: خودکار» خاموش، همان سرور انتخاب‌شده بدون تست وصل می‌شود.
- failover در پروسه‌ی VPN (`:daemon`) اجرا می‌شود، چون پروسه‌ی اپ ممکن است وقتی
  VPN وصل است بسته شده باشد. عمرش دقیقاً با هسته یکی است. هر ۳۰ ثانیه (با صفحه‌ی
  خاموش هر ۲ دقیقه) تأخیر اتصال زنده را از خود هسته می‌پرسد. `FailoverPolicy`
  تصمیم می‌گیرد: دو بار پشت سر هم بد، یعنی failover. «بد» یعنی خطا، یا کندتر از
  آستانه. بعد از هر failover یک cooldown می‌آید که از ۲ دقیقه شروع می‌شود و تا
  ۳۰ دقیقه دو برابر می‌شود.
- موقع failover، سرورهای همان سرویس با `RealPingWorkerService` در همان پروسه
  تست می‌شوند و IPهای تمیز تازه هم امتحان می‌شوند، ولی اسکن جدید انجام نمی‌شود.
  بعد هسته با `reloadCore` روی سرور جدید دوباره ساخته می‌شود؛ تونل پایین نمی‌آید.
  اگر هیچ سروری جواب ندهد، مشکل از شبکه‌ی گوشی حساب می‌شود و سرور عوض نمی‌شود.
- آستانه در صفحه‌ی «سرورها» زیر «سرور: خودکار» تنظیم می‌شود: هرگز، فقط قطعی، ۱،
  ۲ (پیش‌فرض) یا ۳ ثانیه. در `ConnectionPrefs` ذخیره می‌شود؛ MMKV آن
  multi-process است و پروسه‌ی VPN هم آن را می‌خواند.
