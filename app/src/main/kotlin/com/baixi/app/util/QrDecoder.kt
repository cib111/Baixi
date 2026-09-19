package com.baixi.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * 相册二维码解码工具（解析页「二维码」入口用）。
 *
 * 流程：ContentResolver 读图 → 先只读尺寸（inJustDecodeBounds）→ 按最大边约 1600 采样二次解码
 * （避免大图 OOM）→ zxing 多格式读取（TRY_HARDER）→ 0/90/180/270 四个方向各试一次。
 * 全程 runCatching：任何异常/识别失败都返回 null，不抛给调用方。
 */
object QrDecoder {

    /** 解码时图片最大边像素（超过则降采样；识别二维码不需要原始分辨率） */
    private const val MAX_EDGE = 1600

    /** 解码相册二维码图片；失败返回 null（不抛异常） */
    fun decode(context: Context, uri: Uri): String? = runCatching {
        val bitmap = decodeBitmap(context, uri) ?: return@runCatching null
        // 相册图片可能带 EXIF 旋转（部分 ROM 导出图不落地旋转），四个方向逐一尝试
        for (rotation in intArrayOf(0, 90, 180, 270)) {
            val candidate = if (rotation == 0) bitmap else rotate(bitmap, rotation)
            decodeWithZxing(candidate)?.let { return@runCatching it }
        }
        null
    }.getOrNull()

    /** 读取并降采样图片为 ARGB_8888 Bitmap；读取失败返回 null */
    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
        val resolver = context.contentResolver
        // 第一次：只读尺寸，不分配像素内存
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            // 第二次：按采样率真正解码（ARGB_8888：RGBLuminanceSource 需要颜色信息）
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }.getOrNull()

    /** inSampleSize：不断二分直到最大边降到 maxEdge 以内 */
    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxEdge || h / 2 >= maxEdge) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    /** 按角度旋转位图（返回新位图） */
    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /** zxing 单次解码；未识别到码时返回 null（NotFoundException 已吞掉） */
    private fun decodeWithZxing(bitmap: Bitmap): String? = runCatching {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            null
        } else {
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            // 优先按二维码识别；TRY_HARDER 提升模糊/低对比度图片的识别率
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            )
            MultiFormatReader().apply { setHints(hints) }.decode(binary)?.text
        }
    }.getOrNull()
}
