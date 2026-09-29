package com.geekvpn.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.geekvpn.GeekGraph
import com.geekvpn.GeekStorage
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    /** The version on offer, in every state that has one. */
    val offer: UpdateOffer? get() = null

    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(override val offer: UpdateOffer) : UpdateState

    /** [progress] is 0..1, or null while the size is unknown. */
    data class Downloading(override val offer: UpdateOffer, val progress: Float?) : UpdateState
    data class Ready(override val offer: UpdateOffer, val file: File) : UpdateState
    data class Failed(override val offer: UpdateOffer?, @param:StringRes val reason: Int) : UpdateState
}

/**
 * The app updating itself, since it is not installed from Google Play.
 *
 * Asks the backend for the latest release (at most every [CHECK_INTERVAL_MS]
 * unless the customer asks), downloads the APK built for this phone into the
 * cache, checks its SHA-256 and that it really is this app at that version,
 * then hands it to Android's installer. Android itself refuses an APK signed
 * with another key, so a swapped file cannot replace the app either way.
 *
 * Process-wide and in the main process only, like the rest of the API: the
 * download must survive the screen that started it.
 */
object AppUpdater {
    /**
     * Only the published build can update itself: a debug or staging APK has
     * another key or package, and installing the release over it fails.
     */
    val enabled: Boolean = BuildConfig.FLAVOR_env == "prod" && !BuildConfig.DEBUG

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineName("geek-updater"))
    private val prefs by lazy { GeekStorage.open(STORE_ID) }
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    /** The version whose banner the customer closed; asked again only for a newer one. */
    fun dismissedVersion(): String = prefs.decodeString(KEY_DISMISSED).orEmpty()

    fun dismiss(offer: UpdateOffer) {
        if (!offer.required) prefs.encode(KEY_DISMISSED, offer.versionName)
    }

    /** Asks the backend; [force] skips the interval (the customer tapped "check"). */
    fun check(force: Boolean = false) {
        if (!enabled || checkJob?.isActive == true || downloadJob?.isActive == true) return
        if (state.value is UpdateState.Ready) return
        val now = System.currentTimeMillis()
        if (!force && now - prefs.decodeLong(KEY_CHECKED_AT, 0L) < CHECK_INTERVAL_MS) return
        checkJob = scope.launch {
            if (force) mutableState.value = UpdateState.Checking
            val offer = try {
                UpdatePlan.offer(BuildConfig.VERSION_NAME, GeekGraph.api.appVersion(), Build.SUPPORTED_ABIS.toList())
            } catch (e: IOException) {
                LogUtil.w(AppConfig.TAG, "AppUpdater: version check failed", e)
                if (force) mutableState.value = UpdateState.Failed(null, R.string.geek_update_err_check)
                return@launch
            }
            prefs.encode(KEY_CHECKED_AT, now)
            mutableState.value = if (offer == null) UpdateState.UpToDate else UpdateState.Available(offer)
        }
    }

    fun download(context: Context) {
        val offer = state.value.offer ?: return
        if (downloadJob?.isActive == true) return
        val app = context.applicationContext
        downloadJob = scope.launch {
            mutableState.value = UpdateState.Downloading(offer, null)
            mutableState.value = try {
                UpdateState.Ready(offer, fetch(app, offer))
            } catch (e: UpdateFailure) {
                LogUtil.w(AppConfig.TAG, "AppUpdater: ${offer.versionName} not usable: ${e.message}")
                UpdateState.Failed(offer, e.reason)
            } catch (e: IOException) {
                LogUtil.w(AppConfig.TAG, "AppUpdater: downloading ${offer.versionName} failed", e)
                UpdateState.Failed(offer, R.string.geek_update_err_download)
            }
        }
    }

    fun cancelDownload() {
        val offer = state.value.offer ?: return
        downloadJob?.cancel()
        mutableState.value = UpdateState.Available(offer)
    }

    /**
     * Opens Android's installer. When the customer has not yet allowed
     * GeekVPN to install apps, opens that setting instead and returns false;
     * they come back and tap install again.
     */
    fun install(context: Context): Boolean {
        val ready = state.value as? UpdateState.Ready ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(settings)
            } catch (e: ActivityNotFoundException) {
                LogUtil.w(AppConfig.TAG, "AppUpdater: no unknown-sources settings screen", e)
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", ready.file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            LogUtil.w(AppConfig.TAG, "AppUpdater: no package installer", e)
            mutableState.value = UpdateState.Failed(ready.offer, R.string.geek_update_err_install)
            false
        }
    }

    private class UpdateFailure(@param:StringRes val reason: Int, message: String) : IOException(message)

    private suspend fun fetch(context: Context, offer: UpdateOffer): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        // One update at a time: whatever an older attempt left is not needed.
        dir.listFiles()?.forEach { if (it.name != offer.apk.fileName) it.delete() }
        val target = File(dir, offer.apk.fileName)
        val partial = File(dir, "${offer.apk.fileName}.part")
        val digest = MessageDigest.getInstance("SHA-256")

        http.newCall(Request.Builder().url(offer.apk.url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val total = response.body.contentLength().takeIf { it > 0 } ?: offer.apk.sizeBytes.takeIf { it > 0 }
            response.body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var shown = -1
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        val percent = total?.let { (read * 100 / it).toInt() } ?: -1
                        if (percent != shown) {
                            shown = percent
                            mutableState.value = UpdateState.Downloading(offer, total?.let { (read.toFloat() / it).coerceIn(0f, 1f) })
                        }
                    }
                }
            }
        }

        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (offer.apk.sha256 != null && hash != offer.apk.sha256) {
            partial.delete()
            throw UpdateFailure(R.string.geek_update_err_corrupt, "sha256 $hash != ${offer.apk.sha256}")
        }
        val info = archiveInfo(context.packageManager, partial.absolutePath)
        if (info == null || info.packageName != context.packageName ||
            UpdatePlan.compare(info.versionName.orEmpty(), offer.versionName) != 0
        ) {
            partial.delete()
            throw UpdateFailure(R.string.geek_update_err_corrupt, "not ${context.packageName} ${offer.versionName}: ${info?.packageName} ${info?.versionName}")
        }
        target.delete()
        if (!partial.renameTo(target)) throw IOException("could not move the download into place")
        target
    }

    /**
     * `getPackageArchiveInfo(String, Int)` is deprecated from API 33, where
     * the `PackageInfoFlags` overload replaces it. Remove the older branch
     * when minSdk reaches 33.
     */
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(path, 0)
        }

    private const val STORE_ID = "GEEK_UPDATE"
    private const val KEY_DISMISSED = "dismissed_version"
    private const val KEY_CHECKED_AT = "checked_at"
    private const val DIR = "updates"
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
}
