package com.baixi.app.data.repository

import android.webkit.CookieManager
import com.baixi.app.data.db.UCAccountDao
import com.baixi.app.data.db.UCAccountEntity
import com.baixi.app.data.network.UCApi
import com.baixi.app.data.network.UCConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UC 账号数据仓库：Room 持久化 + 网络验证 + __puus 会话刷新（与夸克同源，修复取链/直链过期失败）。
 * __puus 约 3 小时过期，是取链接口（/file/download 等）必须携带的有效会话字段；
 * 取链前惰性刷新 + 响应 Set-Cookie 自动回写双保险。
 */
class UCAccountRepository(
    private val dao: UCAccountDao,
    private val api: UCApi
) {

    /** 上次主动刷新 __puus 的时间戳（进程内；跨进程重启后首次取链会因间隔超时触发刷新） */
    private var lastRefreshTs = 0L

    /** cookieSink 落库用独立作用域（非 UI 线程，避免阻塞 API 调用链） */
    private val sinkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 每次 API 响应若带 Set-Cookie（__puus/__pus），自动合并并落库，保持会话始终新鲜
        api.cookieSink = { merged ->
            sinkScope.launch {
                dao.getAccount()?.let { acc ->
                    if (acc.cookie != merged) {
                        dao.upsert(acc.copy(cookie = merged, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    fun observeAccount(): Flow<UCAccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): UCAccountEntity? = dao.getAccount()

    /**
     * 返回「保证 __puus 未过期」的 Cookie（取链/下载前调用）：
     * - 距上次刷新超过 PUUS_REFRESH_INTERVAL_MS 时，先 refreshSession 再落库；
     * - 刷新失败则回退返回当前 Cookie（不让取链/下载直接崩）。
     */
    suspend fun getFreshCookie(): String? {
        val acc = dao.getAccount() ?: return null
        val need = System.currentTimeMillis() - lastRefreshTs > UCConstants.PUUS_REFRESH_INTERVAL_MS
        if (!need) return acc.cookie
        val refreshed = api.refreshSession(acc.cookie)
        // 仅当确实拿到「与旧 Cookie 不同的新 Cookie」才落库并推进 lastRefreshTs；
        // 否则视为刷新失败（不推进计时），下次调用仍会重试
        return if (refreshed != null && refreshed != acc.cookie) {
            dao.upsert(acc.copy(cookie = refreshed, updatedAt = System.currentTimeMillis()))
            lastRefreshTs = System.currentTimeMillis()
            refreshed
        } else {
            acc.cookie
        }
    }

    /**
     * 退出登录：只清理本平台域内的 WebView Cookie（不再 removeAllCookies 清掉其它平台登录态）+ 清除本地记录。
     * 注意：CookieManager 必须在带 Looper 的线程（Main）上调用，故切到 Dispatchers.Main。
     */
    suspend fun logoutUC() {
        withContext(Dispatchers.Main) {
            runCatching { expireCookiesOn(UC_COOKIE_DOMAINS) }
        }
        dao.clear()
    }

    /**
     * 逐条过期指定域上的 Cookie（只影响本平台，不动其它平台）。
     * CookieManager 没有「按域枚举 Cookie」的 API，故先用 getCookie 读出该域现有 Cookie 串，
     * 再对每个 name 写 Max-Age=0；同时按父域（如 .uc.cn）再写一次，清掉 domain cookie 的落点。
     */
    private fun expireCookiesOn(urls: List<String>) {
        val cm = CookieManager.getInstance()
        for (url in urls) {
            val raw = runCatching { cm.getCookie(url) }.getOrNull().orEmpty()
            if (raw.isBlank()) continue
            val host = url.substringAfter("://").substringBefore('/')
            val parent = host.split('.').takeLast(2).joinToString(".")
            for (kv in raw.split(";")) {
                val name = kv.trim().substringBefore('=')
                if (name.isBlank()) continue
                runCatching { cm.setCookie(url, "$name=; Max-Age=0; Path=/") }
                runCatching { cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.$parent") }
            }
        }
        runCatching { cm.flush() }
    }

    suspend fun saveUCAccount(cookie: String): Boolean {
        if (!UCConstants.isValidCookie(cookie)) return false
        val nickname = api.fetchNickname(cookie) ?: "UC用户"
        dao.upsert(
            UCAccountEntity(
                id = "uc",
                cookie = cookie,
                nickname = nickname
            )
        )
        // 重置刷新计时：新登录的 __puus 是新鲜的，避免立刻触发一次无谓刷新
        lastRefreshTs = System.currentTimeMillis()
        return true
    }

    private companion object {
        /**
         * UC 登录态 Cookie 所在域（取自已有常量，不凭空编）：
         * UCConstants.COOKIE_DOMAIN（https://drive.uc.cn）即登录 WebView（LOGIN_URL）的 host。
         * UC 的 Cookie 常挂在 .uc.cn 父域上，expireCookiesOn 会同时过期父域落点。
         */
        val UC_COOKIE_DOMAINS = listOf(UCConstants.COOKIE_DOMAIN)
    }
}