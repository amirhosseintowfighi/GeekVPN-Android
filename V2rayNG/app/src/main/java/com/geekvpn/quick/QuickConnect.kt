package com.geekvpn.quick

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.geekvpn.connection.ConnectionPrefs
import com.geekvpn.ui.home.HomeActivity

/**
 * Connecting from outside the app: the Quick Settings tile, the home-screen
 * widget and the launcher shortcuts. Those live in the VPN process and would
 * start the selected server as it stands; with «سرور: خودکار» on they open
 * Home instead, which runs smart connect (clean addresses, delay test,
 * retries). Stopping stays direct everywhere.
 */
object QuickConnect {
    private const val REQUEST_CODE = 0x6e6b

    /** Whether a start from outside should go through the app's smart connect. */
    fun viaApp(): Boolean = ConnectionPrefs.open().autoServer

    fun intent(context: Context): Intent =
        Intent(context, HomeActivity::class.java)
            .putExtra(HomeActivity.EXTRA_CONNECT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Opens Home from the tile and closes the shade. The `Intent` overload is
     * deprecated from API 34, which requires the `PendingIntent` one; remove
     * the older branch when minSdk reaches 34.
     */
    fun openFromTile(service: TileService) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.startActivityAndCollapse(pendingIntent(service))
        } else {
            openFromTileLegacy(service)
        }
    }

    /**
     * Below API 34 only, where the `PendingIntent` overload does not exist;
     * from 34 the `Intent` one throws. Remove when minSdk reaches 34.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openFromTileLegacy(service: TileService) {
        service.startActivityAndCollapse(intent(service))
    }
}
