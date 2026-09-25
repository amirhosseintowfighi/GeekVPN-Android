import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.jaredsburrows.license")
}

// GeekVPN: deployment values come from -P, the environment or local.properties,
// never from this file, so no endpoint is committed. See docs/geekvpn.md.
val geekLocalProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Blank counts as unset: CI passes an unset secret through as an empty string.
fun geekProperty(name: String, default: String): String =
    sequenceOf(
        providers.gradleProperty(name).orNull,
        System.getenv(name),
        geekLocalProperties.getProperty(name),
    ).firstOrNull { !it.isNullOrBlank() } ?: default

// A plain-http API base would put the login tokens on the wire, so refuse to build one.
fun geekHttpsUrl(name: String, default: String): String {
    val url = geekProperty(name, default).trim().trimEnd('/')
    require(url.startsWith("https://")) { "$name must be an https:// URL, got '$url'" }
    return url
}

android {
    // The namespace stays upstream's so v2rayNG merges keep applying cleanly;
    // only the installed identity is GeekVPN's.
    namespace = "com.v2ray.ang"
    compileSdk = 37
    providers.gradleProperty("ndkVersion").orNull?.let { ndkVersion = it }

    defaultConfig {
        applicationId = "com.geekvpn.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (!abiFilterList.isNullOrEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    include(
                        "arm64-v8a",
                        "armeabi-v7a",
                        "x86_64",
                        "x86"
                    )
                }
                isUniversalApk = abiFilterList.isNullOrEmpty()
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // GeekVPN: the Telegram bot, for "support" and wallet links. Not a secret,
        // but set outside the repo like the API base; empty hides those links.
        val botUsername = geekProperty("GEEK_BOT_USERNAME", "").trim().removePrefix("@")
        require(botUsername.isEmpty() || Regex("[A-Za-z0-9_]{5,32}").matches(botUsername)) {
            "GEEK_BOT_USERNAME must be a Telegram username, got '$botUsername'"
        }
        buildConfigField("String", "BOT_USERNAME", "\"$botUsername\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("distribution")
    flavorDimensions.add("env")
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
            buildConfigField("String", "DISTRIBUTION", "\"F-Droid\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"Play Store\"")
        }

        // GeekVPN: which backend the build talks to. Staging installs beside prod.
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField(
                "String", "API_BASE",
                "\"${geekHttpsUrl("GEEK_API_BASE_STAGING", "https://staging-api.geekvpn.invalid")}\""
            )
        }
        create("prod") {
            dimension = "env"
            buildConfigField(
                "String", "API_BASE",
                "\"${geekHttpsUrl("GEEK_API_BASE_PROD", "https://api.geekvpn.invalid")}\""
            )
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    applicationVariants.all {
        val variant = this
        val isFdroid = variant.productFlavors.any { it.name == "fdroid" }
        if (isFdroid) {
            val versionCodes =
                mapOf(
                    "armeabi-v7a" to 2, "arm64-v8a" to 1, "x86" to 4, "x86_64" to 3, "universal" to 0
                )

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = output.getFilter("ABI") ?: "universal"
                    output.outputFileName = "v2rayNG_${variant.versionName}-fdroid_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (100 * variant.versionCode + versionCodes[abi]!!).plus(5000000)
                    } else {
                        return@forEach
                    }
                }
        } else {
            val versionCodes =
                mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = if (output.getFilter("ABI") != null)
                        output.getFilter("ABI")
                    else
                        "universal"

                    output.outputFileName = "GeekVPN_${variant.versionName}_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
                    } else {
                        return@forEach
                    }
                }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
        // GeekVPN ships Persian (default, see AppLocaleManager) and English only. The other
        // upstream translations stay in the tree so merges apply, but are not packaged.
        localeFilters += listOf(
            "en",
            "fa"
        )
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

}

// GeekVPN is not published to F-Droid; the upstream flavor stays declared so
// upstream merges apply, but its variants are not built.
androidComponents {
    beforeVariants(selector().withFlavor("distribution" to "fdroid")) { it.enable = false }
}

dependencies {
    // Core Libraries
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // AndroidX Core Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose Libraries
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    // Online payment gateways open in a Custom Tab (GeekVPN shop).
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Data and Storage Libraries
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Reactive and Utility Libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // QR Code: CameraX + ZXing
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)
    implementation(libs.core) // zxing core

    // AndroidX Lifecycle and Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Background Task Libraries
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Reorderable list
    implementation(libs.reorderable)

    // Testing Libraries
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.org.mockito.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
