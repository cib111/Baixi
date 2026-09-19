package com.baixi.app.data.repository

import android.webkit.CookieManager
import com.baixi.app.data.db.C139AccountDao
import com.baixi.app.data.db.C139AccountEntity
import com.baixi.app.data.network.C139Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 139 网盘账号数据仓库：Room 持久化 + Cookie 校验。
 * 登录态 = mail.10086.cn 的 Os_SSo_Sid + RMKEY（WebView 登录后提取）。
 */
class C139AccountRepository(
    private val dao: C139AccountDao
) {

    fun observeAccount(): Flow<C139AccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): C139AccountEntity? = dao.getAccount()

    /**
     * 退出登录：只清理本平台域内的 WebView Cookie（不再 removeAllCookies 清掉其它平台登录态）+ 清除本地记录。
     * 注意：CookieManager 必须在带 Looper 的线程（Main）上调用，故切到 Dispatchers.Main。
     */
    suspend fun logoutC139() {
        withContext(Dispatchers.Main) {
            runCatching { expireCookiesOn(C139_COOKIE_DOMAINS) }
        }
        dao.clear()
    }

    /**
     * 逐条过期指定域上的 Cookie（只影响本平台，不动其它平台）。
     * CookieManager 没有「按域枚举 Cookie」的 API，故先用 getCookie 读出该域现有 Cookie 串，
     * 再对每个 name 写 Max-Age=0；同时按父域（如 .139.com / .10086.cn）再写一次，清掉 domain cookie 的落点。
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

    /**
     * 校验 139 Cookie 有效性（Os_SSo_Sid+RMKEY 或 authorization 任一成立）；
     * 有效则提取账号与 authorization 并落库，返回 true。
     */
    suspend fun saveC139Account(cookie: String): Boolean {
        if (!C139Constants.isValidCookie(cookie)) return false
        val nickname = C139Constants.extractAccount(cookie) ?: "139用户"
        val authorization = C139Constants.extractAuthorization(cookie).orEmpty()
        dao.upsert(
            C139AccountEntity(
                id = "c139",
                cookie = cookie,
                nickname = nickname,
                authorization = authorization
            )
        )
        return true
    }

    private companion object {
        /**
         * 139 登录态 Cookie 所在域（取自已有常量，不凭空编）：
         * COOKIE_DOMAIN（https://mail.10086.cn，Os_SSo_Sid + RMKEY）与
         * COOKIE_DOMAIN_BACKUP（https://yun.139.com，authorization；亦为登录 WebView LOGIN_URL 的 host）。
         * expireCookiesOn 会同时过期父域（.10086.cn / .139.com）落点。
         */
        val C139_COOKIE_DOMAINS = listOf(
            C139Constants.COOKIE_DOMAIN,
            C139Constants.COOKIE_DOMAIN_BACKUP
        )
    }
}