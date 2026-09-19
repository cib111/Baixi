package com.baixi.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.Window
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 文件应用内预览（全屏覆盖层）：
 * - 图片（流式解码 + 最长边 2048 降采样）/ 视频（VideoView + 系统控制条）/ 音频（MediaPlayer，含 MIDI）/
 *   PDF（框架 PdfRenderer，优先描述符直读）/ 文本（UTF-8 自动回退 GBK，只读前 128KB）；
 * - 渲染器统一接收 `savePath`（content:// 或绝对路径），内部经 [previewUri] 解析后**流式读取**：
 *   默认下载目录（MediaStore/SAF）给的 content:// 不再预先整份复制到 cacheDir，点预览立即有反馈，
 *   大文件也能应用内预览；仅 PDF 在 provider 描述符不可 seek 时才回退落缓存（见 [copyToPreviewCache]）；
 * - 右上角常驻「用其他应用打开」兜底，任何类型都可交由外部应用。
 */
@Composable
fun FilePreviewScreen(
    fileName: String,
    filePath: String?,
    onOpenExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    val kind = remember(fileName) { previewKindOf(fileName) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 视频全屏：横屏 + 隐藏系统栏（顶栏保留，便于一键退出与外部打开）
    var fullscreen by remember(fileName) { mutableStateOf(false) }
    val isVideo = kind == PreviewKind.VIDEO

    // 全屏进出统一放在 Dialog 内部处理（见下方 DisposableEffect）：
    // ★ 预览是 Compose Dialog，它有自己的 window；只对 Activity 的 window 隐藏系统栏是无效的。

    // 返回键：全屏时先退全屏，再按才关闭预览。
    // Dialog 的 dismissOnBackPress 关掉，统一由这里处理，避免返回键被平台对话框先消费掉。
    BackHandler {
        if (fullscreen) fullscreen = false else onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)
    ) {
        // Surface 承载背景（替代裸 Column + background）：Surface 自动把内容色设为 onSurface，
        // 裸容器下无 color 的 Text 会回退 M3 默认纯黑——深色模式黑底黑字不可读。
        // 视频用纯黑底：画面更清楚，也让全屏时上下不会露出浅色条。
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isVideo) Color.Black else MaterialTheme.colorScheme.surface
        ) {
        // ★ 全屏必须作用于「对话框自己的 window」：预览是 Compose Dialog，它拥有独立 window，
        //   此前只对 Activity 的 window 调 insetsController，系统栏根本不会隐藏 —— 这就是
        //   「点了全屏没反应」的根因。取 DialogWindowProvider 的 window，取不到再退回 Activity。
        val fullscreenWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            ?: activity?.window
        DisposableEffect(fullscreen) {
            if (fullscreen) {
                // SENSOR_LANDSCAPE：横屏但不锁死左右方向；旋转失败的老设备上，
                // 下面的「隐藏系统栏 + 隐藏顶栏」依然会给出明确的全屏效果
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                applySystemBars(fullscreenWindow, visible = false)
                // 双保险：某些 ROM 的对话框 window 与 Activity window 的 insets 归属不同
                if (fullscreenWindow !== activity?.window) {
                    applySystemBars(activity?.window, visible = false)
                }
            }
            onDispose {
                if (fullscreen) {
                    applySystemBars(fullscreenWindow, visible = true)
                    applySystemBars(activity?.window, visible = true)
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶栏：返回 + 文件名 +（视频）全屏 + 外部打开
            // 全屏时整条顶栏隐藏（改由画面右上角的浮动按钮退出），让画面真正铺满
            val barContentColor = if (isVideo) Color.White else MaterialTheme.colorScheme.onSurface
            if (!fullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fullscreen) Modifier else Modifier.statusBarsPadding())
                    .background(
                        if (isVideo) Color.Black.copy(alpha = 0.32f) else Color.Transparent
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = barContentColor
                    )
                }
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = barContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 视频：全屏/退出全屏（系统栏隐藏 + 横屏；退出时复原方向）
                if (isVideo) {
                    TextButton(onClick = { fullscreen = !fullscreen }) {
                        Text(
                            text = if (fullscreen) "退出全屏" else "全屏",
                            color = barContentColor,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                // 常驻外部打开入口：与内容类型无关，任何渲染失败都能从这里兜底
                IconButton(onClick = onOpenExternal) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = "用其他应用打开",
                        tint = if (isVideo) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
            }
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (kind) {
                    PreviewKind.IMAGE -> ImagePreview(filePath, onOpenExternal)
                    PreviewKind.VIDEO -> VideoPreview(filePath, onOpenExternal)
                    PreviewKind.AUDIO -> AudioPreview(fileName, filePath)
                    PreviewKind.PDF -> PdfPreview(filePath, onOpenExternal)
                    PreviewKind.TEXT -> TextPreview(filePath, onOpenExternal)
                    PreviewKind.UNSUPPORTED -> UnsupportedPreview(onOpenExternal)
                }
                // 全屏时系统栏与顶栏都隐藏了，必须留一个不依赖系统栏的出口
                if (fullscreen) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.42f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        IconButton(onClick = { fullscreen = false }) {
                            Icon(
                                imageVector = Icons.Outlined.FullscreenExit,
                                contentDescription = "退出全屏",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/** 从 Compose 的 LocalContext 里取出宿主 Activity（可能是 ContextWrapper 包裹） */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 显示/隐藏系统栏（全屏用）；拿不到 window 时静默忽略，不影响其它功能 */
private fun applySystemBars(window: Window?, visible: Boolean) {
    if (window == null) return
    // 退出预览时对话框 window 可能已在销毁中，这里兜一层，避免恢复系统栏时抛异常
    runCatching {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

// ---------- 路径解析（全链路 URI 感知） ----------

/**
 * 把保存路径统一解析为可交给 ContentResolver 读取的 Uri：
 * - `content://`（MediaStore / SAF 自定义目录，Android 10+ 默认下载位置就是这种）→ 原样解析，
 *   全程流式读取，不复制文件；
 * - 绝对路径（Android 9- 的公共 Download 目录、Download/xxx 等）→ File.exists() 校验后经
 *   FileProvider 转成 content:// 再读（Android 7.0+ 禁止向外暴露 file:// URI，统一走 provider）；
 * - 空串 / 文件不存在 / FileProvider 未配置该目录（getUriForFile 抛 IllegalArgumentException）→ null，
 *   调用方进错误态并用顶栏「用其他应用打开」兜底。
 * 纯函数式解析：不读文件内容，不落盘。
 */
private fun previewUri(context: Context, savePath: String): Uri? {
    if (savePath.isBlank()) return null
    if (savePath.startsWith("content://")) {
        return runCatching { Uri.parse(savePath) }.getOrNull()
    }
    // 兼容 file:// 前缀的绝对路径写法（老任务记录里可能出现）
    val rawPath = if (savePath.startsWith("file://")) {
        runCatching { Uri.parse(savePath).path }.getOrNull() ?: return null
    } else {
        savePath
    }
    if (!File(rawPath).exists()) return null
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(rawPath))
    }.getOrNull()
}

// ---------- 类型判定 ----------

internal enum class PreviewKind { IMAGE, VIDEO, AUDIO, PDF, TEXT, UNSUPPORTED }

internal fun previewKindOf(fileName: String): PreviewKind = when (
    fileName.substringAfterLast('.', "").lowercase()
) {
    // 图片：heic/heif（Android 9+ 解码）、avif（Android 12+ 解码），老机型失败走错误态兜底
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif" -> PreviewKind.IMAGE
    // 视频：ts（HLS/传输流切片）、3g2/3gpp，编解码不支持时走错误态 + 外部打开兜底
    "mp4", "mkv", "webm", "3gp", "3gpp", "3g2", "avi", "mov", "m4v", "ts" -> PreviewKind.VIDEO
    // 音频：mid/midi（MediaPlayer 原生支持 MIDI 合成）
    "mp3", "wav", "flac", "aac", "ogg", "m4a", "amr", "opus", "mid", "midi" -> PreviewKind.AUDIO
    "pdf" -> PreviewKind.PDF
    // 文本：源码 / 配置 / 数据 / 字幕 / 文档附注（nfo）等纯文本格式
    "txt", "md", "log", "json", "ndjson", "xml", "html", "htm", "csv", "tsv", "sql",
    "kt", "kts", "java", "py", "js", "jsx", "css", "scss", "less", "vue",
    "c", "cpp", "h", "hpp", "cs", "go", "rs", "swift", "dart", "rb", "php", "lua", "pl",
    "scala", "groovy", "gradle", "sh", "bash", "bat", "ps1",
    "yml", "yaml", "toml", "ini", "properties", "conf", "cfg", "env",
    "srt", "ass", "ssa", "vtt", "lrc", "nfo", "diff", "patch" -> PreviewKind.TEXT
    else -> PreviewKind.UNSUPPORTED
}

/** 该文件名是否支持应用内预览（下载页决定是否显示预览按钮用） */
fun supportsPreview(fileName: String): Boolean =
    previewKindOf(fileName) != PreviewKind.UNSUPPORTED

// ---------- 通用：加载中 / 加载失败态 ----------

/** 统一的加载态：居中转圈 + 一行说明（避免只转圈让人不知道在等什么） */
@Composable
private fun PreviewLoading(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- 通用：加载失败态 ----------

@Composable
private fun PreviewError(message: String, onOpenExternal: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(40.dp)
        )
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onOpenExternal != null) {
            Button(onClick = onOpenExternal) { Text("用其他应用打开") }
        }
    }
}

// ---------- 图片 ----------

@Composable
private fun ImagePreview(savePath: String?, onOpenExternal: () -> Unit) {
    if (savePath == null) {
        PreviewLoading("正在加载图片…")
        return
    }
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = savePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = previewUri(context, savePath) ?: error("无法解析文件地址")
                // 第一次开流：只读尺寸（inJustDecodeBounds 不解码像素，decodeStream 返回 null 属正常），读完立即关流
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                (context.contentResolver.openInputStream(uri) ?: error("无法打开文件流")).use { input ->
                    BitmapFactory.decodeStream(input, null, bounds)
                }
                // 连尺寸都读不到（不是图片 / 编码不支持，如旧机型的 heic/avif）→ 提前失败
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("无法识别图片尺寸")
                // 降采样解码：最长边 2048，防止超大图 OOM
                var sample = 1
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxSide / (sample * 2) >= 2048) sample *= 2
                // 第二次重新开流做真正解码：InputStream 不可复用/不可回退，必须重开
                val decoded = (context.contentResolver.openInputStream(uri) ?: error("无法打开文件流"))
                    .use { input ->
                        BitmapFactory.decodeStream(
                            input,
                            null,
                            BitmapFactory.Options().apply { inSampleSize = sample }
                        )
                    }
                decoded ?: error("图片解码失败")
            }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp == null) {
        // 地址解析失败 / 流打不开 / 解码失败（heic、avif 在旧机型上可能不支持）统一走错误态
        PreviewError("无法加载图片", onOpenExternal)
    } else {
        // 手势状态：双指缩放（scale）+ 拖动（offset），双击在 1x / 2.5x 间切换
        var scale by remember(savePath) { mutableFloatStateOf(1f) }
        var offset by remember(savePath) { mutableStateOf(Offset.Zero) }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "预览图片",
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(savePath) {
                        // 双指缩放 + 拖动：缩放范围 1x~5x；缩回 1x 时偏移归零（自然回位）
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(savePath) {
                        // 双击：< 2x 放大到 2.5x，否则复位到 1x
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}

// ---------- 视频 ----------

@Composable
private fun VideoPreview(savePath: String?, onOpenExternal: () -> Unit) {
    if (savePath == null) {
        PreviewLoading("正在加载视频…")
        return
    }
    val context = LocalContext.current
    // content:// 直接交给 VideoView 播放；绝对路径经 FileProvider 转成 content://，都不复制文件
    val resolved = remember(savePath) { previewUri(context, savePath) }
    var failed by remember(savePath) { mutableStateOf(false) }
    if (resolved == null) {
        // 地址解析失败（文件不存在 / FileProvider 未配置该目录）：错误态 + 外部打开兜底
        PreviewError("无法播放该视频", onOpenExternal = onOpenExternal)
        return
    }
    val videoUri: Uri = resolved
    if (failed) {
        // 播放失败（编解码器不支持/文件损坏）：错误态 + 外部打开兜底
        // 注意：VideoView 是 SurfaceView 会盖在 Compose 层之上，失败时必须整个移除而不是叠加
        PreviewError("无法播放该视频", onOpenExternal = onOpenExternal)
    } else {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(videoUri)
                    setMediaController(MediaController(ctx))
                    setOnPreparedListener { it.isLooping = false; start() }
                    // 返回 true 表示已自行处理，不再弹系统错误对话框
                    setOnErrorListener { _, _, _ -> failed = true; true }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ---------- 音频 ----------

@Composable
private fun AudioPreview(fileName: String, savePath: String?) {
    if (savePath == null) {
        PreviewLoading("正在加载音频…")
        return
    }
    val context = LocalContext.current
    val uri = remember(savePath) { previewUri(context, savePath) }
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val player = remember {
        MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
    }
    LaunchedEffect(savePath) {
        if (uri == null) {
            failed = true
            return@LaunchedEffect
        }
        val audioUri: Uri = uri
        withContext(Dispatchers.IO) {
            runCatching {
                // setDataSource(context, audioUri)：content:// 与 FileProvider URI 都能直接读，不复制文件
                player.setDataSource(context, audioUri)
                player.prepare()
                player.start()
            }.onSuccess { playing = true }.onFailure { failed = true }
        }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { player.stop(); player.release() } }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(64.dp)
        )
        Text(
            fileName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (failed) {
            Text("无法播放该音频", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TextButton(onClick = {
                if (player.isPlaying) { player.pause(); playing = false }
                else { player.start(); playing = true }
            }) { Text(if (playing) "暂停" else "播放") }
        }
        Text(
            "音频预览 · 可点击右上角用其他应用打开",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- PDF（框架 PdfRenderer，逐页渲染 + 翻页） ----------

/**
 * PDF 专用单线程调度器。
 *
 * 为什么必须固定在同一个线程：PdfRenderer 内部是 native pdfium 句柄，**不是线程安全的**。
 * 跨线程调用（A 线程构造、B 线程渲染）、或在一页还没渲染完时就 close()，会直接 native 崩溃
 * （SIGSEGV / use-after-free，Kotlin 的 runCatching 根本拦不住）。这里把「打开 / 渲染 / 关闭」
 * 全部排到这一个线程上串行执行，从根上消除这类崩溃。
 */
private val pdfDispatcher: CoroutineDispatcher =
    Executors.newSingleThreadExecutor { r ->
        Thread(r, "baixi-pdf").apply { isDaemon = true }
    }.asCoroutineDispatcher()

/** 关闭动作的兜底作用域：onDispose 不能挂起，只能把 close 丢回 PDF 线程排队执行 */
private val pdfCleanupScope = CoroutineScope(SupervisorJob() + pdfDispatcher)

/** 单页渲染的最长边像素上限（A4 约 2.4 倍，清晰且单页位图约 12MB） */
private const val PDF_RENDER_MAX_SIDE = 2048

/** 单页位图总像素上限（约 400 万像素 / 16MB）：超大页面按比例缩到上限内，避免 OOM 崩溃 */
private const val PDF_RENDER_MAX_PIXELS = 4_000_000

/** 渲染缩放上下限：小页面最多放大 2.5 倍，超大页面允许缩小 */
private const val PDF_RENDER_MIN_SCALE = 0.15f
private const val PDF_RENDER_MAX_SCALE = 2.5f

/** PDF 回退缓存目录名（cacheDir/preview）与保留时长（24 小时） */
private const val PDF_FALLBACK_DIR = "preview"
private const val PDF_FALLBACK_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000

/**
 * ★ PDF 回退路径专用（不是默认路径）：把 provider 里的文件复制到 cacheDir/preview/。
 * 只有 provider 的描述符无法喂给 PdfRenderer 时才会调用；其余渲染器全程流式读取、不复制。
 * 复制前顺手清理超过 24 小时的旧回退文件，避免长期占用内部存储；失败返回 null（调用方进错误态）。
 */
private fun copyToPreviewCache(context: Context, uri: Uri): File? = runCatching {
    val resolver = context.contentResolver
    val displayName = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
    val dir = File(context.cacheDir, PDF_FALLBACK_DIR)
    runCatching {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { old ->
            if (now - old.lastModified() > PDF_FALLBACK_CACHE_MAX_AGE_MS) old.delete()
        }
    }
    dir.mkdirs()
    val name = (displayName ?: uri.lastPathSegment ?: "preview.pdf")
        .substringAfterLast('/')
        .takeIf { it.isNotBlank() } ?: "preview.pdf"
    val out = File(dir, name)
    // 同名旧缓存先删：保证预览内容与当前文件一致，也不留残包
    runCatching { if (out.exists()) out.delete() }
    resolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { output -> input.copyTo(output) }
    } ?: return@runCatching null
    out
}.getOrNull()

/**
 * 一个 PDF 文档的会话：持有 renderer + 描述符，并用 Mutex 把「打开 / 渲染 / 关闭」串行化。
 * 关闭一定排在最后一次渲染之后，不会出现「渲染途中被关闭」的 native 崩溃。
 */
private class PdfSession {
    private val mutex = Mutex()
    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null

    /** 打开文档并返回页数；失败返回 0。重复调用直接复用已打开的文档（幂等，避免切换页面时重复打开） */
    suspend fun open(context: Context, savePath: String): Int = mutex.withLock {
        renderer?.let { return it.pageCount }
        val uri = previewUri(context, savePath) ?: return 0
        // ① 首选：provider 描述符直读（content:// 大文件零复制）
        val direct = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (direct != null) {
            val r = runCatching { PdfRenderer(direct) }.getOrNull()
            if (r != null) {
                renderer = r
                pfd = direct
                return r.pageCount
            }
            runCatching { direct.close() }
        }
        // ② 回退：provider 描述符不可随机访问 → 复制到 cacheDir/preview，再用本地描述符渲染
        val cached = copyToPreviewCache(context, uri) ?: return 0
        val local = runCatching {
            ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return 0
        val r = runCatching { PdfRenderer(local) }.getOrNull()
        if (r == null) {
            runCatching { local.close() }
            return 0
        }
        renderer = r
        pfd = local
        r.pageCount
    }

    /** 渲染第 index 页（0 基）；失败返回 null */
    suspend fun render(index: Int): Bitmap? = mutex.withLock {
        val r = renderer ?: return null
        if (index < 0 || index >= r.pageCount) return null
        runCatching {
            r.openPage(index).use { page ->
                val pw = page.width
                val ph = page.height
                if (pw <= 0 || ph <= 0) error("PDF 页面尺寸异常")
                // 先按最长边算缩放，再按总像素兜一层：海报/工程图这类超大页面也不会把内存撑爆
                val scale = (PDF_RENDER_MAX_SIDE.toFloat() / maxOf(pw, ph))
                    .coerceIn(PDF_RENDER_MIN_SCALE, PDF_RENDER_MAX_SCALE)
                var outW = (pw * scale).toInt().coerceAtLeast(1)
                var outH = (ph * scale).toInt().coerceAtLeast(1)
                val pixels = outW.toLong() * outH.toLong()
                if (pixels > PDF_RENDER_MAX_PIXELS) {
                    val k = kotlin.math.sqrt(PDF_RENDER_MAX_PIXELS.toDouble() / pixels.toDouble()).toFloat()
                    outW = (outW * k).toInt().coerceAtLeast(1)
                    outH = (outH * k).toInt().coerceAtLeast(1)
                }
                val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }.getOrNull()
    }

    /** 关闭（必须在 PDF 线程上、且持有 Mutex 时调用） */
    private fun closeBlocking() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        renderer = null
        pfd = null
    }

    /** 排队关闭：等当前这一页渲染完再关，绝不并发 */
    suspend fun close() = mutex.withLock { closeBlocking() }
}

@Composable
private fun PdfPreview(savePath: String?, onOpenExternal: () -> Unit) {
    if (savePath == null) {
        PreviewLoading("正在打开 PDF…")
        return
    }
    val context = LocalContext.current
    val session = remember(savePath) { PdfSession() }
    var pageCount by remember(savePath) { mutableIntStateOf(-1) } // -1=打开中，0=打开失败
    var page by remember(savePath) { mutableIntStateOf(0) }
    var bitmap by remember(savePath) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(savePath) { mutableStateOf(false) }
    var rendering by remember(savePath) { mutableStateOf(false) }

    // 打开 + 渲染都排在 PDF 单线程上串行执行（原因见 pdfDispatcher 注释）
    LaunchedEffect(session, page) {
        if (pageCount < 0) {
            val count = withContext(pdfDispatcher) { session.open(context, savePath) }
            pageCount = count
            if (count <= 0) {
                failed = true
                return@LaunchedEffect
            }
            if (page > count - 1) {
                page = 0
                return@LaunchedEffect // page 变化会让本 effect 重启
            }
        }
        rendering = true
        val bmp = withContext(pdfDispatcher) { session.render(page) }
        rendering = false
        if (bmp == null) failed = true else bitmap = bmp
    }
    // 退出预览 / 切换文件：关闭动作排到 PDF 线程最后（Mutex 保证在渲染之后）
    DisposableEffect(session) {
        onDispose { pdfCleanupScope.launch { session.close() } }
    }

    val bmp = bitmap
    when {
        failed -> PreviewError("无法打开 PDF", onOpenExternal)
        pageCount < 0 -> PreviewLoading("正在打开 PDF…")
        bmp == null -> PreviewLoading("正在渲染页面…")
        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF 第 ${page + 1} 页",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                // 翻页时保留上一页画面，只在顶部走一条细进度条，避免整屏闪白
                if (rendering) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }
            if (pageCount > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    TextButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Text("上一页") }
                    Text(
                        "${page + 1} / $pageCount",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    TextButton(
                        onClick = { if (page < pageCount - 1) page++ },
                        enabled = page < pageCount - 1
                    ) { Text("下一页") }
                }
            }
        }
    }
}

// ---------- 文本 ----------

@Composable
private fun TextPreview(savePath: String?, onOpenExternal: () -> Unit) {
    if (savePath == null) {
        PreviewLoading("正在加载文本…")
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember(savePath) { mutableStateOf(false) }
    // 分页追加状态：已加载文本 / 下一页偏移 / 是否到末尾 / 是否加载中 / 已加载字节 / 页数 / 已探测编码
    var loaded by remember(savePath) { mutableStateOf("") }
    var nextOffset by remember(savePath) { mutableStateOf(0L) }
    var eof by remember(savePath) { mutableStateOf(false) }
    var loading by remember(savePath) { mutableStateOf(false) }
    var loadedBytes by remember(savePath) { mutableStateOf(0L) }
    var pages by remember(savePath) { mutableIntStateOf(0) }
    var charset by remember(savePath) { mutableStateOf<Charset?>(null) }

    suspend fun loadNextPage() {
        if (loading || eof) return
        loading = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val uri = previewUri(context, savePath) ?: error("无法解析文件地址")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    readTextPage(input, nextOffset, charset, TEXT_PAGE_BYTES)
                } ?: error("无法打开文件流")
            }.getOrNull()
        }
        loading = false
        if (result == null) {
            failed = true
        } else {
            charset = result.charset
            loaded += result.text
            nextOffset = result.nextOffset
            loadedBytes += result.consumedBytes
            pages++
            eof = result.eof
        }
    }

    // 进页面先加载第一页；后续页由「下一页」按钮触发（追加式，阅读连续）
    LaunchedEffect(savePath) { loadNextPage() }

    if (failed && loaded.isEmpty()) {
        PreviewError("无法读取该文件", onOpenExternal)
        return
    }
    if (loaded.isEmpty() && !eof) {
        PreviewLoading("正在加载文本…")
        return
    }

    val clipboard = LocalClipboardManager.current
    var copied by remember(savePath) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部工具行：分页进度 + 复制已加载内容（不把整个大文件读进内存）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已加载 $pages 页 · ${formatByteSize(loadedBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(loaded))
                    Toast.makeText(context, "已复制已加载内容", Toast.LENGTH_SHORT).show()
                    copied = true
                }
            ) {
                Text(if (copied) "已复制" else "复制已加载", style = MaterialTheme.typography.labelMedium)
            }
        }
        // SelectionContainer：长按出现系统选择手柄，可拖选任意段落复制
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = loaded.ifBlank { "（空文件）" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            )
        }
        // 底部翻页控件：单页文件（第一页就读到末尾）不显示，保持干净界面
        if (!eof || pages > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (eof) {
                    Text(
                        text = "已到文件末尾",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { scope.launch { loadNextPage() } },
                        enabled = !loading
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("下一页")
                    }
                }
            }
        }
        // 中途读失败：保留已加载内容，提示可用外部应用查看完整文件
        if (failed) {
            Text(
                text = "后续内容读取失败，可用上方「用其他应用打开」查看完整文件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

/** 每页读取字节数：256KB（约 13 万汉字），足够滚好几屏，单页解码也不会吃满内存 */
private const val TEXT_PAGE_BYTES = 256 * 1024

/** 一页文本的读取结果 */
private data class TextPageResult(
    val text: String,
    /** 下一页起点（已按行边界对齐的绝对偏移） */
    val nextOffset: Long,
    val eof: Boolean,
    /** 本页实际消费字节数（累计「已加载 xx KB」用） */
    val consumedBytes: Long,
    /** 首页探测出的编码，后续页沿用（避免逐页编码漂移导致乱码） */
    val charset: Charset
)

/**
 * 读取一页文本：
 * - 用 skip 定位到 [offset]，不重复读取前面已加载的内容（content:// 流同样支持）；
 * - 读满一页后**回退到最后一个换行符之后**，剩余字节留给下一页 —— 不切断一行，也不会切断多字节字符；
 * - 编码在首页探测（UTF-8 严格解码失败 → GBK），由调用方在后续页传入固定 charset。
 */
private fun readTextPage(
    input: InputStream,
    offset: Long,
    charset: Charset?,
    pageBytes: Int
): TextPageResult {
    skipFully(input, offset)
    val buf = ByteArray(pageBytes)
    var read = 0
    while (read < buf.size) {
        val r = input.read(buf, read, buf.size - read)
        if (r < 0) break
        read += r
    }
    if (read <= 0) {
        // 空文件 / 已到末尾
        return TextPageResult("", offset, eof = true, consumedBytes = 0, charset = charset ?: Charsets.UTF_8)
    }
    val eof = read < buf.size
    var take = read
    if (!eof) {
        // 从后往前手动查找换行符（不依赖数组扩展函数，避免兼容性意外）
        var i = read - 1
        var lastNl = -1
        while (i >= 0) {
            if (buf[i] == '\n'.code.toByte()) { lastNl = i; break }
            i--
        }
        // 整页都没有换行（例如单行超长 JSON/log）：整页返回，保证下次仍有进展，不死循环
        if (lastNl >= 0) take = lastNl + 1
    }
    val bytes = if (take == buf.size) buf else buf.copyOf(take)
    val cs = charset ?: detectCharset(bytes)
    return TextPageResult(
        text = String(bytes, cs),
        nextOffset = offset + take,
        eof = eof,
        consumedBytes = take.toLong(),
        charset = cs
    )
}

/** 定位到指定偏移：InputStream.skip 可能返回 0，需循环；个别 provider 不支持 skip 时退化为逐个读丢弃 */
private fun skipFully(input: InputStream, offset: Long) {
    var remaining = offset
    while (remaining > 0) {
        val skipped = runCatching { input.skip(remaining) }.getOrDefault(0L)
        if (skipped > 0) {
            remaining -= skipped
        } else if (input.read() < 0) {
            return
        } else {
            remaining -= 1
        }
    }
}

/** 编码探测：UTF-8 严格解码（遇非法序列立即报错）→ 失败按中文老编码 GBK 处理 */
private fun detectCharset(bytes: ByteArray): Charset = runCatching {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
    Charsets.UTF_8
}.getOrElse { runCatching { charset("GBK") }.getOrDefault(Charsets.UTF_8) }

/** 字节数格式化（「已加载 xx KB」用） */
private fun formatByteSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    return if (kb < 1024) String.format(java.util.Locale.US, "%.1f KB", kb)
    else String.format(java.util.Locale.US, "%.2f MB", kb / 1024.0)
}

// ---------- 不支持的类型 ----------

@Composable
private fun UnsupportedPreview(onOpenExternal: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(40.dp)
        )
        Text("该文件类型暂不支持应用内预览", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onOpenExternal) { Text("用其他应用打开") }
    }
}
