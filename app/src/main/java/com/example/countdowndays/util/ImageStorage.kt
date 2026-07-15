package com.example.countdowndays.util

import android.content.Context
import android.net.Uri
import java.io.File

/** 把用户选择的图片复制到内部存储，返回本地路径 */
object ImageStorage {

    private fun dir(context: Context): File =
        File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

    fun saveFromUri(context: Context, uri: Uri, prefix: String): String? {
        return try {
            val target = File(dir(context), "${prefix}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun delete(path: String?) {
        if (path.isNullOrEmpty()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
