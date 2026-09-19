package com.baixi.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.baixi.app.MainActivity
import com.baixi.app.R

/**
 * 下载前台服务：下载进行中保持前台运行。
 * 前台服务让系统将应用视为「前台」，避免 Doze/后台省电限速、防止进程被杀，
 * 从而保证切后台后下载速度不受影响。
 * 生命周期由 DownloadManager 驱动：任务开始 → start()，全部结束 → stop()。
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FGS 铁律（Android 12+ 强制校验）：被 startForegroundService 拉起后必须先 startForeground，
        // 无论 intent 是进度更新、停止还是系统重建的 null——否则超时即崩
        // （ForegroundServiceDidNotStartInTimeException）。所以这里无条件先进入前台。
        ensureChannel(this)
        goForeground(
            buildNotification(
                // 通知按钮的 Intent 也带全量状态；缺省值取上次缓存（服务被系统重建时不闪回默认文案）
                intent?.getStringExtra(EXTRA_TITLE) ?: lastTitle,
                intent?.getIntExtra(EXTRA_PROGRESS, lastProgress) ?: lastProgress,
                intent?.getStringExtra(EXTRA_SPEED) ?: lastSpeed,
                intent?.getBooleanExtra(EXTRA_SHOW_SPEED, lastShowSpeed) ?: lastShowSpeed,
                intent?.getBooleanExtra(EXTRA_CAN_PAUSE, lastCanPause) ?: lastCanPause,
                intent?.getBooleanExtra(EXTRA_CAN_RESUME, lastCanResume) ?: lastCanResume
            )
        )
        if (intent == null) {
            // 系统重建服务（START_NOT_STICKY 下不应发生，防御处理）：已合规进入前台，安全退场
            stopSelf()
        } else when (intent.action) {
            // 停止：已合法进入前台，stopSelf 不再受「FGS 超时」约束
            ACTION_STOP -> stopSelf()
            // 通知栏「暂停全部 / 继续全部」：服务不持有任务状态，转发给 DownloadManager 处理
            ACTION_PAUSE_ALL -> commandHandler?.invoke(ACTION_PAUSE_ALL)
            ACTION_RESUME_ALL -> commandHandler?.invoke(ACTION_RESUME_ALL)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    /** 进入前台（幂等）：所有 startForegroundService 路径统一走这里，确保不触发 FGS 超时 */
    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        title: String,
        progress: Int,
        speed: String,
        showSpeed: Boolean,
        canPauseAll: Boolean,
        canResumeAll: Boolean
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(R.mipmap.icon)
            .setContentTitle(title)
            // 完整通知显示下载速度；简化模式仅提示下载中（且不显示进度条）
            .setContentText(
                if (showSpeed && speed.isNotBlank()) "下载速度 $speed"
                else "正在后台下载，完成前请勿关闭应用"
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        // 仅「完整通知」模式显示进度条；简化模式隐藏进度条
        if (showSpeed && progress in 0..100) {
            builder.setProgress(100, progress, false)
        }
        // 控制按钮：正在下载 → 「暂停全部」；无活动任务 → 「继续全部」（二选一，避免出现点不动的按钮）
        if (canPauseAll) {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    "暂停全部",
                    actionIntent(
                        ACTION_PAUSE_ALL, title, progress, speed, showSpeed,
                        canPauseAll, canResumeAll, REQUEST_PAUSE_ALL
                    )
                ).build()
            )
        } else if (canResumeAll) {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    "继续全部",
                    actionIntent(
                        ACTION_RESUME_ALL, title, progress, speed, showSpeed,
                        canPauseAll, canResumeAll, REQUEST_RESUME_ALL
                    )
                ).build()
            )
        }
        return builder.build()
    }

    /**
     * 通知按钮 → 回本服务的 PendingIntent。
     * 携带当前通知状态：服务被动作再次拉起时重建通知不会闪回默认文案。
     */
    private fun actionIntent(
        action: String,
        title: String,
        progress: Int,
        speed: String,
        showSpeed: Boolean,
        canPauseAll: Boolean,
        canResumeAll: Boolean,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(this, DownloadService::class.java)
            .setAction(action)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_PROGRESS, progress)
            .putExtra(EXTRA_SPEED, speed)
            .putExtra(EXTRA_SHOW_SPEED, showSpeed)
            .putExtra(EXTRA_CAN_PAUSE, canPauseAll)
            .putExtra(EXTRA_CAN_RESUME, canResumeAll)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val CHANNEL_ID = "baixi_download"
        private const val NOTIFICATION_ID = 1001
        private const val COMPLETION_NOTIFICATION_ID = 1002
        private const val ACTION_STOP = "com.baixi.app.action.STOP_DOWNLOAD"
        /** 通知栏「暂停全部 / 继续全部」动作（服务转发给 DownloadManager 执行） */
        const val ACTION_PAUSE_ALL = "com.baixi.app.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.baixi.app.action.RESUME_ALL"
        /** 「等待WiFi」普通提示通知 id（独立于前台通知 1001 / 完成通知 1002，避免互相覆盖） */
        private const val WAITING_WIFI_NOTIFICATION_ID = 1003
        /** 停止延迟：吸收「startForegroundService 刚发出、服务尚未创建就被 stopService」的竞态窗口 */
        private const val STOP_DELAY_MS = 800L
        /** 主线程派发器：仅用于延迟 stopService（Handler 跨线程收发是线程安全的） */
        private val mainHandler = Handler(Looper.getMainLooper())
        /** 每次 start 递增；延迟停止执行时比对代数，代数变了说明期间有新任务启动，放弃停止 */
        @Volatile
        private var startGeneration = 0
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_SPEED = "speed"
        private const val EXTRA_SHOW_SPEED = "show_speed"
        private const val EXTRA_CAN_PAUSE = "can_pause"
        private const val EXTRA_CAN_RESUME = "can_resume"
        private const val REQUEST_PAUSE_ALL = 11
        private const val REQUEST_RESUME_ALL = 12

        /**
         * 通知栏操作处理器：由 DownloadManager 初始化时注册。
         * 前台服务本身不持有任务状态，暂停/继续必须回到管理器执行。
         */
        @Volatile
        var commandHandler: ((String) -> Unit)? = null

        // 上次通知状态：服务被系统重建 / 动作 Intent 缺 extra 时复用，避免闪回默认文案
        @Volatile
        private var lastTitle = "下载中…"
        @Volatile
        private var lastProgress = -1
        @Volatile
        private var lastSpeed = ""
        @Volatile
        private var lastShowSpeed = true
        @Volatile
        private var lastCanPause = false
        @Volatile
        private var lastCanResume = false

        /** 下载任务开始时调用（服务不存在则创建前台服务） */
        fun start(context: Context, title: String, progress: Int = 0) {
            push(context, title, progress, "", true, false, false)
        }

        /**
         * 更新/启动前台通知（标题/进度/速度变化；调用方节流）。
         * @param canPauseAll 是否有活动任务（true 时通知显示「暂停全部」）
         * @param canResumeAll 是否有可恢复任务（true 时通知显示「继续全部」）
         */
        fun update(
            context: Context,
            title: String,
            progress: Int,
            speed: String,
            showSpeed: Boolean,
            canPauseAll: Boolean = false,
            canResumeAll: Boolean = false
        ) {
            push(context, title, progress, speed, showSpeed, canPauseAll, canResumeAll)
        }

        private fun push(
            context: Context,
            title: String,
            progress: Int,
            speed: String,
            showSpeed: Boolean,
            canPauseAll: Boolean,
            canResumeAll: Boolean
        ) {
            // 新任务/进度更新到达：撤销排期中的停止，并推进代数（使更早排期的停止失效）
            mainHandler.removeCallbacksAndMessages(null)
            startGeneration++
            lastTitle = title
            lastProgress = progress
            lastSpeed = speed
            lastShowSpeed = showSpeed
            lastCanPause = canPauseAll
            lastCanResume = canResumeAll
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_SPEED, speed)
                .putExtra(EXTRA_SHOW_SPEED, showSpeed)
                .putExtra(EXTRA_CAN_PAUSE, canPauseAll)
                .putExtra(EXTRA_CAN_RESUME, canResumeAll)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 下载完成提示（非前台通知）：点击「打开/安装」直达文件。
         */
        fun notifyCompleted(context: Context, fileName: String, savedPath: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val contentIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val isApk = fileName.endsWith(".apk", true)
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            builder.setSmallIcon(R.mipmap.icon)
                .setContentTitle("下载完成")
                .setContentText(fileName)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
            // 「打开 / 安装」动作：Android 10+ 应用在后台时无法直接拉起界面，这个按钮就是兜底入口
            val viewPi = viewPendingIntent(context, savedPath, fileName)
            if (viewPi != null) {
                builder.addAction(
                    Notification.Action.Builder(
                        null, if (isApk) "安装" else "打开", viewPi
                    ).build()
                )
            }
            runCatching { nm.notify(COMPLETION_NOTIFICATION_ID, builder.build()) }
        }

        /** 已下载文件 → ACTION_VIEW 的 PendingIntent（MIME 按扩展名推断，APK 为安装类型） */
        private fun viewPendingIntent(context: Context, savedPath: String, fileName: String): PendingIntent? {
            val uri = fileUriOf(context, savedPath) ?: return null
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeOf(fileName))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        /** 保存路径 → content:// URI（SAF/MediaStore 已是 content://；绝对路径经 FileProvider 转换） */
        private fun fileUriOf(context: Context, savedPath: String): android.net.Uri? = runCatching {
            if (savedPath.startsWith("content://")) android.net.Uri.parse(savedPath)
            else androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", java.io.File(savedPath)
            )
        }.getOrNull()

        /** 扩展名 → MIME（推断不出时回退通配类型，避免 setDataAndType 传 null 触发 ActivityNotFound） */
        private fun mimeOf(fileName: String): String {
            if (fileName.endsWith(".apk", true)) return "application/vnd.android.package-archive"
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }

        /**
         * 自动打开已下载文件（仅应用在前台时调用：Android 10+ 后台启动界面会被系统拦截）。
         * @return 是否成功拉起界面
         */
        fun startOpen(context: Context, savedPath: String, fileName: String): Boolean {
            val uri = fileUriOf(context, savedPath) ?: return false
            return runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, mimeOf(fileName))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
        }

        /** 自动拉起 APK 安装界面（仅前台调用；仍需用户在系统弹窗确认安装） */
        fun startInstall(context: Context, savedPath: String): Boolean {
            val uri = fileUriOf(context, savedPath) ?: return false
            return runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
        }

        /** 取消「下载完成」通知（自动打开/自动安装已拉起界面时调用，避免重复打扰） */
        fun cancelCompletedNotification(context: Context) {
            runCatching {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                    ?.cancel(COMPLETION_NOTIFICATION_ID)
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "下载任务", NotificationManager.IMPORTANCE_LOW)
                    )
                }
            }
        }

        /** 「等待WiFi」提示（普通静默通知，非前台服务）：任务因流量网络暂停，连接 WiFi 后自动继续 */
        fun notifyWaitingWifi(context: Context, fileName: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            ensureChannel(context)
            val contentIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            builder.setSmallIcon(R.mipmap.icon)
                .setContentTitle("等待WiFi")
                .setContentText("「$fileName」已暂停，连接 WiFi 后自动继续")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
            runCatching { nm.notify(WAITING_WIFI_NOTIFICATION_ID, builder.build()) }
        }

        /** 清除「等待WiFi」提示（WiFi 恢复继续 / 任务被暂停或删除时调用） */
        fun cancelWaitingWifi(context: Context) {
            runCatching {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                    ?.cancel(WAITING_WIFI_NOTIFICATION_ID)
            }
        }

        /**
         * 全部任务结束：延迟停止前台服务。
         * 立即 stopService 存在系统级竞态：若此刻恰有一次 startForegroundService 已发出但服务
         * 尚未完成创建（OEM ROM 投递延迟可达数秒），服务被拉起时已「死亡」、永远没机会调
         * startForeground → 系统抛 ForegroundServiceDidNotStartInTimeException 直接崩溃。
         * 延迟 + 代数校验：期间任何新任务 start 都会撤销/作废这次停止。
         */
        fun stop(context: Context) {
            val gen = startGeneration
            mainHandler.postDelayed({
                if (startGeneration == gen) {
                    context.stopService(Intent(context, DownloadService::class.java))
                }
            }, STOP_DELAY_MS)
        }
    }
}