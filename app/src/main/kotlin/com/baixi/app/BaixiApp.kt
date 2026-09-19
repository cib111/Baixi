package com.baixi.app

import android.app.Application
import com.baixi.app.crash.CrashHandler
import com.baixi.app.data.db.AppDatabase
import com.baixi.app.data.download.ChunkDownloader
import com.baixi.app.data.download.DownloadManager
import com.baixi.app.data.network.HttpClients
import com.baixi.app.data.prefs.SettingsRepository

class BaixiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appInstance = this
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        // 迅雷动态设备指纹：首次启动生成并持久化（开源分发后每台设备独立指纹）
        com.baixi.app.data.network.XunleiDeviceFingerprint.init(this)
        // 前后台可见性跟踪：下载完成后「自动打开/自动安装」需据此判断能否启动界面
        com.baixi.app.util.AppVisibility.install(this)
    }

    companion object {
        @Volatile
        private var appInstance: BaixiApp? = null

        /** 应用级单例（DownloadManager 等长生命周期对象依赖它，不能持有 Activity） */
        fun instance(): BaixiApp = appInstance
            ?: throw IllegalStateException("BaixiApp 尚未初始化（Application.onCreate 未执行）")

        /** 应用级设置（SharedPreferences 单进程共享） */
        val settings: SettingsRepository by lazy { SettingsRepository(instance()) }

        /**
         * 应用级下载管理器。
         *
         * ★ 必须是进程单例：旧实现由 MainScreen 的 remember 持有，而 remember 只活过一次组合——
         *   Activity 重建（旋转 / 深色切换 / 多窗口尺寸变化）会创建第二个实例，旧实例的协程仍在下载，
         *   但新 UI 的暂停/删除只作用于新实例的 activeJobs，结果：
         *   ① 界面显示「已暂停」而旧实例仍在写盘耗流量；② 两套前台服务计数互相 stop；
         *   ③ 旧实例通过 context 持有已销毁的 Activity（泄漏）。
         *   改用 applicationContext + 进程单例后，任务状态与前台服务生命周期始终唯一。
         *
         * 注意：storagePermissionProvider / meteredPrompt 仍由 UI 层注入（需要 Activity 申请权限/弹窗），
         * 每次 MainScreen 组合都会刷新这两个回调，指向当前存活的 Activity。
         */
        val downloadManager: DownloadManager by lazy {
            val app = instance()
            DownloadManager(
                context = app,
                dao = AppDatabase.get(app).downloadTaskDao(),
                downloader = ChunkDownloader({ HttpClients.downloadClient() }),
                threadProvider = { settings.downloadThreads },
                // 分平台下载线程数（0=跟随全局）
                quarkThreadProvider = { settings.quarkDownloadThreads },
                ucThreadProvider = { settings.ucDownloadThreads },
                baiduThreadProvider = { settings.baiduDownloadThreads },
                c139ThreadProvider = { settings.c139DownloadThreads },
                pan123ThreadProvider = { settings.pan123DownloadThreads },
                // 自定义下载保存目录（SAF tree Uri）/ 并发任务数 / 全局限速 / 失败重试
                saveDirProvider = { settings.downloadDirUri },
                concurrencyProvider = { settings.maxConcurrentDownloads },
                speedLimitProvider = { settings.downloadSpeedLimit },
                retryCountProvider = { settings.downloadRetryCount },
                // 锁屏保持下载 / 通知栏速度 / 流量网络策略
                keepWhenLockedProvider = { settings.keepDownloadWhenLocked },
                showSpeedProvider = { settings.notificationShowSpeed },
                meteredChoiceProvider = { settings.meteredDownloadChoice },
                // 下载完成后的自动化动作（设置页开关，默认全关）
                autoOpenProvider = { settings.autoOpenAfterDownload },
                autoInstallApkProvider = { settings.autoInstallApk },
                autoDeleteTaskProvider = { settings.autoDeleteTaskAfterDownload },
                // 下载统计：完成一个任务累加流量与完成数
                statsRecorder = { bytes -> settings.recordCompletedDownload(bytes) }
            )
        }
    }
}
