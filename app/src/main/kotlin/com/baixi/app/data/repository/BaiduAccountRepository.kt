package com.baixi.app.data.repository

import android.webkit.CookieManager
import com.baixi.app.data.db.BaiduAccountDao
import com.baixi.app.data.db.BaiduAccountEntity
import com.baixi.app.data.network.BaiduApi
import com.baixi.app.data.network.BaiduConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 百度账号数据仓库：Room 持久化 + 网络验证（gettemplatevariable 拿昵称）。
 */
class BaiduAccountRepository(
    private val dao: BaiduAccountDao,
    private val api: BaiduApi
) {

    fun observeAccount(): Flow<BaiduAccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): BaiduAccountEntity? = dao.getAccount()

    /**
     * 退出登录：只清理本平台域内的 WebView Cookie（不再 removeAllCookies 清掉其它平台登录态）+ 清除本地记录。
     * 注意：CookieManager 必须在带 Looper 的线程（Main）上调用，故切到 Dispatchers.Main。
     */
    suspend fun logoutBaidu() {
        withContext(Dispatchers.Main) {
            runCatching { expireCookiesOn(BAIDU_COOKIE_DOMAINS) }
        }
        dao.clear()
    }

    /**
     * 逐条过期指定域上的 Cookie（只影响本平台，不动其它平台）。
     * CookieManager 没有「按域枚举 Cookie」的 API，故先用 getCookie 读出该域现有 Cookie 串，
     * 再对每个 name 写 Max-Age=0；同时按父域（如 .baidu.com）再写一次，清掉 domain cookie 的落点。
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
     * 校验 Cookie 有效性（需含 BDUSS）；有效则拉取昵称并落库，返回 true；无效返回 false。
     */
    suspend fun saveBaiduAccount(cookie: String): Boolean {
        if (!BaiduConstants.isValidCookie(cookie)) return false
        val nickname = api.fetchNickname(cookie) ?: "百度用户"
        dao.upsert(
            BaiduAccountEntity(
                id = "baidu",
                cookie = cookie,
                nickname = nickname
            )
        )
        return true
    }

    private companion object {
        /**
         * 百度登录态 Cookie 所在域（取自已有常量，不凭空编）：
         * BaiduConstants.COOKIE_DOMAIN（https://pan.baidu.com）即登录 WebView（LOGIN_URL）的 host。
         * 百度的 BDUSS 等常挂在 .baidu.com 父域上，expireCookiesOn 会同时过期父域落点。
         */
        val BAIDU_COOKIE_DOMAINS = listOf(BaiduConstants.COOKIE_DOMAIN)
    }
}