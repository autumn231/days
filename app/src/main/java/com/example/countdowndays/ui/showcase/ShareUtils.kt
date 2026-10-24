package com.example.countdowndays.ui.showcase

import android.content.ContentValues
import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** 展示卡片的导出与分享 */
object ShareUtils {

    private fun writeToCache(context: Context, bitmap: Bitmap, name: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    fun shareCard(context: Context, image: ImageBitmap, title: String) {
        val bitmap = image.asAndroidBitmap()
        val file = writeToCache(context, bitmap, "days_${System.currentTimeMillis()}")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 保存到相册（Q 以上走 MediaStore，以下落到应用专属图片目录） */
    fun saveToGallery(context: Context, image: ImageBitmap, displayName: String): Boolean = runCatching {
        val bitmap = image.asAndroidBitmap()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Days")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "Days"
            ).apply { mkdirs() }
            val file = File(dir, "$displayName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        true
    }.getOrDefault(false)

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * 上下对称裁到指定高度（px），用于生成比整屏更修长的分享图。
 * 高度不小于原图时原样返回。
 */
fun ImageBitmap.cropCenterHeight(targetHeight: Int): ImageBitmap {
    val src = asAndroidBitmap()
    val height = targetHeight.coerceIn(1, src.height)
    if (height >= src.height) return this
    val top = (src.height - height) / 2
    return Bitmap.createBitmap(src, 0, top, src.width, height).asImageBitmap()
}
