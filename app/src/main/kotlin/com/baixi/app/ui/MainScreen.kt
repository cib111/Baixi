package com.baixi.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.res.Configuration
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.baixi.app.BaixiApp
import com.baixi.app.data.db.AppDatabase
import com.baixi.app.data.db.DownloadTaskEntity
import com.baixi.app.data.download.DownloadManager
import com.baixi.app.data.download.DownloadService
import com.baixi.app.data.backup.AuthBackupManager
import com.baixi.app.data.network.BaiduApi
import com.baixi.app.data.network.C139Api
import com.baixi.app.data.network.Pan123Api
import com.baixi.app.data.network.QuarkApi
import com.baixi.app.data.network.UCApi
import com.baixi.app.data.network.NetworkPolicy
import com.baixi.app.data.network.XunleiApi
import com.baixi.app.data.prefs.SettingsRepository
import com.baixi.app.data.update.UpdateInfo
import com.baixi.app.data.update.UpdateRepository
import com.baixi.app.data.repository.BaiduAccountRepository
import com.baixi.app.data.repository.BaiduResolveRepository
import com.baixi.app.data.repository.C139AccountRepository
import com.baixi.app.data.repository.C139ResolveRepository
import com.baixi.app.data.repository.Pan123AccountRepository
import com.baixi.app.data.repository.Pan123ResolveRepository
import com.baixi.app.data.repository.ResolveHistoryRepository
import com.baixi.app.data.repository.QuarkAccountRepository
import com.baixi.app.data.repository.QuarkResolveRepository
import com.baixi.app.data.repository.UCAccountRepository
import com.baixi.app.data.repository.UCResolveRepository
import com.baixi.app.data.repository.XunleiAccountRepository
import com.baixi.app.data.repository.XunleiResolveRepository
import com.baixi.app.ui.login.BaiduLoginScreen
import com.baixi.app.ui.login.C139LoginScreen
import com.baixi.app.ui.login.Pan123LoginScreen
import com.baixi.app.ui.login.QuarkLoginScreen
import com.baixi.app.ui.login.UCLoginScreen
import com.baixi.app.ui.login.XunleiLoginScreen
import com.baixi.app.ui.login.XunleiVerifyWebViewScreen
import com.baixi.app.ui.navigation.MainTab
import com.baixi.app.ui.screens.AboutScreen
import com.baixi.app.ui.screens.DirectLinkScreen
import com.baixi.app.ui.screens.DownloadedFilesScreen
import com.baixi.app.ui.screens.AgreementDialog
import com.baixi.app.ui.screens.DownloadScreen
import com.baixi.app.ui.screens.DriveScreen
import com.baixi.app.ui.screens.OnboardingScreen
import com.baixi.app.ui.screens.PlatformThreadsScreen
import com.baixi.app.ui.screens.ResolveHistoryScreen
import com.baixi.app.ui.screens.ResolveScreen
import com.baixi.app.ui.components.UpdateDialog
import com.baixi.app.ui.screens.SettingsScreen
import com.baixi.app.ui.screens.ThemeScreen
import com.baixi.app.ui.viewmodel.BaiduAccountViewModel
import com.baixi.app.ui.viewmodel.BaiduCloudViewModel
import com.baixi.app.ui.viewmodel.C139AccountViewModel
import com.baixi.app.ui.viewmodel.C139CloudViewModel
import com.baixi.app.ui.viewmodel.DownloadViewModel
import com.baixi.app.ui.viewmodel.DriveQuotaViewModel
import com.baixi.app.ui.viewmodel.Pan123AccountViewModel
import com.baixi.app.ui.viewmodel.Pan123CloudViewModel
import com.baixi.app.ui.viewmodel.QuarkAccountViewModel
import com.baixi.app.ui.viewmodel.QuarkCloudViewModel
import com.baixi.app.ui.viewmodel.ResolveViewModel
import com.baixi.app.ui.viewmodel.UCCoudViewModel
import com.baixi.app.ui.viewmodel.UCAccountViewModel
import com.baixi.app.ui.viewmodel.XunleiAccountViewModel
import com.baixi.app.ui.viewmodel.XunleiCloudViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.baixi.app.data.network.HttpClients

/**
 * 主页框架：
 * - 顶部可折叠大标题（LargeTopAppBar），切换 Tab 时标题文字随 Tab 变化，折叠状态不受影响；
 * - 导航 Tab（解析 / 网盘 / 下载 / 设置）：竖屏为底部导航栏（NavigationBar），横屏切换为侧边导航栏（NavigationRail）；
 * - 通过 SaveableStateHolder 保存各页面状态，切换 Tab 再切回来不会重置；
 * - 夸克登录页全屏覆盖展示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Resolve) }
    var showQuarkLogin by rememberSaveable { mutableStateOf(false) }
    var showUCLogin by rememberSaveable { mutableStateOf(false) }
    var showXunleiLogin by rememberSaveable { mutableStateOf(false) }
    var showXunleiVerify by rememberSaveable { mutableStateOf(false) }
    var xunleiVerifyUrl by rememberSaveable { mutableStateOf("") }
    var xunleiVerifyDeviceId by rememberSaveable { mutableStateOf("") }
    var showBaiduLogin by rememberSaveable { mutableStateOf(false) }
    var showC139Login by rememberSaveable { mutableStateOf(false) }
    var showPan123Login by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    // 解析历史页（全屏覆盖层，解析 Tab 顶栏「历史」入口）
    var showResolveHistory by rememberSaveable { mutableStateOf(false) }
    // 分平台下载线程数页（全屏覆盖层，设置页「分平台下载线程数」入口）
    var showPlatformThreads by rememberSaveable { mutableStateOf(false) }
    // 已下载文件管理页 / 直链下载页（全屏覆盖层，设置页「通用」组入口）
    var showDownloadedFiles by rememberSaveable { mutableStateOf(false) }
    var showDirectLink by rememberSaveable { mutableStateOf(false) }
    // 引导页翻页位置：提升到本层（永远在组合中）持久保存——
    // 引导页跳登录页会被整体卸载，页内 rememberSaveable 无法存活，登录返回后重置回第一页；
    // 状态放这里，引导页重组时从外部读回，登录往返仍停在原页
    var onboardingPage by rememberSaveable { mutableIntStateOf(0) }
    val saveableStateHolder = rememberSaveableStateHolder()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 横屏时使用侧边导航栏（NavigationRail），竖屏保持底部导航栏（NavigationBar）
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 首次启动：先弹「用户协议 & 免责协议 & 隐私条款」，同意后才能进入（引导页或主界面）。
    // 首帧组合期同步读 SharedPreferences（两个布尔值，微秒级，与 ThemeController.init 同模式）：
    // 原实现走 LaunchedEffect 异步 + 空壳首帧 + 下一帧补内容，表现为「首页闪动一下」的布局跳动。
    val prefs = remember { context.getSharedPreferences("baixi_prefs", android.content.Context.MODE_PRIVATE) }
    val agreed = remember { prefs.getBoolean("agreement_accepted", false) }
    var showAgreement by remember(agreed) { mutableStateOf(!agreed) }
    // 首次启动引导页（context 声明后检测）
    var showOnboarding by remember(agreed) { mutableStateOf(agreed && !prefs.getBoolean("onboarding_shown", false)) }

    // 首次启动协议弹窗：不同意直接退出应用（弹窗本身禁止返回键/点外关闭）
    if (showAgreement) {
        AgreementDialog(
            onAgree = {
                val prefs = context.getSharedPreferences("baixi_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("agreement_accepted", true).apply()
                showAgreement = false
                // 同意协议后：若还没看过引导页，进入引导页
                showOnboarding = !prefs.getBoolean("onboarding_shown", false)
            },
            onDisagree = {
                // 退出应用：finish 当前 Activity，兜底杀进程
                (context as? android.app.Activity)?.finish()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        )
        return
    }

    // 应用内更新检查：读取白析自己的 GitHub 公开仓库 Releases（见 UpdateRepository.OWNER/REPO）
    val api = remember { QuarkApi() }
    val ucApi = remember { UCApi() }
    val xunleiApi = remember { XunleiApi() }
    val baiduApi = remember { BaiduApi() }
    val c139Api = remember { C139Api() }
    val pan123Api = remember { Pan123Api() }
    val db = remember { AppDatabase.get(context) }
    val settings = remember { SettingsRepository(context) }
    // 启动时同步「忽略 SSL 证书」开关（设置页隐藏菜单持久化，全局客户端即时生效）
    LaunchedEffect(Unit) { HttpClients.ignoreSsl = settings.ignoreSslCert }
    val repository = remember {
        QuarkAccountRepository(db.quarkAccountDao(), api)
    }
    val ucRepository = remember {
        UCAccountRepository(db.ucAccountDao(), ucApi)
    }
    val xunleiRepository = remember {
        XunleiAccountRepository(db.xunleiAccountDao(), xunleiApi)
    }
    val baiduRepository = remember {
        BaiduAccountRepository(db.baiduAccountDao(), baiduApi)
    }
    val c139Repository = remember {
        C139AccountRepository(db.c139AccountDao())
    }
    val pan123Repository = remember {
        Pan123AccountRepository(db.pan123AccountDao(), pan123Api)
    }
    // 解析历史仓库：解析成功自动记录，解析页顶栏「历史」入口读取
    val resolveHistoryRepository = remember {
        ResolveHistoryRepository(db.resolveHistoryDao())
    }
    // 网盘认证备份：打包/恢复各平台凭证
    val backupManager = remember {
        AuthBackupManager(
            db.quarkAccountDao(),
            db.ucAccountDao(),
            db.xunleiAccountDao(),
            db.baiduAccountDao(),
            db.c139AccountDao(),
            db.pan123AccountDao()
        )
    }
    // 下载管理器：应用级进程单例（见 BaixiApp.downloadManager 的说明）。
    // 旧实现在这里 remember 一个实例，Activity 重建会产生第二个实例，导致旧任务的暂停/删除失效、
    // 前台服务被旧实例误停、Activity context 泄漏。
    val downloadManager = remember { BaixiApp.downloadManager }

    // ---------- 应用内更新（GitHub Releases）----------
    // 仓库是公开的 → 走匿名 API，APK 里不含任何 token；换更新源只改 UpdateRepository 的两个常量
    val updateRepository = remember { UpdateRepository(context) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    // 启动自动检查：静默失败（网络问题不打扰），只有「确实有新版本且用户没跳过该版本」才弹窗
    LaunchedEffect(Unit) {
        if (!settings.autoCheckUpdate) return@LaunchedEffect
        val info = updateRepository.check().getOrNull() ?: return@LaunchedEffect
        if (info.version != settings.skippedUpdateVersion) updateInfo = info
    }
    // 手动检查（设置页「检查更新」）：无论结果都用 Snackbar 明确反馈
    val checkUpdateManually: () -> Unit = {
        if (!checkingUpdate) {
            checkingUpdate = true
            scope.launch {
                updateRepository.check()
                    .onSuccess { info ->
                        if (info == null) {
                            SnackbarController.show("已是最新版本 v${updateRepository.currentVersionName}")
                        } else {
                            updateInfo = info
                        }
                    }
                    .onFailure { e ->
                        SnackbarController.show("检查更新失败：${e.message ?: "网络异常"}")
                    }
                checkingUpdate = false
            }
        }
    }

    // Android 9- 写公共 Download 需要 WRITE_EXTERNAL_STORAGE 运行时授权：
    // 下载完成保存前由 DownloadManager.storagePermissionProvider 触发动态申请，授权后自动继续保存
    var pendingStoragePermission by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingStoragePermission?.complete(granted)
        pendingStoragePermission = null
    }
    downloadManager.storagePermissionProvider = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true // Android 10+ MediaStore 无需存储权限
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            val deferred = CompletableDeferred<Boolean>()
            pendingStoragePermission = deferred
            withContext(Dispatchers.Main) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            deferred.await()
        }
    }
    val viewModel: QuarkAccountViewModel = viewModel(
        factory = QuarkAccountViewModel.Factory(repository)
    )
    val ucViewModel: UCAccountViewModel = viewModel(
        factory = UCAccountViewModel.Factory(ucRepository)
    )
    val xunleiViewModel: XunleiAccountViewModel = viewModel(
        factory = XunleiAccountViewModel.Factory(xunleiRepository)
    )
    val baiduViewModel: BaiduAccountViewModel = viewModel(
        factory = BaiduAccountViewModel.Factory(baiduRepository)
    )
    val c139ViewModel: C139AccountViewModel = viewModel(
        factory = C139AccountViewModel.Factory(c139Repository)
    )
    val pan123ViewModel: Pan123AccountViewModel = viewModel(
        factory = Pan123AccountViewModel.Factory(pan123Repository)
    )
    // 夸克云盘浏览：作为网盘 Tab 内容展示（非全屏），cookie 从数据库读取（避免 StateFlow 初始值为空的竞态）；
    // 下载前经 getFreshCookie 惰性刷新 __puus（修复 AlistGo/alist#830 下载 412）
    val quarkCloudViewModel: QuarkCloudViewModel = viewModel(
        factory = QuarkCloudViewModel.Factory(
            api,
            { repository.getFreshCookie() },
            downloadManager
        )
    )
    // UC 网盘云盘浏览：点击已登录的 UC 卡片打开（cookie 从数据库读取）；
    // 取链前经 getFreshCookie 惰性刷新 __puus（与夸克同源，修复取链/直链过期失败）
    val ucCloudViewModel: UCCoudViewModel = viewModel(
        factory = UCCoudViewModel.Factory(
            ucApi,
            { ucRepository.getFreshCookie() },
            downloadManager
        )
    )
    // 迅雷 access_token 过期（401 unauthenticated）自动刷新：refresh_token 换新并持久化（对齐官方 /v1/auth/token 抓包）
    xunleiApi.refreshTokenProvider = { deviceId ->
        val acc = xunleiRepository.getAccount()
        if (acc == null || acc.refreshToken.isBlank()) null
        else xunleiApi.refreshToken(acc.refreshToken, deviceId)?.also { (at, nrt) ->
            xunleiRepository.updateTokens(at, nrt)
        }
    }
    // 迅雷云盘浏览：点击已登录的迅雷卡片打开（access_token/设备指纹/captcha 从数据库读取）
    val xunleiCloudViewModel: XunleiCloudViewModel = viewModel(
        factory = XunleiCloudViewModel.Factory(
            xunleiApi,
            { xunleiRepository.getAccount()?.accessToken },
            { xunleiRepository.getAccount()?.deviceId },
            { xunleiRepository.getAccount()?.captchaToken },
            downloadManager
        )
    )
    // 百度网盘云盘浏览：点击已登录的百度卡片打开（cookie 从数据库读取）
    val baiduCloudViewModel: BaiduCloudViewModel = viewModel(
        factory = BaiduCloudViewModel.Factory(
            baiduApi,
            { baiduRepository.getAccount()?.cookie },
            downloadManager
        )
    )
    // 139 网盘云盘浏览：点击已登录的 139 卡片打开（cookie 从数据库读取）
    val c139CloudViewModel: C139CloudViewModel = viewModel(
        factory = C139CloudViewModel.Factory(
            c139Api,
            { c139Repository.getAccount()?.cookie },
            downloadManager
        )
    )
    // 123 云盘浏览：点击已登录的 123 卡片打开（token 从数据库读取）
    val pan123CloudViewModel: Pan123CloudViewModel = viewModel(
        factory = Pan123CloudViewModel.Factory(
            pan123Api,
            { pan123Repository.getAccount()?.accessToken },
            downloadManager
        )
    )
    // 网盘空间详情：网盘页顶部「空间总览」展示 6 平台容量使用
    val driveQuotaViewModel: DriveQuotaViewModel = viewModel(
        factory = DriveQuotaViewModel.Factory(
            api, { repository.getAccount()?.cookie },
            ucApi, { ucRepository.getAccount()?.cookie },
            xunleiApi,
            { xunleiRepository.getAccount()?.accessToken },
            { xunleiRepository.getAccount()?.deviceId },
            { xunleiRepository.getAccount()?.captchaToken },
            baiduApi, { baiduRepository.getAccount()?.cookie },
            c139Api, { c139Repository.getAccount()?.cookie },
            pan123Api, { pan123Repository.getAccount()?.accessToken }
        )
    )
    val xunleiResolveRepository = remember {
        XunleiResolveRepository(
            api = xunleiApi,
            accountProvider = { xunleiRepository.getAccount()?.accessToken },
            deviceIdProvider = { xunleiRepository.getAccount()?.deviceId },
            captchaProvider = { xunleiRepository.getAccount()?.captchaToken },
            // token 过期（含导入恢复后旧 token 过期）自动用 refresh_token 刷新并持久化
            refreshProvider = {
                val acc = xunleiRepository.getAccount()
                if (acc == null || acc.refreshToken.isBlank()) null
                else xunleiApi.refreshToken(acc.refreshToken, acc.deviceId)?.also { (at, nrt) ->
                    xunleiRepository.updateTokens(at, nrt)
                }
            }
        )
    }
    val baiduResolveRepository = remember {
        BaiduResolveRepository(
            api = baiduApi,
            tempDirNameProvider = { settings.tempDirName }
        )
    }
    val c139ResolveRepository = remember {
        C139ResolveRepository(c139Api)
    }
    val pan123ResolveRepository = remember {
        Pan123ResolveRepository(
            api = pan123Api,
            tokenProvider = { pan123Repository.getAccount()?.accessToken }
        )
    }
    // 夸克解析仓储抽成变量：除了给 ResolveViewModel 用，启动时还要用它清理上次遗留的临时转存目录
    val quarkResolveRepository = remember {
        QuarkResolveRepository(api, tempDirNameProvider = { settings.tempDirName })
    }
    val resolveViewModel: ResolveViewModel = viewModel(
        factory = ResolveViewModel.Factory(
            repository,
            quarkResolveRepository,
            ucRepository,
            UCResolveRepository(
                ucApi,
                tempDirNameProvider = { settings.tempDirName }
            ),
            xunleiRepository,
            xunleiResolveRepository,
            baiduRepository,
            baiduResolveRepository,
            c139Repository,
            c139ResolveRepository,
            pan123Repository,
            pan123ResolveRepository,
            downloadManager,
            resolveHistoryRepository
        )
    )
    val downloadViewModel: DownloadViewModel = viewModel(
        factory = DownloadViewModel.Factory(downloadManager)
    )
    // 「已下载」文件管理页的数据源（供设置页入口打开的全屏覆盖层使用）
    // 设置写入版本号：SharedPreferences 的普通属性不参与 Compose 快照，
    // 直接读 settings.xxx 改完不会重组（表现为「设置有时生效有时没反应」）。
    // 订阅版本号并把它当 remember 的 key，设置页改完值这里立刻跟着刷新。
    val settingsRevision by settings.revision.collectAsState()
    val downloadThreads = remember(settingsRevision) { settings.downloadThreads }

    val allDownloadTasks by downloadViewModel.tasks.collectAsState()
    val quarkAccount by viewModel.quarkAccount.collectAsState()
    val ucAccount by ucViewModel.ucAccount.collectAsState()
    val xunleiAccount by xunleiViewModel.xunleiAccount.collectAsState()
    val baiduAccount by baiduViewModel.baiduAccount.collectAsState()
    val c139Account by c139ViewModel.c139Account.collectAsState()
    val pan123Account by pan123ViewModel.pan123Account.collectAsState()

    // 系统分享入口：MainActivity 经 ShareIntentHub 转入的分享文本 → 关覆盖层、注入解析页并自动解析
    LaunchedEffect(Unit) {
        ShareIntentHub.pendingText.collect { text ->
            if (text != null) {
                ShareIntentHub.consume()
                showAbout = false
                showOnboarding = false
                resolveViewModel.injectPendingLink(text.trim())
                currentTab = MainTab.Resolve
                SnackbarController.show("已收到分享链接，正在自动解析…")
            }
        }
    }

    // 首次下载引导：锁屏保持下载默认开启，但新用户未加入「忽略电池优化」白名单 →引导一次
    var showBatteryGuide by remember { mutableStateOf(false) }
    var batteryGuideShown by remember { mutableStateOf(false) }

    // 解析页发起下载后，自动切换到「下载」Tab
    LaunchedEffect(resolveViewModel.downloadStarted) {
        if (resolveViewModel.downloadStarted) {
            currentTab = MainTab.Download
            resolveViewModel.consumeDownloadStarted()
        }
    }

    // 流量网络下载询问（弹窗三选）：任务启动前由 DownloadManager 经 meteredPrompt 回调询问。
    // Mutex 串行化：多任务同时被拦截时排队询问，防止弹窗互相覆盖导致前一个协程永久挂起
    val meteredMutex = remember { kotlinx.coroutines.sync.Mutex() }
    var meteredPromptInfo by remember { mutableStateOf<NetworkPolicy.TaskInfo?>(null) }
    var meteredPromptDeferred by remember {
        mutableStateOf<CompletableDeferred<NetworkPolicy.Decision>?>(null)
    }
    // 启动恢复：上次会话处于「下载中」的任务按设置自动续传（或统一标记为已暂停）
    LaunchedEffect(Unit) {
        downloadManager.recoverInterrupted(settings.resumeInterruptedOnStart)
    }

    // 启动一次性清理：上次会话被系统杀死/崩溃时，夸克「先转存 → 再取链」流程可能留下 tr_* 临时目录
    // （含完整文件），旧实现只在下载成功/删除任务时清理，进程被杀就永久残留在用户网盘里。
    LaunchedEffect(Unit) {
        runCatching {
            val cookie = repository.getFreshCookie() ?: repository.getAccount()?.cookie
            if (!cookie.isNullOrBlank()) quarkResolveRepository.cleanupOrphanTempDirs(cookie)
        }
    }
    LaunchedEffect(Unit) {
        downloadManager.meteredPrompt = { info ->
            meteredMutex.withLock {
                val deferred = CompletableDeferred<NetworkPolicy.Decision>()
                withContext(Dispatchers.Main) {
                    meteredPromptDeferred = deferred
                    meteredPromptInfo = info
                }
                deferred.await()
            }
        }
    }
    // ★ Activity 销毁/重建兜底：DownloadManager 已是进程单例，而下面这两个 deferred 属于本组合作用域。
    //   若不完成它们，等待「存储权限」或「流量网络弹窗」决定的下载协程会永久挂起，且新 Activity 上
    //   没有任何弹窗可点（表现为任务永远停在等待中）。重建时按「拒绝/CANCEL」收敛，用户可手动重试。
    DisposableEffect(Unit) {
        onDispose {
            runCatching { pendingStoragePermission?.complete(false) }
            pendingStoragePermission = null
            runCatching { meteredPromptDeferred?.complete(NetworkPolicy.Decision.CANCEL) }
            meteredPromptDeferred = null
            meteredPromptInfo = null
        }
    }

    // 统一出口：完成 deferred（解除挂起的下载协程）+ 关闭弹窗
    fun completeMeteredPrompt(decision: NetworkPolicy.Decision) {
        meteredPromptDeferred?.complete(decision)
        meteredPromptDeferred = null
        meteredPromptInfo = null
    }
    meteredPromptInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { completeMeteredPrompt(NetworkPolicy.Decision.CANCEL) },
            title = { Text("当前为流量网络") },
            text = {
                Column {
                    Text(
                        text = "「${info.fileName}」正在等待下载，继续可能消耗较多移动流量。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // 第三选项收进正文区：一次选择，之后流量下直接下载
                    TextButton(onClick = {
                        settings.meteredDownloadChoice = SettingsRepository.METERED_ALWAYS_CONTINUE
                        completeMeteredPrompt(NetworkPolicy.Decision.CONTINUE)
                    }) { Text("不再提示，流量下直接下载") }
                }
            },
            confirmButton = {
                TextButton(onClick = { completeMeteredPrompt(NetworkPolicy.Decision.CONTINUE) }) {
                    Text("继续下载")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    settings.meteredDownloadChoice = SettingsRepository.METERED_ALWAYS_WAIT
                    completeMeteredPrompt(NetworkPolicy.Decision.WAIT_WIFI)
                    SnackbarController.show("已暂停，连接 WiFi 后自动继续")
                }) { Text("等WiFi再下载") }
            }
        )
    }

    // 首次下载任务启动：锁屏保持下载默认开启但未豁免电池优化 →引导一次。
    // 监听任务状态而非 downloadStarted，覆盖解析页/网盘页/手动添加等所有下载入口。
    LaunchedEffect(Unit) {
        downloadViewModel.tasks.collect { tasks ->
            if (!batteryGuideShown && tasks.any {
                    it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                        it.status == DownloadTaskEntity.STATUS_PENDING
                }
            ) {
                batteryGuideShown = true
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (settings.keepDownloadWhenLocked &&
                    pm?.isIgnoringBatteryOptimizations(context.packageName) != true
                ) {
                    showBatteryGuide = true
                }
            }
        }
    }

    // 夸克登录页：全屏覆盖
    if (showQuarkLogin) {
        QuarkLoginScreen(
            viewModel = viewModel,
            onBack = { showQuarkLogin = false },
            onSaved = { showQuarkLogin = false }
        )
        return
    }

    // UC 登录页：全屏覆盖
    if (showUCLogin) {
        UCLoginScreen(
            viewModel = ucViewModel,
            onBack = { showUCLogin = false },
            onSaved = { showUCLogin = false }
        )
        return
    }

    // 迅雷登录页：全屏覆盖（账号+密码，可能触发短信验证）
    if (showXunleiLogin) {
        XunleiLoginScreen(
            viewModel = xunleiViewModel,
            onBack = { showXunleiLogin = false },
            onSaved = { showXunleiLogin = false },
            onVerify = { url, deviceId ->
                // 应用内验证：登录页让位，切到验证 WebView 全屏承载（不再跳外部浏览器）
                xunleiVerifyUrl = url
                xunleiVerifyDeviceId = deviceId
                showXunleiLogin = false
                showXunleiVerify = true
            }
        )
        return
    }

    // 迅雷验证页（应用内 WebView 承载验证面板）：全屏覆盖（兜底承载，核心验证仍走自有短信流）
    if (showXunleiVerify) {
        XunleiVerifyWebViewScreen(
            verifyUrl = xunleiVerifyUrl,
            deviceId = xunleiVerifyDeviceId,
            onResult = { success, _ ->
                showXunleiVerify = false
                showXunleiLogin = true // 回到登录页
                if (success) {
                    // 设备已验证受信任：自动重试密码登录（应直接成功并自动关闭登录页）
                    SnackbarController.show("验证完成，正在自动登录…")
                    xunleiViewModel.retryLoginAfterVerify()
                } else {
                    SnackbarController.show("验证未完成，请重试")
                }
            },
            onBack = {
                showXunleiVerify = false
                showXunleiLogin = true // 返回登录页短信步骤
            }
        )
        return
    }

    // 百度登录页：全屏覆盖（WebView 登录提取 Cookie）
    if (showBaiduLogin) {
        BaiduLoginScreen(
            viewModel = baiduViewModel,
            onBack = { showBaiduLogin = false },
            onSaved = { showBaiduLogin = false }
        )
        return
    }

    // 139 登录页：全屏覆盖（WebView 登录提取 Cookie）
    if (showC139Login) {
        C139LoginScreen(
            viewModel = c139ViewModel,
            onBack = { showC139Login = false },
            onSaved = { showC139Login = false }
        )
        return
    }

    // 123 登录页：全屏覆盖（账号+密码表单登录换 JWT）
    if (showPan123Login) {
        Pan123LoginScreen(
            viewModel = pan123ViewModel,
            onBack = { showPan123Login = false },
            onSaved = { showPan123Login = false }
        )
        return
    }

    // 首次启动引导页：全屏覆盖。
    // 置于全部登录覆盖块之后：引导页内点「去登录」→ 登录页覆盖显示 →
    // 登录成功（onSaved 置 false）自动返回引导页，登录态实时刷新，可继续配置其他平台
    if (showOnboarding) {
        OnboardingScreen(
            initialPage = onboardingPage,
            onPageChange = { onboardingPage = it },
            // 六大平台登录态与登录直达：登录成功自动返回引导页，可继续配置或随时跳过
            quarkLoggedIn = quarkAccount != null,
            ucLoggedIn = ucAccount != null,
            xunleiLoggedIn = xunleiAccount != null,
            baiduLoggedIn = baiduAccount != null,
            c139LoggedIn = c139Account != null,
            pan123LoggedIn = pan123Account != null,
            onLoginQuark = { showQuarkLogin = true },
            onLoginUc = { showUCLogin = true },
            onLoginXunlei = { showXunleiLogin = true },
            onLoginBaidu = { showBaiduLogin = true },
            onLoginC139 = { showC139Login = true },
            onLoginPan123 = { showPan123Login = true },
            onFinish = {
                context.getSharedPreferences("baixi_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("onboarding_shown", true)
                    .apply()
                showOnboarding = false
            }
        )
        return
    }

    // 折叠标题状态提升到本层：跨页面共享，页面切换时折叠/展开状态保持不变
    // 用 exitUntilCollapsed（默认实现，含松手吸附）：滚动时标题先收起再滚内容；
    // 向上滚动回顶部过程中标题保持收起，只有列表到达最顶部后继续下拉（overscroll）才重新展开
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    // 全局 Snackbar 宿主（Material3，替换原 Toast 提示）
    val snackbarHostState = rememberGlobalSnackbarHostState()

    // 主框架与全屏覆盖层（关于页）放在同一 Box：覆盖层带过渡动画
    Box(modifier = Modifier.fillMaxSize()) {
    // 顶部可折叠大标题（竖屏 / 横屏共用）
    val topBarContent: @Composable () -> Unit = {
        LargeTopAppBar(
            title = {
                Text(
                    text = currentTab.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            // 顶栏右侧动作区：
            // - 解析页：「解析历史」入口（时钟图标）
            // - 下载页：「直链下载」「文件管理」常驻入口（这两个功能原来只藏在设置页里，入口太深）
            actions = {
                if (currentTab == MainTab.Resolve) {
                    IconButton(onClick = { showResolveHistory = true }) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "解析历史",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (currentTab == MainTab.Download) {
                    IconButton(onClick = { showDirectLink = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = "直链下载",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showDownloadedFiles = true }) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = "文件管理",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                // 滚动收起后用 surfaceContainer：顶栏与内容区形成层次，不再「糊成一片」
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
    }
    // Tab 内容区（竖屏 / 横屏共用）：每个页面独立保存状态，切换 Tab 再切回来不丢失；带 Material3 过渡动画（按 Tab 顺序决定方向）
    val tabContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                // 根据 Tab 顺序决定滑动方向：向右切（新Tab在右边）→ 新页从右滑入；向左切反向
                val forward = targetState.ordinal > initialState.ordinal
                if (forward) {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 4 })
                } else {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 4 })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 4 })
                }
            },
            label = "mainTab"
        ) { tab ->
            saveableStateHolder.SaveableStateProvider(tab) {
                when (tab) {
                    MainTab.Resolve -> ResolveScreen(
                        scrollBehavior,
                        resolveViewModel,
                        quarkCloudViewModel,
                        xunleiCloudViewModel,
                        baiduCloudViewModel,
                        c139CloudViewModel,
                        ucCloudViewModel,
                        pan123CloudViewModel
                    )
                    MainTab.Drive -> DriveScreen(
                        scrollBehavior = scrollBehavior,
                        quarkAccount = quarkAccount,
                        ucAccount = ucAccount,
                        xunleiAccount = xunleiAccount,
                        baiduAccount = baiduAccount,
                        c139Account = c139Account,
                        pan123Account = pan123Account,
                        quarkCloudViewModel = quarkCloudViewModel,
                        ucCloudViewModel = ucCloudViewModel,
                        xunleiCloudViewModel = xunleiCloudViewModel,
                        baiduCloudViewModel = baiduCloudViewModel,
                        c139CloudViewModel = c139CloudViewModel,
                        pan123CloudViewModel = pan123CloudViewModel,
                        driveQuotaViewModel = driveQuotaViewModel,
                        onQuarkLogin = { showQuarkLogin = true },
                        onQuarkLogout = { viewModel.logout() },
                        onDownloadStarted = { currentTab = MainTab.Download },
                        onUCLogin = { showUCLogin = true },
                        onUCLogout = { ucViewModel.logout() },
                        onXunleiLogin = { showXunleiLogin = true },
                        onXunleiLogout = { xunleiViewModel.logout() },
                        onBaiduLogin = { showBaiduLogin = true },
                        onBaiduLogout = { baiduViewModel.logout() },
                        onC139Login = { showC139Login = true },
                        onC139Logout = { c139ViewModel.logout() },
                        onPan123Login = { showPan123Login = true },
                        onPan123Logout = { pan123ViewModel.logout() }
                    )
                    MainTab.Download -> DownloadScreen(scrollBehavior, downloadViewModel)
                    MainTab.Settings -> SettingsScreen(
                        scrollBehavior = scrollBehavior,
                        downloadThreads = downloadThreads,
                        onThreadsChange = { settings.downloadThreads = it },
                        onThemeClick = { showTheme = true },
                        onPlatformThreadsClick = { showPlatformThreads = true },
                        onAboutClick = { showAbout = true },
                        backupManager = backupManager,
                        // clearCache 为重 IO（suspend），丢协程异步执行，不阻塞主线程
                        onClearCache = { downloadManager.clearCache() },
                        onOpenDownloadedFiles = { showDownloadedFiles = true },
                        onOpenDirectLink = { showDirectLink = true },
                        currentVersionName = updateRepository.currentVersionName,
                        onCheckUpdate = checkUpdateManually
                    )
                }
            }
        }
    }

    if (isLandscape) {
        // 横屏：左侧侧边导航栏（NavigationRail）+ 右侧顶栏 & 内容
        // Surface 承载背景（替代裸 Box + background）：Surface 自动把内容色设为 onSurface，
        // 裸容器下无 color 的 Text 会回退 M3 默认纯黑——深色模式黑底黑字不可读
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                MainNavigationRail(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    topBarContent()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        tabContent()
                    }
                }
            }
            // 全局 Snackbar（横屏无底部栏，悬浮底部居中）
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
    } else {
        // 竖屏：Scaffold + 底部导航栏
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = { topBarContent() },
            bottomBar = {
                MainBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                tabContent()
            }
        }
    }

    // 解析历史：叠加覆盖层（淡入 + 轻微缩放过渡，与关于页同款动效）
    AnimatedVisibility(
        visible = showResolveHistory,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        ResolveHistoryScreen(
            repository = resolveHistoryRepository,
            onReopen = { url ->
                showResolveHistory = false
                // 注入待解析链接 → 解析页常驻 LaunchedEffect 自动填入并解析（与系统分享同链路）
                resolveViewModel.injectPendingLink(url)
                currentTab = MainTab.Resolve
                SnackbarController.show("已从历史填入链接，正在自动解析…")
            },
            onBack = { showResolveHistory = false }
        )
    }

    // 关于白析：叠加覆盖层（淡入 + 轻微缩放过渡）
    AnimatedVisibility(
        visible = showAbout,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        AboutScreen(
            onBack = { showAbout = false },
            onPreviewOnboarding = {
                context.getSharedPreferences("baixi_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("onboarding_shown", false)
                    .apply()
                showAbout = false
                showOnboarding = true
            }
        )
    }

    // 主题与外观：叠加覆盖层（淡入 + 轻微缩放过渡）
    AnimatedVisibility(
        visible = showTheme,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        ThemeScreen(
            onBack = { showTheme = false }
        )
    }

    // 分平台下载线程数：叠加覆盖层（淡入 + 轻微缩放过渡）
    AnimatedVisibility(
        visible = showPlatformThreads,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        PlatformThreadsScreen(
            settings = settings,
            globalThreads = downloadThreads,
            onBack = { showPlatformThreads = false }
        )
    }

    // 已下载文件管理页：叠加覆盖层（入口在设置页「通用」组）
    AnimatedVisibility(
        visible = showDownloadedFiles,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        DownloadedFilesScreen(
            // 只展示真正落盘成功的任务（savePath 为空的已完成任务无法定位文件）
            tasks = allDownloadTasks.filter {
                it.status == DownloadTaskEntity.STATUS_COMPLETED && it.savePath.isNotBlank()
            },
            onBack = { showDownloadedFiles = false },
            onRename = { task, newName ->
                scope.launch { runCatching { db.downloadTaskDao().updateFileName(task.id, newName) } }
            },
            onDelete = { list ->
                // 复用下载管理器的删除：会一并清掉本地文件与云端临时转存
                list.forEach { downloadManager.remove(it.id, deleteLocal = true) }
            }
        )
    }

    // 直链下载页：叠加覆盖层（入口在设置页「通用」组）
    AnimatedVisibility(
        visible = showDirectLink,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        DirectLinkScreen(
            onBack = { showDirectLink = false },
            onEnqueue = { url, name, headers ->
                downloadViewModel.enqueue(url, name, headers)
                showDirectLink = false
                // 入队后跳到下载页，用户能立刻看到任务
                currentTab = MainTab.Download
            }
        )
    }
    }

    // 首次下载引导：加入「忽略电池优化」白名单（锁屏保持下载生效的前提）
    if (showBatteryGuide) {
        AlertDialog(
            onDismissRequest = { showBatteryGuide = false },
            title = { Text("保持后台下载") },
            text = {
                Text(
                    text = "「锁屏后保持下载」已开启，但应用尚未加入「忽略电池优化」白名单，息屏后可能被系统中断下载。是否前往系统设置？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryGuide = false
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
                TextButton(onClick = { showBatteryGuide = false }) { Text("暂不") }
            }
        )
    }

    // 发现新版本弹窗：启动自动检查 / 手动检查命中时弹出
    updateInfo?.let { info ->
        UpdateDialog(
            info = info,
            currentVersion = updateRepository.currentVersionName,
            onDownload = {
                updateInfo = null
                // 复用应用自身的下载器：下载页可见进度、通知栏有进度，完成后自动拉起安装界面
                scope.launch {
                    runCatching {
                        downloadManager.enqueue(
                            url = info.apkUrl,
                            fileName = "Baixi-v${info.version}.apk",
                            size = info.apkSize,
                            onComplete = { savedPath ->
                                DownloadService.startInstall(context, savedPath)
                            }
                        )
                    }.onFailure { e ->
                        SnackbarController.show("更新包下载失败：${e.message ?: "未知错误"}")
                    }
                }
                currentTab = MainTab.Download
                SnackbarController.show("已开始下载 v${info.version}，完成后会自动打开安装界面")
            },
            onSkipVersion = {
                settings.skippedUpdateVersion = info.version
                updateInfo = null
                SnackbarController.show("已跳过 v${info.version}，之后可在设置里手动检查")
            },
            onDismiss = { updateInfo = null }
        )
    }
}

/**
 * 底部导航栏（竖屏）：4 个主 Tab（解析 / 网盘 / 下载 / 设置）。
 */
@Composable
private fun MainBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Column {
        // 细分隔线：底部导航与内容区之间一条极浅的分割，避免两块同色区域连成一片
        HorizontalDivider(
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            MainTab.values().forEach { tab ->
                NavigationBarItem(
                    selected = currentTab == tab,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title
                        )
                    },
                    label = { Text(tab.title) }
                )
            }
        }
    }
}

/**
 * 侧边导航栏（横屏）：同 4 个主 Tab，未选中项只显示图标，节省横向空间。
 */
@Composable
private fun MainNavigationRail(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    NavigationRail {
        MainTab.values().forEach { tab ->
            NavigationRailItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.title
                    )
                },
                label = { Text(tab.title) },
                alwaysShowLabel = currentTab == tab
            )
        }
    }
}