package com.geekvpn

import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import java.io.File

/**
 * GeekVPN's own MMKV files. Multi-process: the VPN service process reads the
 * scanner's overrides while the app process writes them.
 *
 * Outside MMKV's default directory on purpose: v2rayNG's backup copies every
 * file there (`MMKV.backupAllToDirectory`) into a zip the user can share, and
 * the account, device ID and tokens must not go with it.
 */
object GeekStorage {
    fun open(id: String): MMKV {
        val root = File(AngApplication.application.filesDir, "geek_mmkv").absolutePath
        return MMKV.mmkvWithID(id, MMKV.MULTI_PROCESS_MODE, null, root)
    }
}
