package com.baixi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * 首次启动「用户协议 + 隐私政策」弹窗：
 * 阅读并同意后才能进入应用；不同意则退出。
 * - 禁止返回键关闭（dismissOnBackPress = false）
 * - 禁止点击外部关闭（dismissOnClickOutside = false）
 * - 同意 / 不同意 二选一，没有第三条路
 */
@Composable
fun AgreementDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = { /* 不允许任何方式关闭，只能二选一 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---------- 标题 ----------
                Text(
                    text = "用户协议与隐私政策",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "白析 v${appVersion(context)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ---------- 协议正文（可滚动） ----------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AgreementSection(
                        title = "一、免责协议",
                        body = "本软件仅供个人学习与技术交流使用，请勿用于商业用途；下载内容版权归原作者所有，" +
                            "请于下载后 24 小时内删除；解析与下载依赖网盘平台接口，接口可能随时失效，" +
                            "使用本应用产生的任何后果由使用者自行承担。"
                    )
                    AgreementSection(
                        title = "二、隐私条款",
                        body = "1. 网盘登录凭证（Cookie / Token）仅保存在本机，不会上传至任何服务器；\n" +
                            "2. 本应用不收集、不存储任何个人身份信息，不包含任何广告或统计 SDK；\n" +
                            "3. 下载任务与账号信息仅存于本机数据库，卸载应用即全部清除。"
                    )
                    AgreementSection(
                        title = "三、开源信息",
                        body = "本软件根据 CYQawa/YunX 开源项目二次开发（原项目著作权归其作者所有），" +
                            "遵循 GNU AGPL-3.0 协议；白析自身的源码与许可证可在应用内「关于」页面查看。"
                    )
                    // 开源地址：可点击跳转原项目仓库
                    TextButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/cib111/Baixi")
                            )
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "开源地址：github.com/cib111/Baixi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ---------- 操作按钮 ----------
                Button(
                    onClick = onAgree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("同意并继续", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDisagree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("不同意并退出", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "点击「同意并继续」即表示您已阅读并同意以上全部条款",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 协议小节：标题 + 正文 */
@Composable
private fun AgreementSection(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

/** 读取应用版本号 */
@Composable
private fun appVersion(context: android.content.Context): String =
    try {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        pkg.versionName ?: ""
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }