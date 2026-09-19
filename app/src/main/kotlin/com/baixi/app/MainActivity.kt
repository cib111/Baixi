package com.baixi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.baixi.app.ui.MainScreen
import com.baixi.app.ui.ShareIntentHub
import com.baixi.app.ui.theme.ComposeEmptyActivityTheme
import com.baixi.app.ui.theme.ThemeController

class MainActivity : ComponentActivity() {

    // Android 13+：下载前台服务通知需要动态授权，首次启动即引导（无论通知栏开关状态，授权后通知才可见）
    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动期主题决策：应用内「强制深色/浅色」时精确指定主题变体，
        // 使系统启动动画背景与应用内配色一致（跟随系统时由 values-night 自动处理）。
        // setTheme 必须在 super.onCreate 之前调用。
        ThemeController.init(this)
        when (ThemeController.darkMode) {
            2 -> setTheme(R.style.Theme_Baixi_Dark)
            1 -> setTheme(R.style.Theme_Baixi_Light)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            ComposeEmptyActivityTheme {
                MainScreen()
            }
        }
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** 系统分享入口：其他应用分享的文本（如链接）推入 ShareIntentHub，MainScreen 收集后转入解析页 */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            ShareIntentHub.push(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    /** Android 13+ 申请通知权限；低版本（<33）系统自动授予，无需申请 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}