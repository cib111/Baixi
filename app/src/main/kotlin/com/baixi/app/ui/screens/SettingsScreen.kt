package com.baixi.app.ui.screens
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baixi.app.data.backup.AuthBackupManager
import com.baixi.app.data.backup.AuthCrypto
import com.baixi.app.data.download.DownloadSaver
import com.baixi.app.data.network.HttpClients
import com.baixi.app.data.prefs.SettingsRepository
import com.baixi.app.ui.SnackbarController
import com.baixi.app.util.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页：下载线程数设置 + 主题外观 + 日志与网盘认证。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    scrollBehavior: TopAppBarScrollBehavior,
    downloadThreads: Int,
    onThreadsChange: (Int) -> Unit,
    /** 分平台下载线程数：进入独立设置页（夸克/UC/迅雷单独调整） */
    onPlatformThreadsClick: () -> Unit,
    onThemeClick: () -> Unit,
    onAboutClick: () -> Unit,
    backupManager: AuthBackupManager,
    /** 清除下载缓存：临时转存分片与合并/HLS 缓存（MainScreen 注入 DownloadManager.clearCache） */
    /** 清除下载缓存（分片 / HLS / 合并 / 预览副本）：suspend，调用方等待清理完成后才能重算大小 */
    onClearCache: suspend () -> Unit,
    /** 打开「已下载」文件管理页（叠加覆盖层，入口在本页「通用」组） */
    onOpenDownloadedFiles: () -> Unit = {},
    /** 打开「直链下载」页（叠加覆盖层，入口在本页「通用」组） */
    onOpenDirectLink: () -> Unit = {},
    /** 当前版本名（如 1.2）：展示在「检查更新」条目上 */
    currentVersionName: String = "",
    /** 手动检查更新：由 MainScreen 执行请求并弹窗/提示 */
    onCheckUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showThreadsDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    // 网盘认证导出弹窗（AES 加密 + 导出范围）
    var showExportAuthDialog by remember { mutableStateOf(false) }
    // 网盘认证导入：加密文件内容（非空时弹解密密码框）
    var pendingImportContent by remember { mutableStateOf<String?>(null) }
    var showImportAuthDialog by remember { mutableStateOf(false) }
    // 下载线程数直接以参数 downloadThreads 为准（单一数据源，避免本地镜像与外部值不同步）
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 下载保存目录（SAF）：本地状态驱动 UI 刷新，同时同步 SharedPreferences
    val settingsRepo = remember { SettingsRepository(context) }
    // 设置写入版本号：主题等值写在 SharedPreferences 里，订阅它才能让摘要跟着变
    val settingsRevision by settingsRepo.revision.collectAsState()
    var downloadDirUri by remember { mutableStateOf(settingsRepo.downloadDirUri) }
    // 隐藏开发调试：忽略 SSL 证书（抓包用，长按「关于白析」打开菜单）
    var ignoreSsl by remember { mutableStateOf(settingsRepo.ignoreSslCert) }
    var showDevMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ignoreSsl = settingsRepo.ignoreSslCert }
    // 网络与下载策略（本地状态驱动 UI，同时同步 SharedPreferences）
    var maxConcurrent by remember { mutableStateOf(settingsRepo.maxConcurrentDownloads) }
    var speedLimitBps by remember { mutableStateOf(settingsRepo.downloadSpeedLimit) }
    var retryCount by remember { mutableStateOf(settingsRepo.downloadRetryCount) }
    var showConcurrencyDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showRetryDialog by remember { mutableStateOf(false) }
    // 流量网络下载策略：弹窗三选一（单选确认，替代原「点按循环切换」）
    var showMeteredDialog by remember { mutableStateOf(false) }
    // 用户体验与系统适配：锁屏保持下载 / 通知栏速度
    var keepLocked by remember { mutableStateOf(settingsRepo.keepDownloadWhenLocked) }
    var showSpeed by remember { mutableStateOf(settingsRepo.notificationShowSpeed) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    // 下载完成后的自动化动作（默认全关；自动打开/自动安装在应用处于后台时会被系统拦截，仅前台生效）
    var autoOpen by remember { mutableStateOf(settingsRepo.autoOpenAfterDownload) }
    var autoInstallApk by remember { mutableStateOf(settingsRepo.autoInstallApk) }
    var autoDeleteTask by remember { mutableStateOf(settingsRepo.autoDeleteTaskAfterDownload) }
    // 启动时恢复未完成任务（默认开启：应用重启后自动续传上次中断的下载）
    var resumeInterrupted by remember { mutableStateOf(settingsRepo.resumeInterruptedOnStart) }
    // 新增设置：临时转存目录名 / 剪切板自动识别开关（本地状态驱动 UI，同时同步 SharedPreferences）
    var tempDirName by remember { mutableStateOf(settingsRepo.tempDirName) }
    var showTempDirDialog by remember { mutableStateOf(false) }
    var clipboardAutoDetect by remember { mutableStateOf(settingsRepo.clipboardAutoDetect) }
    // 启动时自动检查更新（默认开启）
    var autoCheckUpdate by remember { mutableStateOf(settingsRepo.autoCheckUpdate) }
    // 流量网络下载策略：0=每次询问，1=不再提示，2=等待WiFi（与下载前弹窗三选共享同一持久化）
    var meteredChoice by remember { mutableStateOf(settingsRepo.meteredDownloadChoice) }
    // 清除缓存：计算各类缓存的大小（首次进设置页时计算一次），确认后调用注入的清理回调
    var cacheSizeBytes by remember { mutableStateOf(0L) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    // 是否计算过（避免每次重组/旋转都重算一遍）
    var cacheComputed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!cacheComputed) {
            cacheSizeBytes = withContext(Dispatchers.IO) { cacheSizeBytesOf(context) }
            cacheComputed = true
        }
    }
    // 清理后重新计算，让提示数字准确。
    // ★ 必须先 await 清理完成再重算：清理走注入的挂起回调（DownloadManager.clearCache 内部含递归删除），
    //   旧实现是「发起清理（异步）→ 立刻重算」，会在删除完成前统计，误报「仍有 X 缓存」。
    val refreshCacheSize: () -> Unit = {
        scope.launch {
            runCatching { onClearCache() }
            cacheSizeBytes = withContext(Dispatchers.IO) { cacheSizeBytesOf(context) }
            SnackbarController.show(
                if (cacheSizeBytes == 0L) "缓存已清空" else "仍有 ${formatCacheSize(cacheSizeBytes)} 缓存（正在下载的任务占用）"
            )
        }
    }
    // 通知权限（Android 13+）：未授权时点击「通知栏下载进度」先申请，授权后生效
    val notifyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showSpeed = true
            settingsRepo.notificationShowSpeed = true
        }
    }
    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // 持久授权：应用重启后仍可写（API19+；Android 10/11+ 分区存储必需）
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            settingsRepo.downloadDirUri = uri.toString()
            downloadDirUri = uri.toString()
            SnackbarController.show("下载保存目录已更新")
        }
    }
    // 导入网盘认证文件选择器：选择后先判断是否加密备份，加密则弹密码框
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (text == null) {
                    SnackbarController.show("读取文件失败")
                    return@launch
                }
                if (AuthCrypto.isEncrypted(text)) {
                    // 加密备份：弹解密密码框
                    pendingImportContent = text
                    showImportAuthDialog = true
                } else {
                    // 明文备份：直接导入
                    val count = runCatching {
                        withContext(Dispatchers.IO) { backupManager.importJson(text) }
                    }.getOrElse { e ->
                        SnackbarController.show("导入失败：${e.message}")
                        return@launch
                    }
                    SnackbarController.show("已恢复 $count 个平台的认证信息")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
    ) {
        CollapsibleSection(
            title = "下载",
            icon = Icons.Outlined.Download,
            summary = "$downloadThreads 线程 · 同时 $maxConcurrent 个 · 限速 ${speedLimitText(speedLimitBps)}",
            initiallyExpanded = true,
            itemCount = 10
        ) {
            SettingsItem(
                icon = Icons.Outlined.Tune,
                title = "下载线程数",
                // 说明作用范围：改完对「正在下载」的任务不生效（需暂停后继续），避免误以为设置没反应
                description = "当前 $downloadThreads 线程（分片并发）；对正在下载的任务需暂停后继续才生效",
                onClick = { showThreadsDialog = true }
            )

            SectionDivider()

            // 分平台下载线程数：独立页面（平台多，弹窗展示不下）
            SettingsItem(
                icon = Icons.Outlined.Memory,
                title = "分平台下载线程数",
                description = "为夸克 / UC / 迅雷单独设置（默认跟随全局）",
                onClick = onPlatformThreadsClick
            )

            SectionDivider()

            // 临时转存目录名：夸克/UC/迅雷/百度等平台转存时自动创建的目标目录
            SettingsItem(
                icon = Icons.Outlined.CreateNewFolder,
                title = "临时转存目录名",
                description = if (tempDirName.isBlank()) "默认：${SettingsRepository.DEFAULT_TEMP_DIR_NAME}" else "当前：$tempDirName",
                onClick = { showTempDirDialog = true }
            )

            SectionDivider()

            // 下载保存目录：系统文件夹选择器（SAF，适配各 Android 版本分区存储）；
            // 已自定义时卡片右侧内嵌「恢复默认」操作（不单独外露按钮）
            SettingsItem(
                icon = Icons.Outlined.FolderOpen,
                title = "下载保存目录",
                description = downloadDirUri?.let { "已自定义：${DownloadSaver.safDirDisplay(it)}" }
                    ?: "系统默认 Download（点击自定义）",
                onClick = { dirLauncher.launch(null) },
                trailing = if (downloadDirUri != null) {
                    {
                        TextButton(
                            onClick = {
                                downloadDirUri = null
                                settingsRepo.downloadDirUri = null
                                SnackbarController.show("已恢复默认下载目录")
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "恢复默认",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    null
                }
            )

            SectionDivider()

            // 网络与下载策略
            SettingsItem(
                icon = Icons.Outlined.Layers,
                title = "最大同时下载任务数",
                description = "同时下载 $maxConcurrent 个任务（限制后台并发，避免占满带宽）",
                onClick = { showConcurrencyDialog = true }
            )

            SectionDivider()

            SettingsItem(
                icon = Icons.Outlined.Speed,
                title = "下载速度限制",
                description = speedLimitText(speedLimitBps),
                onClick = { showSpeedDialog = true }
            )

            SectionDivider()

            SettingsItem(
                icon = Icons.Outlined.Refresh,
                title = "失败自动重试",
                description = if (retryCount == 0) "失败后不自动重试" else "失败后自动重试 $retryCount 次（断点续传）",
                onClick = { showRetryDialog = true }
            )

            SectionDivider()

            // 流量网络下载策略：与下载前弹窗三选共享持久化，点击弹窗单选，立即生效
            SettingsItem(
                icon = Icons.Outlined.Wifi,
                title = "流量网络下载",
                description = when (meteredChoice) {
                    SettingsRepository.METERED_ALWAYS_CONTINUE -> "不再提示，流量下直接下载"
                    SettingsRepository.METERED_ALWAYS_WAIT -> "等待 WiFi 再下载"
                    else -> "每次下载前询问"
                },
                onClick = { showMeteredDialog = true }
            )

            SectionDivider()

            // 用户体验与系统适配：锁屏保持下载 / 通知栏进度样式
            SettingsItem(
                icon = Icons.Outlined.Power,
                title = "锁屏后保持下载",
                description = "开启后下载时获取 WakeLock 维持网络，并可加入「忽略电池优化」白名单",
                onClick = {
                    keepLocked = !keepLocked
                    settingsRepo.keepDownloadWhenLocked = keepLocked
                    if (keepLocked) {
                        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                        if (pm?.isIgnoringBatteryOptimizations(context.packageName) != true) {
                            showBatteryDialog = true
                        }
                    }
                },
                trailing = { Switch(checked = keepLocked, onCheckedChange = null) }
            )

            SectionDivider()

            SettingsItem(
                icon = Icons.Outlined.Notifications,
                title = "通知栏下载进度",
                description = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED ->
                        "未授予通知权限，下载通知将不可见（点击申请）"
                    showSpeed -> "完整通知：进度条 + 下载速度"
                    else -> "仅显示通知（隐藏下载速度）"
                },
                onClick = {
                    // Android 13+ 未授权：先申请通知权限，授权后自动开启完整通知
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        showSpeed = !showSpeed
                        settingsRepo.notificationShowSpeed = showSpeed
                    }
                },
                trailing = { Switch(checked = showSpeed, onCheckedChange = null) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 收起摘要：一眼看到「下载完成后」开了哪些自动动作
        val autoSummary = listOfNotNull(
            "自动打开".takeIf { autoOpen },
            "自动装 APK".takeIf { autoInstallApk },
            "删任务记录".takeIf { autoDeleteTask }
        ).joinToString(" · ").ifEmpty { "全部关闭" }
        CollapsibleSection(
            title = "下载完成后",
            summary = autoSummary,
            icon = Icons.Outlined.SystemUpdate,
            initiallyExpanded = true,
            itemCount = 4
        ) {
            SettingsItem(
                icon = Icons.Outlined.OpenInNew,
                title = "自动打开文件",
                description = if (autoOpen) "下载完成后自动打开（APK 走安装）" else "下载完成后仅用通知提示",
                onClick = {
                    autoOpen = !autoOpen
                    settingsRepo.autoOpenAfterDownload = autoOpen
                },
                trailing = { Switch(checked = autoOpen, onCheckedChange = null) }
            )

            SectionDivider()
            SettingsItem(
                icon = Icons.Outlined.SystemUpdate,
                title = "APK 自动安装",
                description = if (autoInstallApk) "APK 下载完成后自动拉起安装界面" else "需手动点完成通知的「安装」",
                onClick = {
                    autoInstallApk = !autoInstallApk
                    settingsRepo.autoInstallApk = autoInstallApk
                },
                trailing = { Switch(checked = autoInstallApk, onCheckedChange = null) }
            )

            SectionDivider()
            SettingsItem(
                icon = Icons.Outlined.DeleteSweep,
                title = "完成后删除任务记录",
                description = if (autoDeleteTask) "完成后自动清掉下载列表记录（文件保留）" else "完成后保留任务记录",
                onClick = {
                    autoDeleteTask = !autoDeleteTask
                    settingsRepo.autoDeleteTaskAfterDownload = autoDeleteTask
                },
                trailing = { Switch(checked = autoDeleteTask, onCheckedChange = null) }
            )

            SectionDivider()

            // 启动时恢复未完成任务：关闭后中断的任务只标记为「已暂停」
            SettingsItem(
                icon = Icons.Outlined.Refresh,
                title = "启动时恢复未完成任务",
                description = if (resumeInterrupted) "上次未下完的任务在应用启动后自动续传" else "已关闭：中断的任务只标记为已暂停",
                onClick = {
                    resumeInterrupted = !resumeInterrupted
                    settingsRepo.resumeInterruptedOnStart = resumeInterrupted
                },
                trailing = { Switch(checked = resumeInterrupted, onCheckedChange = null) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 收起摘要：直接反映当前深色模式与主题色方案
        val appearanceSummary = remember(settingsRevision) {
            val dark = when (settingsRepo.darkMode) {
                1 -> "浅色"
                2 -> "深色"
                else -> "跟随系统"
            }
            val color = when (settingsRepo.themeColorMode) {
                1 -> "默认蓝"
                2 -> "自定义色"
                else -> "动态色彩"
            }
            "$dark · $color"
        }
        CollapsibleSection(
            title = "外观",
            summary = appearanceSummary,
            icon = Icons.Outlined.Palette,
            initiallyExpanded = true,
            itemCount = 1
        ) {
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = "主题与外观",
                description = "主题色、动态色彩与深色模式",
                onClick = onThemeClick
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        CollapsibleSection(
            title = "通用",
            icon = Icons.Outlined.Layers,
            summary = "剪贴板识别${if (clipboardAutoDetect) "已开" else "已关"} · 缓存 ${formatCacheSize(cacheSizeBytes)}",
            initiallyExpanded = true,
            itemCount = 4
        ) {
            SettingsItem(
                icon = Icons.Outlined.ContentPaste,
                title = "剪切板自动识别",
                description = if (clipboardAutoDetect) "复制分享链接后自动识别并提示" else "已关闭，需手动输入或粘贴链接",
                onClick = {
                    clipboardAutoDetect = !clipboardAutoDetect
                    settingsRepo.clipboardAutoDetect = clipboardAutoDetect
                },
                trailing = { Switch(checked = clipboardAutoDetect, onCheckedChange = null) }
            )

            SectionDivider()
            SettingsItem(
                icon = Icons.Outlined.DeleteSweep,
                title = "清除缓存",
                description = "临时转存缓存 ${formatCacheSize(cacheSizeBytes)}（分片 / HLS / 合并 / 预览副本）",
                onClick = {
                    if (cacheSizeBytes > 0) showClearCacheDialog = true
                    else SnackbarController.show("当前没有可清理的缓存")
                }
            )

            SectionDivider()
            SettingsItem(
                icon = Icons.Outlined.FolderOpen,
                title = "已下载文件",
                description = "管理已保存到本地的文件：搜索、打开、分享、重命名、批量删除",
                onClick = onOpenDownloadedFiles
            )

            SectionDivider()
            SettingsItem(
                icon = Icons.Outlined.Link,
                title = "直链下载",
                description = "粘贴任意 HTTP/HTTPS 直链或 m3u8 链接直接多线程下载",
                onClick = onOpenDirectLink
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        CollapsibleSection(
            title = "日志",
            icon = Icons.Outlined.Article,
            summary = "崩溃日志 · 一键导出分享",
            initiallyExpanded = false,
            itemCount = 1
        ) {
            SettingsItem(
                icon = Icons.Outlined.Article,
                title = "导出日志",
                description = "导出崩溃日志与应用信息，便于排查问题",
                onClick = { showLogDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        CollapsibleSection(
            title = "网盘认证",
            icon = Icons.Outlined.Backup,
            summary = "登录状态加密备份与恢复",
            initiallyExpanded = false,
            itemCount = 2
        ) {
            SettingsItem(
                icon = Icons.Outlined.Backup,
                title = "导出网盘认证",
                description = "使用至少 8 位口令加密 Cookie/JWT 后导出",
                onClick = { showExportAuthDialog = true }
            )

            SectionDivider()
            SettingsItem(
                icon = Icons.Outlined.Restore,
                title = "导入网盘认证",
                description = "选择加密或明文的认证备份文件，恢复网盘登录",
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        CollapsibleSection(
            title = "关于",
            icon = Icons.Outlined.Info,
            summary = "v$currentVersionName · 自动检查更新${if (autoCheckUpdate) "已开" else "已关"}",
            initiallyExpanded = false,
            itemCount = 1
        ) {
            SettingsItem(
                icon = Icons.Outlined.Info,
                title = "关于白析",
                description = "版本信息、支持平台与技术说明",
                onClick = onAboutClick,
                onLongClick = { showDevMenu = true }, // 长按打开隐藏开发调试菜单
                onLongClickLabel = "打开开发调试菜单" // 无障碍：TalkBack 可发现该长按动作
            )

            SectionDivider()

            // 手动检查更新：从 GitHub Releases 读取最新版本（公开仓库，匿名 API）
            SettingsItem(
                icon = Icons.Outlined.SystemUpdate,
                title = "检查更新",
                description = "当前版本 v$currentVersionName · 从 GitHub Releases 获取最新版",
                onClick = onCheckUpdate
            )

            SectionDivider()

            // 启动时自动检查：打开应用即静默检查，有新版才弹窗
            SettingsItem(
                icon = Icons.Outlined.SystemUpdate,
                title = "启动时自动检查更新",
                description = if (autoCheckUpdate) "打开应用时自动检查，发现新版本会弹窗提示" else "已关闭，只能手动检查更新",
                onClick = {
                    autoCheckUpdate = !autoCheckUpdate
                    settingsRepo.autoCheckUpdate = autoCheckUpdate
                },
                trailing = { Switch(checked = autoCheckUpdate, onCheckedChange = null) }
            )
        }
    }
    // 导出日志方式选择弹窗
    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("导出日志") },
            text = {
                Column {
                    Text(
                        text = "选择日志导出方式：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showLogDialog = false
                            scope.launch {
                                val file = withContext(Dispatchers.IO) { LogExporter.export(context) }
                                if (file != null && LogExporter.share(context, file)) {
                                    SnackbarController.show("日志已分享")
                                } else {
                                    SnackbarController.show("导出日志失败")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("分享日志（发送到其他应用）")
                    }
                    TextButton(
                        onClick = {
                            showLogDialog = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    LogExporter.saveToDownloads(context)
                                }
                                SnackbarController.show(if (ok) "已保存到下载目录" else "保存失败")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存到下载目录")
                    }
                    TextButton(
                        onClick = {
                            showLogDialog = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    LogExporter.clearLogcat()
                                }
                                SnackbarController.show(if (ok) "日志缓存已清空" else "清空失败")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("清空日志缓存（logcat -c）")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) { Text("取消") }
            }
        )
    }

    // 隐藏开发调试菜单（长按「关于白析」打开）：忽略 SSL 证书（抓包调试用）
    if (showDevMenu) {
        AlertDialog(
            onDismissRequest = { showDevMenu = false },
            title = { Text("开发调试") },
            text = {
                Column {
                    val toggleIgnoreSsl = {
                        ignoreSsl = !ignoreSsl
                        settingsRepo.ignoreSslCert = ignoreSsl
                        HttpClients.ignoreSsl = ignoreSsl
                        SnackbarController.show(
                            if (ignoreSsl) "已忽略 SSL 证书校验（抓包模式）" else "已恢复 SSL 证书校验"
                        )
                    }
                    val rowShape = MaterialTheme.shapes.medium
                    val rowInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .combinedClickable(
                                interactionSource = rowInteraction,
                                indication = ripple(bounded = true),
                                onClick = toggleIgnoreSsl
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "忽略 SSL 证书",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (ignoreSsl) "已开启：所有网络请求不校验证书（抓包用）" else "已关闭：正常校验证书",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = ignoreSsl, onCheckedChange = { toggleIgnoreSsl() })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "开启后所有 API 与下载请求将忽略 SSL 证书校验，便于配合抓包调试。请勿在日常使用中开启，存在中间人攻击风险。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevMenu = false }) { Text("关闭") }
            }
        )
    }

    // 流量网络下载策略弹窗：三选一单选（所见即所选，选中即保存并关闭）
    if (showMeteredDialog) {
        val meteredOptions = listOf(
            Triple(
                SettingsRepository.METERED_ASK, "每次下载前询问",
                "流量网络下每次下载都会弹窗确认"
            ),
            Triple(
                SettingsRepository.METERED_ALWAYS_CONTINUE, "不再提示，流量下直接下载",
                "流量网络直接开始下载，不再弹窗"
            ),
            Triple(
                SettingsRepository.METERED_ALWAYS_WAIT, "等待 WiFi 再下载",
                "流量网络下自动暂停，连接 WiFi 后续传"
            )
        )
        AlertDialog(
            onDismissRequest = { showMeteredDialog = false },
            title = { Text("流量网络下载") },
            text = {
                Column {
                    Text(
                        text = "使用移动流量等计费网络下载时的策略：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    meteredOptions.forEach { (value, title, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = {
                                        meteredChoice = value
                                        settingsRepo.meteredDownloadChoice = value
                                        showMeteredDialog = false
                                    }
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // onClick=null：选中态由外层行点击驱动，RadioButton 纯展示
                            RadioButton(selected = meteredChoice == value, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMeteredDialog = false }) { Text("取消") }
            }
        )
    }

    // 线程数选择弹窗
    if (showThreadsDialog) {
        AlertDialog(
            onDismissRequest = { showThreadsDialog = false },
            title = { Text("下载线程数") },
            text = {
                Column {
                    Text(
                        text = "线程数越多，分片并行下载越快（需服务器支持 Range）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 三列网格：10 个选项（1~512）分 4 行；仍保留限高 + 滚动，横屏/矮屏时兜底
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        threadOptions.chunked(3).forEach { rowValues ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowValues.forEach { value ->
                                    RadioThreadRow(
                                        value = value,
                                        threads = downloadThreads,
                                        onSelect = { v ->
                                            onThreadsChange(v)
                                            showThreadsDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // 末尾不足 3 个时补空占位，保持三列对齐
                                repeat(3 - rowValues.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThreadsDialog = false }) { Text("取消") }
            }
        )
    }

    // 临时转存目录名编辑弹窗：留空/空格恢复默认
    if (showTempDirDialog) {
        var tempDirInput by remember { mutableStateOf(tempDirName) }
        AlertDialog(
            onDismissRequest = { showTempDirDialog = false },
            title = { Text("临时转存目录名") },
            text = {
                Column {
                    Text(
                        text = "夸克 / UC / 迅雷 / 百度等平台解析转存时，在网盘根目录自动创建的目标目录名。留空恢复默认。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempDirInput,
                        onValueChange = { tempDirInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("目录名") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val v = tempDirInput.trim()
                        tempDirName = if (v.isBlank()) SettingsRepository.DEFAULT_TEMP_DIR_NAME else v
                        settingsRepo.tempDirName = v
                        showTempDirDialog = false
                        SnackbarController.show(
                            if (v.isBlank()) "已恢复默认临时转存目录" else "临时转存目录已更新"
                        )
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTempDirDialog = false }) { Text("取消") }
            }
        )
    }

    // 清除缓存确认弹窗：确认后调用注入的清理回调并重算缓存大小
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清除缓存") },
            text = {
                Text(
                    text = "将删除临时转存缓存 ${formatCacheSize(cacheSizeBytes)}（分片 / HLS / 合并产物）。正在下载的任务对应缓存会保留。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        // 清理 + 重算都在 refreshCacheSize 内串行完成（onClearCache 已改为 suspend）
                        refreshCacheSize()
                    }
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("取消") }
            }
        )
    }

    // 导出网盘认证弹窗（AES 加密密码 + 导出范围）
    if (showExportAuthDialog) {
        ExportAuthDialog(
            onDismiss = { showExportAuthDialog = false },
            onConfirm = { password, onlyLoggedIn ->
                showExportAuthDialog = false
                scope.launch {
                    val content = runCatching {
                        withContext(Dispatchers.IO) { backupManager.export(password, onlyLoggedIn) }
                    }.getOrNull()
                    if (content == null) {
                        SnackbarController.show("导出失败")
                        return@launch
                    }
                    val encrypted = true
                    val saved = withContext(Dispatchers.IO) {
                        backupManager.saveToDownloads(context, content, encrypted)
                    }
                    SnackbarController.show(
                        if (saved) {
                            if (encrypted) "已加密导出到下载目录" else "已导出到下载目录"
                        } else {
                            "导出失败"
                        }
                    )
                }
            }
        )
    }

    // 导入加密备份弹窗（解密密码）
    if (showImportAuthDialog) {
        ImportAuthDialog(
            onDismiss = {
                showImportAuthDialog = false
                pendingImportContent = null
            },
            onConfirm = { password ->
                showImportAuthDialog = false
                val content = pendingImportContent
                pendingImportContent = null
                if (content != null) {
                    scope.launch {
                        val count = try {
                            withContext(Dispatchers.IO) { backupManager.import(content, password) }
                        } catch (e: javax.crypto.AEADBadTagException) {
                            SnackbarController.show("密码错误，解密失败")
                            return@launch
                        } catch (e: Exception) {
                            SnackbarController.show("导入失败：${e.message}")
                            return@launch
                        }
                        SnackbarController.show("已恢复 $count 个平台的认证信息")
                    }
                }
            }
        )
    }

    // 最大同时下载任务数
    if (showConcurrencyDialog) {
        val options = listOf(1, 2, 3, 5, 8)
        AlertDialog(
            onDismissRequest = { showConcurrencyDialog = false },
            title = { Text("最大同时下载任务数") },
            text = {
                Column {
                    options.forEach { v ->
                        val select = {
                            maxConcurrent = v
                            settingsRepo.maxConcurrentDownloads = v
                            showConcurrencyDialog = false
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClick = select)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = maxConcurrent == v, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("同时下载 $v 个任务", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConcurrencyDialog = false }) { Text("取消") }
            }
        )
    }

    // 下载速度限制：预设档位 + 自定义（KB/s）
    if (showSpeedDialog) {
        val presets = listOf(0L, 1L * 1024 * 1024, 2L * 1024 * 1024, 5L * 1024 * 1024, 10L * 1024 * 1024)
        // 弹窗内临时选择（不立即写设置）：null=未操作，-1=自定义，其余=预设值
        var tempSelected by remember { mutableStateOf<Long?>(null) }
        // 自定义输入：打开时若当前是自定义档位，带出原值（重新打开保留）
        var customKb by remember {
            mutableStateOf(
                if (speedLimitBps > 0 && speedLimitBps !in presets) (speedLimitBps / 1024).toString() else ""
            )
        }
        val effective = tempSelected ?: speedLimitBps
        // 自定义选中态：显式识别「-1=自定义」哨兵；未操作时按当前值是否为自定义档位判断
        val isCustom = when {
            tempSelected == -1L -> true
            tempSelected == null -> speedLimitBps > 0 && speedLimitBps !in presets
            else -> false
        }
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("下载速度限制") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    presets.forEach { v ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { tempSelected = v }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = !isCustom && effective == v, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (v == 0L) "不限速" else speedLimitText(v),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    // 自定义档位：点击单选即可选中（进入自定义模式）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustom,
                            onClick = {
                                tempSelected = -1L
                                // 当前已是自定义值时带出原值，便于修改
                                if (speedLimitBps > 0 && speedLimitBps !in presets && customKb.isBlank()) {
                                    customKb = (speedLimitBps / 1024).toString()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = customKb,
                            onValueChange = {
                                customKb = it.filter(Char::isDigit).take(6)
                                // 输入即视为选择自定义
                                tempSelected = -1L
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("自定义 KB/s") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 以当前选中项为准：选自定义则应用输入；选预设则应用预设值
                        if (isCustom) {
                            val kb = customKb.toLongOrNull()?.coerceAtLeast(1L)
                            if (kb != null) {
                                speedLimitBps = kb * 1024
                                settingsRepo.downloadSpeedLimit = kb * 1024
                            }
                            // 自定义输入为空：保持原值
                        } else if (tempSelected != null) {
                            val v = tempSelected ?: speedLimitBps
                            speedLimitBps = v
                            settingsRepo.downloadSpeedLimit = v
                        }
                        // 未做任何选择：保持当前值
                        showSpeedDialog = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("取消") }
            }
        )
    }

    // 失败自动重试次数
    if (showRetryDialog) {
        val options = listOf(0, 1, 2, 3, 5, 8, 10)
        AlertDialog(
            onDismissRequest = { showRetryDialog = false },
            title = { Text("失败自动重试") },
            text = {
                Column {
                    options.forEach { v ->
                        val select = {
                            retryCount = v
                            settingsRepo.downloadRetryCount = v
                            showRetryDialog = false
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClick = select)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = retryCount == v, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (v == 0) "不自动重试" else "失败后自动重试 $v 次",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRetryDialog = false }) { Text("取消") }
            }
        )
    }

    // 锁屏保持下载：引导加入「忽略电池优化」白名单
    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text("保持后台下载") },
            text = {
                Text(
                    text = "为确保障屏后下载不中断，建议将白析加入「忽略电池优化」白名单。是否前往系统设置？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryDialog = false
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                ) { Text("前往设置") }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) { Text("暂不") }
            }
        )
    }
}

/** 导出网盘认证弹窗：AES 加密密码 + 导出范围（仅已登录 / 全部绑定） */
@Composable
private fun ExportAuthDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String, onlyLoggedIn: Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var onlyLoggedIn by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出网盘认证") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "设置至少 8 位密码对认证文件进行 AES 加密。密码请务必牢记，丢失无法找回。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("加密密码（至少 8 位）") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                Text(
                    text = "导出范围",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onlyLoggedIn = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = onlyLoggedIn, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("仅导出当前已登录的网盘", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onlyLoggedIn = false }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !onlyLoggedIn, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出全部绑定的网盘", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password, onlyLoggedIn) },
                enabled = password.length >= 8
            ) { Text("导出") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 导入加密备份弹窗：输入解密密码 */
@Composable
private fun ImportAuthDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入网盘认证") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "该备份文件已加密，请输入导出时设置的密码进行解密。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("解密密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) { Text("解密并导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 可折叠分组：一张 Card 承载「分组头 + 子项列表」。
 * - 分组头：tonal 圆角图标容器 + 标题（titleSmall/SemiBold）+ 条目数 + 展开箭头，整行可点
 * - 展开/收起：AnimatedVisibility（高度展开 + 淡入 / 高度收起 + 淡出），箭头 rotate 平滑过渡
 * - 展开状态用 rememberSaveable 保存，旋转屏幕后不丢失
 * - 子项之间由调用方插入 [SectionDivider]（最后一项之后不插，避免卡片底部出现悬空分割线）
 */
@Composable
private fun CollapsibleSection(
    title: String,
    /** 分组头图标（默认 Tune，仅视觉，不影响任何设置项语义） */
    icon: ImageVector = Icons.Outlined.Tune,
    initiallyExpanded: Boolean = true,
    /**
     * 收起时展示的「当前取值」摘要（如「32 线程 · 同时 1 个 · 限速不限速」）。
     * 全部收起时分组头不再只是一个空标题，一眼就能看到关键设置。
     */
    summary: String? = null,
    /** 分组内条目数（仅用于分组头右侧展示，null 时不显示；有摘要时收起态让位给摘要） */
    itemCount: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    // 箭头（ExpandMore=向下）：展开时旋转 180° 表示可收起，收起时指回展开
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "sectionArrow"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClickLabel = if (expanded) "收起「$title」" else "展开「$title」",
                        onClick = { expanded = !expanded }
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // tonal 图标容器：主色容器 + onPrimaryContainer 图标，深浅色都由主题保证对比度
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 收起时显示当前取值摘要；展开时收起（条目本身就在显示这些值）
                    AnimatedVisibility(
                        visible = !expanded && summary != null,
                        enter = expandVertically(animationSpec = tween(200)) +
                            fadeIn(animationSpec = tween(160)),
                        exit = shrinkVertically(animationSpec = tween(160)) +
                            fadeOut(animationSpec = tween(100))
                    ) {
                        Text(
                            text = summary.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (itemCount != null && (expanded || summary == null)) {
                    Text(
                        text = "$itemCount 项",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起「$title」" else "展开「$title」",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(arrowRotation)
                )
            }
            // 展开/收起动画：高度展开 + 淡入 / 高度收起 + 淡出。
            // 不再叠加 animateContentSize：AnimatedVisibility 自身已在动画高度，两套高度动画会互相争抢导致抖动
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(durationMillis = 240)) +
                    fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 200)) +
                    fadeOut(animationSpec = tween(durationMillis = 120))
            ) {
                Column(modifier = Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}

/**
 * 分组内条目之间的分割线：
 * - start = 56dp：让线从文字起始处开始，而不是横穿图标容器
 * - outlineVariant 半透明：深色模式下不会出现刺眼亮线
 */
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        // 缩进对齐条目文字起点：14(卡片内边距) + 36(图标容器) + 12(文字左间距) = 62dp
        modifier = Modifier.padding(start = 62.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    /** 长按回调（隐藏菜单等）；null 时不启用长按 */
    onLongClick: (() -> Unit)? = null,
    /** 长按的无障碍动作标签（TalkBack 朗读）；仅配合 onLongClick 使用 */
    onLongClickLabel: String? = null,
    /** 自定义尾部内容（如「恢复默认」操作）；null 时显示默认 ChevronRight */
    trailing: @Composable (() -> Unit)? = null
) {
    val shape = MaterialTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    // 条目不再是独立 Card（已由分组 Card 承载），改为卡片内的一行：保留 ripple 与点击/长按语义
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onLongClickLabel = onLongClickLabel,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // tonal 小容器：surfaceContainerHighest + onSurfaceVariant，与分组头的 primaryContainer 形成层级差
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            // 描述最多 2 行：长描述不再把条目高度撑得参差不齐
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // 尾部统一最小宽度并垂直居中：Switch / 文字值 / ChevronRight 三态视觉对齐
        Box(
            modifier = Modifier.widthIn(min = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 线程数单选行（用于弹窗两列布局，每行占半宽） */
@Composable
private fun RadioThreadRow(
    value: Int,
    threads: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            // ★ 整行可点：只把小圆圈做成点击区时，点数字/空白毫无反应（「点了没反应」的根因之一）
            .clickable { onSelect(value) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // onClick=null：点击由整行驱动，RadioButton 纯展示，避免两套点击逻辑
        RadioButton(selected = threads == value, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$value",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (threads == value) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** 速度限制展示文案：0=不限速；>=1MB/s 显示 MB/s，否则 KB/s */
private fun speedLimitText(bps: Long): String {
    if (bps <= 0) return "不限速"
    return if (bps >= 1024 * 1024) {
        val mb = bps / (1024.0 * 1024.0)
        if (mb >= 10) String.format("%.0f MB/s", mb) else String.format("%.1f MB/s", mb)
    } else {
        "${bps / 1024} KB/s"
    }
}

/** 线程数档位：1~512 十档（默认 32，含 512 上限），弹窗三列网格展示 */
private val threadOptions: List<Int> = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512)

/**
 * 计算临时转存缓存总大小（字节）：
 * - download_tmp 下所有分片子目录（外部缓存，download_tmp/<id>）
 * - 内部缓存目录下 hls_*（HLS 转码流）与 merged_*（合并产物）
 */
private fun cacheSizeBytesOf(context: Context): Long {
    var total = 0L
    val tmpRoot = File(context.externalCacheDir ?: context.cacheDir, "download_tmp")
    tmpRoot.listFiles()?.forEach { dir ->
        if (dir.isDirectory) {
            total += dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }
    }
    context.cacheDir.listFiles()?.forEach { f ->
        if (f.name.startsWith("hls_") || f.name.startsWith("merged_")) {
            total += if (f.isDirectory) {
                f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            } else {
                f.length()
            }
        }
    }
    return total
}

/** 字节数格式化为可读文案（B / KB / MB / GB） */
private fun formatCacheSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
