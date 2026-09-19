package com.baixi.app.ui.screens

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.baixi.app.R
import kotlinx.coroutines.launch

/** 引导页平台条目：登录态 + 登录直达回调 */
private data class OnboardPlatform(
    val name: String,
    val icon: ImageVector,
    val loggedIn: Boolean,
    val onLogin: () -> Unit
)

/**
 * 首次启动引导页（三页横滑）：
 * - 页 0「认识白析」：软件介绍精排（功能 2×2 网格 + 免费承诺 + 开源声明与源码下载，AGPL-3.0 合规）；
 * - 页 1「配置账号」：六大网盘登录引导，登录成功自动返回本页可继续添加，也可暂不登录；
 * - 页 2「权限准备」：下载通知 / 存储权限 / 电池优化豁免，状态实时检查 + 一键申请；
 * - 右上角与底部均可跳过，底部主按钮随页切换（下一步 → 完成，开始使用）。
 */
@Composable
fun OnboardingScreen(
    /** 初始页码（外部持久化）：登录往返后引导页重组仍回到原页 */
    initialPage: Int,
    /** 翻页回写（外部持久化）：滑动或点按钮切页时同步给调用方 */
    onPageChange: (Int) -> Unit,
    quarkLoggedIn: Boolean,
    ucLoggedIn: Boolean,
    xunleiLoggedIn: Boolean,
    baiduLoggedIn: Boolean,
    c139LoggedIn: Boolean,
    pan123LoggedIn: Boolean,
    onLoginQuark: () -> Unit,
    onLoginUc: () -> Unit,
    onLoginXunlei: () -> Unit,
    onLoginBaidu: () -> Unit,
    onLoginC139: () -> Unit,
    onLoginPan123: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 3 })
    // 翻页位置回写外部（含手势滑动）：登录往返后由外部 initialPage 恢复
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChange(it) }
    }

    // 入场过渡：覆盖层是裸切换，直进直出显生硬；首帧淡入 + 轻缩放抹平突兀感（从登录页返回同样柔和浮现）
    val enterAlpha = remember { Animatable(0f) }
    val enterScale = remember { Animatable(0.96f) }
    LaunchedEffect(Unit) {
        launch { enterAlpha.animateTo(1f, tween(280)) }
        launch { enterScale.animateTo(1f, tween(280)) }
    }

    // ---------- 页 2 权限状态：申请回调 / 从系统设置返回（ON_RESUME）时重新读取 ----------
    var permTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // permTick 参与表达式以建立重组依赖：tick 变化 → 重读系统 API 刷新状态
    val notifGranted = permTick >= 0 && (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    val storageGranted = permTick >= 0 && (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batteryGranted = permTick >= 0 &&
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permTick++ }
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permTick++ }
    // 电池优化豁免：跳系统设置页，返回后 ON_RESUME 自动刷新状态
    val requestBattery: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    val platforms = listOf(
        OnboardPlatform("夸克网盘", Icons.Outlined.CloudDownload, quarkLoggedIn, onLoginQuark),
        OnboardPlatform("UC 网盘", Icons.Outlined.Cloud, ucLoggedIn, onLoginUc),
        OnboardPlatform("迅雷", Icons.Outlined.CloudQueue, xunleiLoggedIn, onLoginXunlei),
        OnboardPlatform("百度网盘", Icons.Outlined.CloudCircle, baiduLoggedIn, onLoginBaidu),
        OnboardPlatform("139 邮箱网盘", Icons.Outlined.CloudSync, c139LoggedIn, onLoginC139),
        OnboardPlatform("123 云盘", Icons.Outlined.CloudDone, pan123LoggedIn, onLoginPan123)
    )

    // Surface 承载整页背景：不能用裸 Box + Modifier.background——
    // Modifier.background 只画色不设置 LocalContentColor，无 color 的 Text
    // 会回退到 M3 默认纯黑（浅色模式碰巧可读，深色模式黑底黑字不可读）
    Surface(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = enterAlpha.value
                scaleX = enterScale.value
                scaleY = enterScale.value
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏：右上角常驻「跳过」（statusBarsPadding 适配状态栏）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinish) { Text("跳过") }
            }

            // 三页横滑：介绍 → 配置账号 → 权限准备
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> IntroPage()
                    1 -> AccountPage(
                        platforms = platforms,
                        onSkipAccount = { scope.launch { pagerState.animateScrollToPage(2) } }
                    )
                    else -> PermissionPage(
                        notifGranted = notifGranted,
                        storageGranted = storageGranted,
                        batteryGranted = batteryGranted,
                        onRequestNotif = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestStorage = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        },
                        onRequestBattery = requestBattery
                    )
                }
            }

            // 圆点指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(3) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 5.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    )
                }
            }

            // 主按钮：页随动（页 0/1 → 下一步；页 2 → 完成，开始使用）
            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(52.dp)
            ) {
                Text(
                    text = if (pagerState.currentPage < 2) "下一步" else "完成，开始使用",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/** 页 0：认识白析 —— 介绍精排（功能 2×2 网格 + 免费承诺 + 开源/源码合并卡） */
@Composable
private fun IntroPage() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.mipmap.icon),
            contentDescription = "白析图标",
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "白析",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "网盘分享链接 · 解析与高速下载",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        // 免费承诺（原整卡信息瘦身成一行徽标）
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "完全免费 · 无广告 · 无内购",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 功能特性 2×2 网格
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureCell(Icons.Outlined.Link, "一键解析", "分享链接自动识别", Modifier.weight(1f))
            FeatureCell(Icons.Outlined.Speed, "高速下载", "多线程分片 + 断点续传", Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureCell(Icons.Outlined.Storage, "多网盘", "六大主流网盘统一管理", Modifier.weight(1f))
            FeatureCell(Icons.Outlined.Lock, "隐私安全", "登录凭证仅存本机", Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 开源声明 + 源码下载（AGPL-3.0 合规必需，合并展示）
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "开源项目 · GNU AGPL-3.0",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "本软件基于开源项目 CYQawa/YunX（GNU AGPL-3.0）二次开发；源码、版本更新与问题反馈见 GitHub 仓库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/cib111/Baixi")
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("开源仓库")
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/cib111/Baixi/issues")
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("问题反馈")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** 功能特性格：圆图标 + 标题 + 描述（2×2 网格单元） */
@Composable
private fun FeatureCell(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 页 1：配置账号 —— 六大网盘登录引导（登录成功自动返回本页，可继续添加或暂不登录） */
@Composable
private fun AccountPage(
    platforms: List<OnboardPlatform>,
    onSkipAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "登录网盘账号",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "登录后即可解析对应平台的分享链接\n凭证仅保存在本机，不上传任何服务器",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            platforms.forEach { item -> AccountRow(item) }
        }

        Spacer(modifier = Modifier.height(14.dp))
        TextButton(onClick = onSkipAccount) {
            Text("暂不登录，直接下一步", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = "登录成功会自动返回本页，可继续添加其他平台",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 平台账号行：图标 + 名称 + 登录态（已登录徽标 / 未登录「去登录」直达） */
@Composable
private fun AccountRow(item: OnboardPlatform) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = if (item.loggedIn) "已登录 · 可直接解析下载" else "未登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.loggedIn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (item.loggedIn) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "已登录",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = item.onLogin,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("去登录", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 页 2：权限准备 —— 下载通知 / 存储权限 / 电池优化豁免（按系统版本显示，可稍后开启） */
@Composable
private fun PermissionPage(
    notifGranted: Boolean,
    storageGranted: Boolean,
    batteryGranted: Boolean,
    onRequestNotif: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestBattery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "权限准备",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "以下权限用于保障下载体验，全部可选\n跳过后可在系统设置中随时开启",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Android 13+ 通知权限（低版本默认可通知，无需引导）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    icon = Icons.Outlined.Notifications,
                    title = "下载完成通知",
                    description = "通知栏显示下载进度与完成提醒",
                    granted = notifGranted,
                    onAction = onRequestNotif
                )
            }
            // Android 9- 存储权限（10+ 走 MediaStore 无需授权）
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                PermissionRow(
                    icon = Icons.Outlined.Folder,
                    title = "存储权限",
                    description = "保存下载文件到公共下载目录",
                    granted = storageGranted,
                    onAction = onRequestStorage
                )
            }
            PermissionRow(
                icon = Icons.Outlined.BatteryChargingFull,
                title = "忽略电池优化",
                description = "锁屏下载不被系统省电策略中断",
                granted = batteryGranted,
                onAction = onRequestBattery
            )
        }
    }
}

/** 权限行：图标 + 名称 + 说明（已开启徽标 / 「去授权」按钮） */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "已开启",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            } else {
                TextButton(onClick = onAction) { Text("去授权") }
            }
        }
    }
}
