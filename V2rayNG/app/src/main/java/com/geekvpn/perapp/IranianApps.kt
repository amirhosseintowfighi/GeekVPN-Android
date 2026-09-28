package com.geekvpn.perapp

/**
 * Iranian apps, for the per-app preset "برنامه‌های ایرانی مستقیم": banking,
 * payment, ride-hailing and shopping apps that refuse to run, or run slowly,
 * behind a foreign exit. Selected in bypass mode they skip the tunnel and do
 * not see the VPN at all.
 *
 * An `ir.` package prefix is a strong signal on its own (Bale, Eitaa, Divar,
 * Myket, Balad, Torob and most banks); the rest are named here. A package
 * that is not installed simply matches nothing.
 */
object IranianApps {
    private val PREFIXES = listOf(
        "ir.",
        // Tosan builds most Iranian banks' mobile apps.
        "com.tosan.",
        "cab.snapp.",
    )

    private val PACKAGES = setOf(
        "app.rbmain.a", // Rubika
        "mobi.mmdt.ottplus", // Soroush Plus
        "net.iGap", // iGap
        "com.zoodfood.android", // SnappFood
        "taxi.tap30.passenger", // Tapsi
        "com.digikala", // Digikala
        "com.farsitel.bazaar", // Cafe Bazaar
        "com.aparat", // Aparat
        "com.sheypoor.mobile", // Sheypoor
        "org.rajman.neshan.traffic.tehran", // Neshan
        "com.myirancell", // MyIrancell
        "com.samanpr.blu", // Blu Bank
        "com.sibche.aspardproject.app", // Asan Pardakht
        "com.adpdigital.mbs.ayande", // Ayandeh Bank
    )

    fun matches(packageName: String): Boolean =
        packageName in PACKAGES || PREFIXES.any { packageName.startsWith(it) }

    /** Of [installed], the Iranian ones, minus this app itself. */
    fun select(installed: Collection<String>, self: String): Set<String> =
        installed.filterTo(mutableSetOf()) { it != self && matches(it) }
}
