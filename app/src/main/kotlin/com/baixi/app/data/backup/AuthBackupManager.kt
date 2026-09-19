package com.baixi.app.data.backup

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.baixi.app.data.db.BaiduAccountDao
import com.baixi.app.data.db.BaiduAccountEntity
import com.baixi.app.data.db.C139AccountDao
import com.baixi.app.data.db.C139AccountEntity
import com.baixi.app.data.db.Pan123AccountDao
import com.baixi.app.data.db.Pan123AccountEntity
import com.baixi.app.data.db.QuarkAccountDao
import com.baixi.app.data.db.QuarkAccountEntity
import com.baixi.app.data.db.UCAccountDao
import com.baixi.app.data.db.UCAccountEntity
import com.baixi.app.data.db.XunleiAccountDao
import com.baixi.app.data.db.XunleiAccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 网盘认证信息备份：把已登录的平台（夸克/UC/迅雷/百度/139）凭证打包为 JSON，
 * 可导出到下载目录并在另一台设备导入恢复。
 */
class AuthBackupManager(
    private val quarkDao: QuarkAccountDao,
    private val ucDao: UCAccountDao,
    private val xunleiDao: XunleiAccountDao,
    private val baiduDao: BaiduAccountDao,
    private val c139Dao: C139AccountDao,
    private val pan123Dao: Pan123AccountDao
) {

    private companion object {
        const val APP_TAG = "baixi_auth_backup"
        const val VERSION = 1

        /** 日志 TAG（与 JSON 里的 app 字段区分） */
        const val LOG_TAG = "Baixi-AuthBackup"
    }

    /**
     * 导出网盘认证（强制 AES-GCM 加密）：
     * @param password 至少 8 位的备份口令
     * @param onlyLoggedIn true=仅导出凭证可用的已登录平台；false=导出数据库里全部绑定记录
     * @return 明文 JSON 或 Base64 密文
     */
    suspend fun export(password: String? = null, onlyLoggedIn: Boolean = true): String =
        withContext(Dispatchers.IO) {
            require(!password.isNullOrBlank() && password.length >= 8) { "备份口令至少 8 位" }
            val json = exportJson(onlyLoggedIn)
            AuthCrypto.encrypt(json, password)
        }

    /** 导出所有已登录平台为 JSON 字符串；无已登录平台时返回空 accounts */
    suspend fun exportJson(onlyLoggedIn: Boolean = true): String = withContext(Dispatchers.IO) {
        val accounts = JSONArray()
        quarkDao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.cookie.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "quark")
                    .put("cookie", a.cookie)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        ucDao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.cookie.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "uc")
                    .put("cookie", a.cookie)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        xunleiDao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.accessToken.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "xunlei")
                    .put("accessToken", a.accessToken)
                    .put("refreshToken", a.refreshToken)
                    .put("deviceId", a.deviceId)
                    .put("captchaToken", a.captchaToken)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        baiduDao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.cookie.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "baidu")
                    .put("cookie", a.cookie)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        c139Dao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.cookie.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "c139")
                    .put("cookie", a.cookie)
                    .put("authorization", a.authorization)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        pan123Dao.getAccount()?.let { a ->
            if (!onlyLoggedIn || a.accessToken.isNotBlank()) accounts.put(
                JSONObject()
                    .put("platform", "pan123")
                    .put("accessToken", a.accessToken)
                    .put("account", a.account)
                    .put("nickname", a.nickname)
                    .put("updatedAt", a.updatedAt)
            )
        }
        JSONObject()
            .put("app", APP_TAG)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("accounts", accounts)
            .toString(2)
    }

    /**
     * 导入认证内容（可选 AES 解密）：
     * @param password 非空时先解密（密码错误抛异常）；null/空按明文 JSON 解析
     * @return 成功恢复的平台数；文件不合法抛异常
     */
    suspend fun import(content: String, password: String? = null): Int = withContext(Dispatchers.IO) {
        val json = if (password.isNullOrBlank()) content else AuthCrypto.decrypt(content, password)
        importJson(json)
    }

    /** 导入 JSON，恢复各平台凭证；返回成功恢复的平台数；文件不合法抛异常 */
    suspend fun importJson(json: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        if (root.optString("app") != APP_TAG) {
            throw IllegalArgumentException("不是有效的辞白认证备份文件")
        }
        val accounts = root.optJSONArray("accounts") ?: return@withContext 0
        var count = 0
        for (i in 0 until accounts.length()) {
            val obj = accounts.optJSONObject(i) ?: continue
            when (obj.optString("platform")) {
                "quark" -> {
                    val c = obj.optString("cookie")
                    if (c.isNotBlank()) {
                        quarkDao.upsert(
                            QuarkAccountEntity(
                                id = "quark", cookie = c,
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
                "uc" -> {
                    val c = obj.optString("cookie")
                    if (c.isNotBlank()) {
                        ucDao.upsert(
                            UCAccountEntity(
                                id = "uc", cookie = c,
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
                "xunlei" -> {
                    val t = obj.optString("accessToken")
                    if (t.isNotBlank()) {
                        xunleiDao.upsert(
                            XunleiAccountEntity(
                                id = "xunlei", accessToken = t,
                                refreshToken = obj.optString("refreshToken"),
                                deviceId = obj.optString("deviceId"),
                                captchaToken = obj.optString("captchaToken"),
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
                "baidu" -> {
                    val c = obj.optString("cookie")
                    if (c.isNotBlank()) {
                        baiduDao.upsert(
                            BaiduAccountEntity(
                                id = "baidu", cookie = c,
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
                "c139" -> {
                    val c = obj.optString("cookie")
                    if (c.isNotBlank()) {
                        c139Dao.upsert(
                            C139AccountEntity(
                                id = "c139", cookie = c,
                                authorization = obj.optString("authorization"),
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
                "pan123" -> {
                    val t = obj.optString("accessToken")
                    if (t.isNotBlank()) {
                        pan123Dao.upsert(
                            Pan123AccountEntity(
                                id = "pan123", accessToken = t,
                                account = obj.optString("account"),
                                nickname = obj.optString("nickname"),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        ); count++
                    }
                }
            }
        }
        count
    }

    /**
     * 把备份内容保存到公共下载目录（Android 10+ 走 MediaStore 无需权限）。
     * - Android 10+：insert 时先标记 IS_PENDING=1，写完再置 0；任何失败路径删除半成品，
     *   避免在下载目录留下截断的 .baixi/.json 文件。
     * - Android 9-：写公共 Download 需要 WRITE_EXTERNAL_STORAGE；无权限时不申请权限，
     *   退化为应用私有外部目录 getExternalFilesDir(DIRECTORY_DOWNLOADS)（无需权限）。
     * @param encrypted true 时文件名为 .baixi（加密备份），否则 .json（明文）
     * @return 是否保存成功（函数签名不变）
     */
    suspend fun saveToDownloads(context: Context, content: String, encrypted: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = if (encrypted) {
                    "baixi_auth_backup_${timestamp()}.baixi"
                } else {
                    "baixi_auth_backup_${timestamp()}.json"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(
                            MediaStore.Downloads.MIME_TYPE,
                            if (encrypted) "application/octet-stream" else "application/json"
                        )
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        // 写入完成前对其他应用不可见，避免读到半成品
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    var uri: Uri? = null
                    var finished = false
                    try {
                        uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: return@runCatching false
                        val out = resolver.openOutputStream(uri)
                            ?: throw IllegalStateException("无法打开备份输出流")
                        out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                        // 写入成功：清除 pending 标记，文件才对其他应用可见
                        resolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                            null,
                            null
                        )
                        finished = true
                        true
                    } finally {
                        // 任何异常/失败/提前返回：删除半成品，不留截断文件
                        if (!finished) uri?.let { runCatching { resolver.delete(it, null, null) } }
                    }
                } else {
                    // Android 9-：公共 Download 需要运行时权限；未授权时退化到应用私有外部目录
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                    val dir = if (granted) {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    } else {
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            ?: throw IllegalStateException("外部存储不可用，无法导出备份")
                    }
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    if (!granted) {
                        Log.w(
                            LOG_TAG,
                            "无 WRITE_EXTERNAL_STORAGE 权限，备份已保存到应用私有下载目录：${file.absolutePath}"
                        )
                    }
                    true
                }
            }.getOrDefault(false)
        }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
