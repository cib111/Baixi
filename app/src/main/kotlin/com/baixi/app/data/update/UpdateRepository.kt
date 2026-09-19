package com.baixi.app.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.baixi.app.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** 一次可用的更新（来自 GitHub Releases 的最新发布） */
data class UpdateInfo(
    /** 纯版本号，如 1.3（去掉 tag 的 v 前缀） */
    val version: String,
    /** 原始 tag，如 v1.3 */
    val tag: String,
    /** 更新说明（Release body 原文） */
    val changelog: String,
    /** APK 资源下载直链 */
    val apkUrl: String,
    /** APK 字节数（0 = 未知） */
    val apkSize: Long,
    /** Release 页面地址（浏览器查看用） */
    val pageUrl: String
)

/**
 * 应用内更新检查：读取 GitHub 公开仓库的最新 Release，与本地版本比较。
 *
 * - 仓库公开 → 用匿名 GitHub API 即可，**APK 里不包含任何 token**；
 * - 换更新源只改 [OWNER] / [REPO] 两个常量；
 * - 网络失败 / 仓库没有 Release / Release 里没有 APK 资源 → 一律返回 null（当作「已是最新」），不打扰用户。
 */
class UpdateRepository(private val context: Context) {

    /** 当前版本名（如 1.2） */
    val currentVersionName: String = packageVersionName(context)

    /** 当前版本号（versionCode） */
    val currentVersionCode: Long = packageVersionCode(context)

    /** 检查更新；null = 已是最新（或暂无可用的 Release） */
    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Baixi-Android/$currentVersionName")
                .build()
            val body = HttpClients.apiClient().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            parseRelease(body, currentVersionName)
        }
    }

    companion object {
        /** ★ 更新来源仓库：改这两行即可切换更新源（必须是公开仓库，否则匿名读不到 Release） */
        const val OWNER = "cib111"
        const val REPO = "Baixi"

        /** Release JSON → 可用更新；已是最新 / 缺 APK 资源返回 null */
        internal fun parseRelease(json: String, currentVersion: String): UpdateInfo? {
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name").trim()
            val version = tag.removePrefix("v").removePrefix("V").trim()
            if (version.isBlank()) return null
            var apkUrl = ""
            var apkSize = 0L
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        apkSize = a.optLong("size")
                        break
                    }
                }
            }
            if (apkUrl.isBlank()) return null
            if (!isNewer(version, currentVersion)) return null
            return UpdateInfo(
                version = version,
                tag = tag,
                changelog = obj.optString("body").trim(),
                apkUrl = apkUrl,
                apkSize = apkSize,
                pageUrl = obj.optString("html_url")
            )
        }

        /** 版本比较：1.10 > 1.9 > 1.9.0；非数字段按 0 处理 */
        internal fun isNewer(candidate: String, current: String): Boolean {
            val a = segmentsOf(candidate)
            val b = segmentsOf(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun segmentsOf(v: String): List<Int> = v
            .split('.', '-', '_', '+')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

        private fun packageInfo(context: Context) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

        private fun packageVersionName(context: Context): String =
            runCatching { packageInfo(context).versionName.orEmpty() }.getOrDefault("")

        private fun packageVersionCode(context: Context): Long = runCatching {
            val info = packageInfo(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrDefault(0L)
    }
}
