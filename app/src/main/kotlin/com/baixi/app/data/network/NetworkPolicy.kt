package com.baixi.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.delay

/**
 * 网络策略：流量网络下载「三选」支持（继续 / 等待WiFi / 取消）+ WiFi 恢复等待。
 * 不做「仅 WiFi 下载」硬开关——改为下载启动时弹窗询问，一次选择可记忆。
 */
object NetworkPolicy {

    /** 下载任务的展示信息（弹窗用） */
    data class TaskInfo(val fileName: String, val sizeBytes: Long)

    /** 用户对「流量网络下载」的决定 */
    enum class Decision { CONTINUE, WAIT_WIFI, CANCEL }

    /**
     * 当前默认网络是否按流量计费（蜂窝/限速热点）；无网络返回 false（由下载自身报错）。
     * 保险丝：查询失败（权限缺失/系统异常）一律按非计费放行——策略检查绝不能卡死下载本身。
     */
    fun isMetered(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 无默认网络 → false（让下载自然报错）；capabilities 缺失 → 按流量处理（宁可多问不偷跑）
            val network: Network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return true
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } else {
            @Suppress("DEPRECATION")
            cm.isActiveNetworkMetered
        }
    }.getOrDefault(false)

    /** 挂起等待默认网络恢复为非计费（WiFi）；任务被取消（isActive=false）时立即返回 */
    suspend fun awaitWifiRestored(context: Context, isActive: suspend () -> Boolean) {
        while (isActive() && isMetered(context)) {
            delay(2000)
        }
    }
}
