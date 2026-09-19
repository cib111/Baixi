package com.baixi.app.crash

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 全局崩溃捕获：
 * 1. 生成崩溃报告（时间 / 线程 / 设备 / 堆栈）；
 * 2. 落盘到 filesDir/crash/；
 * 3. 启动独立进程(:crash)的崩溃界面，随后终止当前进程。
 *
 * 防递归（崩溃页自身崩溃会无限重启）：
 * - 崩溃界面所在进程（进程名以 ":crash" 结尾）在构造时不再安装本处理器，
 *   而是恢复系统原有处理器；
 * - 拉起崩溃页用静态 AtomicBoolean 保护，同一进程内只会拉起一次；
 * - 崩溃文本超过约 100KB 时不再塞进 Intent extra（避免 Binder 事务过大），
 *   改为只传落盘文件路径 extra（EXTRA_CRASH_LOG_PATH）。
 */
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    /** 当前是否运行在崩溃界面进程（AndroidManifest: android:process=":crash"） */
    private val crashProcess = currentProcessName()?.endsWith(CRASH_PROCESS_SUFFIX) == true

    init {
        // 崩溃界面进程内尽力"不安装"本处理器：恢复系统原有处理器。
        // 注意：BaixiApp 的写法是 Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))，
        // 参数先构造、随后仍会覆盖安装本实例，因此这里只能算尽力而为；
        // 真正的防递归由 uncaughtException 里的 crashProcess 短路 + AtomicBoolean 保证。
        if (crashProcess) {
            defaultHandler?.let { Thread.setDefaultUncaughtExceptionHandler(it) }
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // 崩溃页进程内兜底：交给系统默认处理器并终止，绝不再拉起崩溃页
        if (crashProcess) {
            defaultHandler?.let { it.uncaughtException(thread, throwable) }
            Process.killProcess(Process.myPid())
            System.exit(1)
            return
        }

        val log = buildCrashLog(thread, throwable)
        val file = saveCrashLog(log)
        val logBytes = log.toByteArray(Charsets.UTF_8)

        // 只拉起一次崩溃页：避免崩溃页/后续崩溃反复拉起形成无限重启
        if (crashPageLaunched.compareAndSet(false, true)) {
            // 崩溃可能发生在主线程（主线程已终止），因此崩溃界面必须跑在独立进程
            val intent = Intent(context, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                // 额外提供落盘路径（文本过长时崩溃页可据此读全文）
                if (file != null) putExtra(EXTRA_CRASH_LOG_PATH, file.absolutePath)
                if (logBytes.size <= MAX_INTENT_LOG_LENGTH) {
                    // 正常长度：保持原 extra key / 原行为
                    putExtra(EXTRA_CRASH_LOG, log)
                } else if (file == null) {
                    // 文本过长且落盘失败：只能截断传输，避免 TransactionTooLargeException
                    putExtra(
                        EXTRA_CRASH_LOG,
                        log.take(MAX_INTENT_LOG_LENGTH / 4) + "\n...(崩溃日志过长，已截断)"
                    )
                }
                // 过长且已落盘：只传 EXTRA_CRASH_LOG_PATH，不塞大文本
            }
            runCatching { context.startActivity(intent) }
        }

        Process.killProcess(Process.myPid())
        System.exit(1)
    }

    /** 当前进程名：API 28+ 直接取 Application.getProcessName()，更低版本查运行中进程列表 */
    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return runCatching { Application.getProcessName() }.getOrNull()
        }
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val pid = Process.myPid()
            am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }.getOrNull()
    }

    private fun buildCrashLog(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"

        return buildString {
            appendLine("白析 Crash Report")
            appendLine("时间：$time")
            appendLine("线程：${thread.name}")
            appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}（Android ${Build.VERSION.RELEASE}，SDK ${Build.VERSION.SDK_INT}）")
            appendLine("版本：$versionName")
            appendLine()
            appendLine(sw.toString())
        }
    }

    /** 落盘崩溃日志，返回文件；失败返回 null */
    private fun saveCrashLog(log: String): File? = runCatching {
        val dir = File(context.filesDir, "crash").apply { mkdirs() }
        val file = File(dir, "crash_${System.currentTimeMillis()}.txt")
        file.writeText(log)
        file
    }.getOrNull()

    companion object {
        const val EXTRA_CRASH_LOG = "extra_crash_log"

        /** 崩溃日志落盘文件的绝对路径（文本过长时崩溃页据此读取完整内容） */
        const val EXTRA_CRASH_LOG_PATH = "extra_crash_log_path"

        /** 崩溃界面独立进程名后缀 */
        private const val CRASH_PROCESS_SUFFIX = ":crash"

        /** Intent extra 允许携带的崩溃文本上限（约 100KB） */
        private const val MAX_INTENT_LOG_LENGTH = 100 * 1024

        /** 同一进程内只拉起一次崩溃页 */
        private val crashPageLaunched = AtomicBoolean(false)
    }
}
