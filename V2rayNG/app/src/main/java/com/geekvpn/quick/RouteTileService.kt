package com.geekvpn.quick

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.geekvpn.GeekGraph
import com.geekvpn.connection.RouteMode
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Quick Settings tile «مسیر»: switches the route between smart and
 * global without opening the app, as Home's route sheet does ([RouteMode.apply]).
 * A running connection is rebuilt with it; a stopped one is left stopped.
 *
 * Runs in the app's main process, where the route is set from Home too.
 */
class RouteTileService : TileService() {
    /** The service's own lifetime, not the listening window: a click's write must finish. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineName("geek-route-tile"))

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }

    override fun onStartListening() {
        super.onStartListening()
        show(current())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val next = RouteTile.next(current())
        show(next)
        scope.launch {
            withContext(Dispatchers.IO) {
                next.apply(applicationContext)
                GeekGraph.connectionPrefs.routeMode = next
            }
            LogUtil.i(AppConfig.TAG, "RouteTile: route set to ${next.key}")
            // Restarts a running core only; Home picks the route up on its next start.
            LauncherManager.restartService(applicationContext)
        }
    }

    private fun current(): RouteMode = GeekGraph.connectionPrefs.routeMode ?: RouteMode.Smart

    private fun show(route: RouteMode) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_routing_24dp)
        tile.label = getString(R.string.geek_route_tile_label)
        // Global is the "VPN for everything" state; smart and direct leave Iranian traffic out.
        tile.state = if (route == RouteMode.Global) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val name = getString(RouteTile.labelOf(route))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = name
        } else {
            tile.label = getString(R.string.geek_route_tile_label_with, name)
        }
        tile.contentDescription = getString(R.string.geek_route_tile_label_with, name)
        tile.updateTile()
    }
}

/** Pure, for the tests. */
object RouteTile {
    /** Smart and global alternate; direct (set in the app) goes back to smart. */
    fun next(route: RouteMode): RouteMode = if (route == RouteMode.Smart) RouteMode.Global else RouteMode.Smart

    fun labelOf(route: RouteMode): Int = when (route) {
        RouteMode.Smart -> R.string.geek_route_smart
        RouteMode.Global -> R.string.geek_route_global
        RouteMode.Direct -> R.string.geek_route_direct
    }
}
