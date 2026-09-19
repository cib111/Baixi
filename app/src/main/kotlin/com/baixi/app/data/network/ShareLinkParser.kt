package com.baixi.app.data.network

/** 网盘平台 */
enum class SharePlatform { QUARK, UC, XUNLEI, BAIDU, C139, PAN123 }

/**
 * 解析结果：share_id + 提取码 + 平台。
 */
data class ParsedShare(
    val shareId: String,
    val pwd: String?,
    val platform: SharePlatform
)

/**
 * 从分享链接或整段分享文案中提取 share_id 与提取码。
 * 支持：pan.quark.cn/s/xxx（夸克）、drive.uc.cn/s/xxx（UC）、pan.xunlei.com/s/xxx（迅雷）
 */
object ShareLinkParser {

    private val urlRegex = Regex("""https?://[^\s]+""")
    private val quarkShareIdRegex = Regex("""pan\.quark\.cn/s/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val ucShareIdRegex = Regex("""drive\.uc\.cn/s/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val xunleiShareIdRegex = Regex("""pan\.xunlei\.com/s/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val baiduShareIdRegex = Regex("""pan\.baidu\.com/s/(1[A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    // 百度另一下发形态：https://pan.baidu.com/share/init?surl=xxxx（surl 参数值本身不含 /s/ 形态的前导 "1"）
    private val baiduInitShareIdRegex = Regex("""pan\.baidu\.com/share/init\?[^\s]*?surl=([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val c139ShareIdRegex = Regex("""yun\.139\.com/shareweb/.*?/w/i/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    // 139 移动端短链形态：https://caiyun.139.com/m/i?<linkID>（linkID 直接跟在 ? 后，没有 key=）
    private val c139CaiyunShareIdRegex = Regex("""caiyun\.139\.com/m/i\?([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    // 123 云盘分享链接（抓包 + alist 实践综合，文档 §4.1）：
    // - https://www.123pan.com/s/<ShareKey> / https://www.123865.com/s/<ShareKey>
    // - https://<UID>.share.123pan.cn/123pan/<ShareKey>
    // - https://www.123pan.cn/api/srr?sk=<ShareKey>&st=s
    // ShareKey 形态：字母数字，可能含中划线（如 2785Vv-T4Ded），也可能不含（放开旧的中划线强制要求）
    private val pan123ShareIdRegex = Regex("""123(?:865|pan)\.(?:com|cn)/s/([A-Za-z0-9][A-Za-z0-9-]*)""", RegexOption.IGNORE_CASE)
    private val pan123ShareSubRegex = Regex("""share\.123pan\.cn/123pan/([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val pan123SrrRegex = Regex("""api/srr\?sk=([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val pwdInUrlRegex = Regex("""[?&]pwd=([A-Za-z0-9]+)""")
    // 提取码文案：兼容「提取码：1234 / 提取码:1234 / 提取码 1234 / 密码 1234 / 访问码，1234」等空白/中文分隔
    private val pwdInTextRegex = Regex("(?:提取码|访问码|密码)[：:，,、\\s\\u3000]*([A-Za-z0-9]{4,8})")

    /** 是否为任一支持平台的网盘分享链接（多 URL 文本中筛选真正的分享链接用） */
    private fun isCloudShareUrl(url: String): Boolean =
        quarkShareIdRegex.containsMatchIn(url) ||
            ucShareIdRegex.containsMatchIn(url) ||
            xunleiShareIdRegex.containsMatchIn(url) ||
            baiduShareIdRegex.containsMatchIn(url) ||
            baiduInitShareIdRegex.containsMatchIn(url) ||
            c139ShareIdRegex.containsMatchIn(url) ||
            c139CaiyunShareIdRegex.containsMatchIn(url) ||
            pan123ShareIdRegex.containsMatchIn(url) ||
            pan123ShareSubRegex.containsMatchIn(url) ||
            pan123SrrRegex.containsMatchIn(url)

    fun parse(text: String): ParsedShare? {
        // 分享文案常夹带广告/说明等无关链接：只取真正的网盘分享链接，取不到直接判定解析失败
        val allUrls = urlRegex.findAll(text.trim())
            .map { it.value.trimEnd('。', '，', ',', '；', ';', ')', ']', '}', '"', '\'') }
            .toList()
        if (allUrls.isEmpty()) return null
        val url = allUrls.firstOrNull { isCloudShareUrl(it) } ?: return null
        // 夸克链接
        quarkShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.QUARK)
        }
        // UC 链接
        ucShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.UC)
        }
        // 迅雷链接
        xunleiShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.XUNLEI)
        }
        // 百度链接：https://pan.baidu.com/s/1xxxxx?pwd=xxxx
        baiduShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            // 百度 surl 不包含开头的 "1"（verify/list 接口用 1 后面的部分）
            val surl = sid.removePrefix("1")
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = surl, pwd = pwd, platform = SharePlatform.BAIDU)
        }
        // 百度 share/init 形态：https://pan.baidu.com/share/init?surl=xxxx 提取码 xxxx
        // 注意：此形态的 surl 参数值本身就是 surl（不含 /s/ 形态的前导 "1"），不能再 removePrefix("1")
        baiduInitShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { surl ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = surl, pwd = pwd, platform = SharePlatform.BAIDU)
        }
        // 139（和彩云）链接：https://yun.139.com/shareweb/#/w/i/{linkID} 提取码 xxxx
        c139ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.C139)
        }
        // 139 移动端短链：https://caiyun.139.com/m/i?{linkID} 提取码 xxxx
        c139CaiyunShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.C139)
        }
        // 123 云盘链接（3 种形态，按优先级匹配）
        pan123ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123)
        }
        pan123ShareSubRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123)
        }
        pan123SrrRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123)
        }
        return null
    }
}
