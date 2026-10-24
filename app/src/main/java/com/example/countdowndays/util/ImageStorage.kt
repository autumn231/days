package com.example.countdowndays.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File

/** 把用户选择的图片复制到内部存储，返回本地路径 */
object ImageStorage {

    /** 长边上限，避免把原图（可能 10MB+）整份塞进私有目录 */
    private const val MAX_EDGE = 1920
    private const val JPEG_QUALITY = 88

    private fun dir(context: Context): File =
        File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

    fun saveFromUri(context: Context, uri: Uri, prefix: String): String? {
        val target = File(dir(context), "${prefix}_${System.currentTimeMillis()}.jpg")
        return try {
            val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                saveScaled(context, uri, target)
            } else {
                // API 26/27 没有 ImageDecoder，退回原始拷贝
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null
            }
            if (written) {
                target.absolutePath
            } else {
                target.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            target.delete()
            null
        }
    }

    /**
     * 用 ImageDecoder 解码（会自动按 EXIF 摆正方向），等比缩放到长边 MAX_EDGE 后重新压缩。
     */
    private fun saveScaled(context: Context, uri: Uri, target: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = maxOf(w, h)
            if (longest > MAX_EDGE && longest > 0) {
                val ratio = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (w * ratio).toInt().coerceAtLeast(1),
                    (h * ratio).toInt().coerceAtLeast(1)
                )
            }
        }
        return try {
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun delete(path: String?) {
        if (path.isNullOrEmpty()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    /** 删除所有不在 keep 集合里的图片（保留仍在使用的） */
    fun cleanupOrphans(context: Context, keep: Set<String>) {
        runCatching {
            dir(context).listFiles()?.forEach { file ->
                if (file.absolutePath !in keep) file.delete()
            }
        }
    }
}
