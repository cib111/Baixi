package com.baixi.app.util

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 应用前后台可见性跟踪。
 *
 * 用途：Android 10+ 禁止后台应用启动 Activity（后台 startActivity 会被系统静默拦截），
 * 「下载完成后自动打开/自动安装」必须先判断应用是否可见，不可见时只能依赖完成通知的按钮。
 * 通过 ActivityLifecycleCallbacks 维护 started 计数：不会误判旋转/多 Activity 切换。
 */
object AppVisibility : Application.ActivityLifecycleCallbacks {

    /** 是否至少有一个 Activity 处于 started（可见）状态 */
    @Volatile
    var isForeground: Boolean = false
        private set

    private var startedCount = 0

    /** 在 Application.onCreate 中调用一次 */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedCount++
        isForeground = true
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        isForeground = startedCount > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
