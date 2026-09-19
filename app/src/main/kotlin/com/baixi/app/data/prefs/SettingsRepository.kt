package com.baixi.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用设置（SharedPreferences 持久化）。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("baixi_settings", Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0L)

    /**
     * 设置写入版本号：每次写入自增。
     *
     * ★ 为什么需要它：SharedPreferences 的普通 getter 读取**不参与 Compose 快照**，
     *   在 composable 里直接读 `settings.downloadThreads` 这类属性，改完值界面不会重组，
     *   只有碰巧别的地方触发重组时才会「突然」显示新值 —— 表现就是「点了有时有反应、有时没反应」。
     *   UI 侧用 `collectAsState()` 订阅它，并把它作为 remember 的 key 来读取设置值。
     */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** 统一的写入入口：落盘 + 版本号自增（所有 setter 都必须走这里） */
    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.block()
        editor.apply()
        _revision.value += 1L
    }

    /** 下载线程数（分片并发数），默认 32，上限 512 */
    var downloadThreads: Int
        get() = prefs.getInt("download_threads", DEFAULT_DOWNLOAD_THREADS).coerceIn(1, 512)
        set(value) {
            edit { putInt("download_threads", value.coerceIn(1, 512)) }
        }

    /** 夸克/UC 专属下载线程数（0=跟随全局）；网盘 CDN 对单文件并发 Range 敏感，过高会触发限流 */
    var quarkDownloadThreads: Int
        get() = prefs.getInt("quark_download_threads", 0).coerceIn(0, 512)
        set(value) {
            edit { putInt("quark_download_threads", value.coerceIn(0, 512)) }
        }

    /** UC 专属下载线程数（0=跟随全局）；与夸克同为阿里系 CDN，对单文件并发 Range 敏感 */
    var ucDownloadThreads: Int
        get() = prefs.getInt("uc_download_threads", 0).coerceIn(0, 512)
        set(value) {
            edit { putInt("uc_download_threads", value.coerceIn(0, 512)) }
        }

    /** 百度专属下载线程数（0=跟随全局）；普通账号限速明显，提升线程对非会员效果有限 */
    var baiduDownloadThreads: Int
        get() = prefs.getInt("baidu_download_threads", 0).coerceIn(0, 512)
        set(value) {
            edit { putInt("baidu_download_threads", value.coerceIn(0, 512)) }
        }

    /** 139 专属下载线程数（0=跟随全局）；中国移动云盘 CDN */
    var c139DownloadThreads: Int
        get() = prefs.getInt("c139_download_threads", 0).coerceIn(0, 512)
        set(value) {
            edit { putInt("c139_download_threads", value.coerceIn(0, 512)) }
        }

    /** 123 专属下载线程数（0=跟随全局）；123 云盘 CDN */
    var pan123DownloadThreads: Int
        get() = prefs.getInt("pan123_download_threads", 0).coerceIn(0, 512)
        set(value) {
            edit { putInt("pan123_download_threads", value.coerceIn(0, 512)) }
        }

    /** 临时转存目录名（夸克/UC/迅雷/百度/139/123 云盘分享转存用的临时目录，默认「白析临时存储」） */
    var tempDirName: String
        get() = prefs.getString("temp_dir_name", DEFAULT_TEMP_DIR_NAME)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TEMP_DIR_NAME
        set(value) {
            edit { putString("temp_dir_name", value.trim().ifBlank { DEFAULT_TEMP_DIR_NAME }) }
        }

    /** 启动时自动检查更新（GitHub Releases，默认开启） */
    var autoCheckUpdate: Boolean
        get() = prefs.getBoolean("auto_check_update", true)
        set(value) {
            edit { putBoolean("auto_check_update", value) }
        }

    /** 用户点了「跳过此版本」的版本号；下次不再对该版本弹窗（空/null = 未跳过） */
    var skippedUpdateVersion: String?
        get() = prefs.getString("skipped_update_version", null)?.takeIf { it.isNotBlank() }
        set(value) {
            edit { putString("skipped_update_version", value) }
        }

    /** 剪切板自动识别分享链接（默认开启） */
    var clipboardAutoDetect: Boolean
        get() = prefs.getBoolean("clipboard_auto_detect", true)
        set(value) {
            edit { putBoolean("clipboard_auto_detect", value) }
        }

    /** 自定义下载保存目录（SAF tree Uri，content://...）；null/空 = 系统默认 Download 目录 */
    var downloadDirUri: String?
        get() = prefs.getString("download_dir_uri", null)
        set(value) {
            edit { putString("download_dir_uri", value) }
        }

    /** 最大同时下载任务数（默认 1：前台任务吃满带宽，其余排队；参考 IDM 默认单任务满速） */
    var maxConcurrentDownloads: Int
        get() = prefs.getInt("max_concurrent_downloads", DEFAULT_MAX_CONCURRENT_DOWNLOADS).coerceIn(1, 10)
        set(value) {
            edit { putInt("max_concurrent_downloads", value.coerceIn(1, 10)) }
        }

    /** 下载速度限制（字节/秒；0 = 不限速） */
    var downloadSpeedLimit: Long
        get() = prefs.getLong("download_speed_limit", 0L).coerceAtLeast(0L)
        set(value) {
            edit { putLong("download_speed_limit", value.coerceAtLeast(0L)) }
        }

    /** 下载失败后自动重试次数（默认 3，范围 0-10） */
    var downloadRetryCount: Int
        get() = prefs.getInt("download_retry_count", DEFAULT_DOWNLOAD_RETRY_COUNT)
        set(value) {
            edit { putInt("download_retry_count", value.coerceIn(0, 10)) }
        }

    /** 锁屏后保持下载：开启后下载时获取 WakeLock，并可引导加入「忽略电池优化」白名单（默认开启） */
    var keepDownloadWhenLocked: Boolean
        get() = prefs.getBoolean("keep_download_when_locked", true)
        set(value) {
            edit { putBoolean("keep_download_when_locked", value) }
        }

    /** 通知栏进度样式：true=完整通知（进度条+下载速度）；false=仅显示通知（隐藏速度） */
    var notificationShowSpeed: Boolean
        get() = prefs.getBoolean("notification_show_speed", true)
        set(value) {
            edit { putBoolean("notification_show_speed", value) }
        }

    /**
     * 下载完成后自动打开文件。
     * 仅应用在前台时生效：Android 10+ 禁止后台启动界面（后台会被系统静默拦截），
     * 后台场景由「下载完成」通知的「打开」按钮兜底。
     */
    var autoOpenAfterDownload: Boolean
        get() = prefs.getBoolean("auto_open_after_download", false)
        set(value) {
            edit { putBoolean("auto_open_after_download", value) }
        }

    /** APK 下载完成后自动拉起安装界面（同样仅前台生效，后台走完成通知的「安装」按钮） */
    var autoInstallApk: Boolean
        get() = prefs.getBoolean("auto_install_apk", false)
        set(value) {
            edit { putBoolean("auto_install_apk", value) }
        }

    /** 下载完成后自动删除任务记录（文件保留在下载目录，仅清列表） */
    var autoDeleteTaskAfterDownload: Boolean
        get() = prefs.getBoolean("auto_delete_task_after_download", false)
        set(value) {
            edit { putBoolean("auto_delete_task_after_download", value) }
        }

    /** 启动时自动恢复上次未完成的下载（关闭时只把中断任务标记为「已暂停」） */
    var resumeInterruptedOnStart: Boolean
        get() = prefs.getBoolean("resume_interrupted_on_start", true)
        set(value) {
            edit { putBoolean("resume_interrupted_on_start", value) }
        }

    /** 流量网络下载策略：0=每次询问，1=不再提示（直接下载），2=等待 WiFi 自动续传 */
    var meteredDownloadChoice: Int
        get() = prefs.getInt("metered_download_choice", METERED_ASK)
        set(value) {
            edit { putInt("metered_download_choice", value.coerceIn(0, 2)) }
        }

    // 桌面图标动态切换已移除：主备图标统一为系统默认机器人图标

    /** 忽略 SSL 证书校验（抓包调试用，隐藏菜单开启；默认关闭） */
    var ignoreSslCert: Boolean
        get() = prefs.getBoolean("ignore_ssl_cert", false)
        set(value) {
            edit { putBoolean("ignore_ssl_cert", value) }
        }

    /** 百度网盘大文件限速提示：是否已选择「不再显示」 */
    var baiduLimitHintDismissed: Boolean
        get() = prefs.getBoolean("baidu_limit_hint_dismissed", false)
        set(value) {
            edit { putBoolean("baidu_limit_hint_dismissed", value) }
        }

    /** 深色模式：0=跟随系统，1=浅色，2=深色 */
    var darkMode: Int
        get() = prefs.getInt("dark_mode", 0)
        set(value) {
            edit { putInt("dark_mode", value.coerceIn(0, 2)) }
        }

    /** 主题色模式：0=动态色彩（Android12+ 壁纸取色，低版本回退默认蓝），1=默认蓝色，2=自定义种子色 */
    var themeColorMode: Int
        get() = prefs.getInt("theme_color_mode", 0)
        set(value) {
            edit { putInt("theme_color_mode", value.coerceIn(0, 2)) }
        }

    /** 自定义主题种子色（ARGB 值） */
    var themeSeedColor: Long
        get() = prefs.getLong("theme_seed_color", DEFAULT_SEED_COLOR)
        set(value) {
            edit { putLong("theme_seed_color", value) }
        }

    // ---------- 下载统计（累计 / 今日流量与完成数；无需建表，随下载完成累加） ----------

    /** 累计下载字节数 */
    val statsTotalBytes: Long
        get() = prefs.getLong("stats_total_bytes", 0L)

    /** 累计完成下载数 */
    val statsTotalCount: Int
        get() = prefs.getInt("stats_total_count", 0)

    /** 今日下载字节数（跨天自动归零） */
    val statsTodayBytes: Long
        get() = if (prefs.getString("stats_today_date", "") == todayKey()) {
            prefs.getLong("stats_today_bytes", 0L)
        } else {
            0L
        }

    /**
     * 记录一次下载完成（累计 + 今日；由 DownloadManager 完成回调触发）。
     * ★ @Synchronized：读-改-写必须在同一临界区内完成，否则多任务并发完成时会丢计数/流量。
     */
    @Synchronized
    fun recordCompletedDownload(bytes: Long) {
        val safe = bytes.coerceAtLeast(0L)
        val today = todayKey()
        val sameDay = prefs.getString("stats_today_date", "") == today
        val todayBase = if (sameDay) prefs.getLong("stats_today_bytes", 0L) else 0L
        edit {
            putLong("stats_total_bytes", statsTotalBytes + safe)
            putInt("stats_total_count", statsTotalCount + 1)
            putString("stats_today_date", today)
            putLong("stats_today_bytes", todayBase + safe)
        }
    }

    /** 今日日期键（yyyy-MM-dd，本地时区） */
    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format(
            java.util.Locale.US, "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    companion object {
        const val DEFAULT_DOWNLOAD_THREADS = 32
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 1
        const val DEFAULT_DOWNLOAD_RETRY_COUNT = 3

        /** 流量网络下载策略常量 */
        const val METERED_ASK = 0
        const val METERED_ALWAYS_CONTINUE = 1
        const val METERED_ALWAYS_WAIT = 2

        /** 默认临时转存目录名 */
        const val DEFAULT_TEMP_DIR_NAME = "白析临时存储"

        /** 默认主题种子色：Material Blue（与内置默认方案一致） */
        const val DEFAULT_SEED_COLOR = 0xFF415F91L
    }
}
