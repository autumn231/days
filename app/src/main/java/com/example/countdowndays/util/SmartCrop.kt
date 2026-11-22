package com.example.countdowndays.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.BiasAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 内容感知裁切：思路移植自开源 smartcrop.js（jwagner，MIT）的核心算法——
 * 拉普拉斯边缘 + 饱和度 + 肤色三路信号合成「重要性图」，再用积分图滑动窗口
 * 找出目标宽高比下总重要性最高的裁切窗，最后把窗的位置换算成
 * ContentScale.Crop 用的 BiasAlignment。背景仍旧铺满全屏，
 * 只是被裁掉的那一侧尽量选不重要的内容，而不是永远居中裁。
 */
object SmartCrop {

    private class ImportanceMap(val width: Int, val height: Int, val integral: DoubleArray)

    private val cache = ConcurrentHashMap<String, ImportanceMap>()

    /** 分析图最长边：够判主体位置，又足够快 */
    private const val ANALYZE_SIZE = 128

    /**
     * 返回给定图片文件在目标容器宽高比（width/height）下的最佳裁切对齐。
     * 任何失败（解码失败等）都退回居中，绝不抛异常。
     */
    suspend fun focalAlignment(file: File, containerAspect: Float): BiasAlignment {
        if (containerAspect <= 0f || !file.exists()) return BiasAlignment(0f, 0f)
        val map = importanceMap(file) ?: return BiasAlignment(0f, 0f)
        return withContext(Dispatchers.Default) { bestAlignment(map, containerAspect) }
    }

    private suspend fun importanceMap(file: File): ImportanceMap? {
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        cache[key]?.let { return it }
        val map = withContext(Dispatchers.Default) { runCatching { analyze(file) }.getOrNull() }
        if (map != null) cache[key] = map
        return map
    }

    private fun analyze(file: File): ImportanceMap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > ANALYZE_SIZE * 2 || bounds.outHeight / sample > ANALYZE_SIZE * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp: Bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()
        if (w < 4 || h < 4) return null

        // 亮度（拉普拉斯边缘用）
        val lum = FloatArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            lum[i] = 0.2126f * (c shr 16 and 0xFF) + 0.7152f * (c shr 8 and 0xFF) + 0.0722f * (c and 0xFF)
        }

        val importance = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = pixels[i]
                val r = (c shr 16 and 0xFF) / 255f
                val g = (c shr 8 and 0xFF) / 255f
                val b = (c and 0xFF) / 255f

                // 边缘：拉普拉斯，边界处取 0
                var edge = 0f
                if (x in 1 until w - 1 && y in 1 until h - 1) {
                    edge = abs(4 * lum[i] - lum[i - 1] - lum[i + 1] - lum[i - w] - lum[i + w]) / 255f
                }
                // 饱和度（HSV 的 S）
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                val sat = if (mx > 0f) (mx - mn) / mx else 0f
                // 肤色：归一化 RGB 靠近参考肤色 (0.78, 0.57, 0.44) 且亮度适中时加权
                var skin = 0f
                val sum = r + g + b
                if (sum > 0.45f && sum < 2.7f) {
                    val nr = r / sum * 3f
                    val ng = g / sum * 3f
                    val nb = b / sum * 3f
                    val d = (nr - 1.18f) * (nr - 1.18f) + (ng - 0.86f) * (ng - 0.86f) + (nb - 0.66f) * (nb - 0.66f)
                    if (d < 0.10f) skin = (0.10f - d) / 0.10f
                }
                importance[i] = edge * 0.9f + sat * 0.35f + skin * 0.8f
            }
        }

        // 积分图（带一圈零边，尺寸 (w+1)×(h+1)）
        val iw = w + 1
        val integral = DoubleArray(iw * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0.0
            for (x in 0 until w) {
                rowSum += importance[y * w + x]
                integral[(y + 1) * iw + (x + 1)] = integral[y * iw + (x + 1)] + rowSum
            }
        }
        return ImportanceMap(w, h, integral)
    }

    private fun bestAlignment(map: ImportanceMap, containerAspect: Float): BiasAlignment {
        val w = map.width
        val h = map.height
        val imageAspect = w.toFloat() / h.toFloat()
        // Crop 的裁切窗 = 图内能容纳的最大目标宽高比矩形
        val winW: Int
        val winH: Int
        if (imageAspect > containerAspect) {
            winH = h
            winW = (h * containerAspect).toInt().coerceIn(1, w)
        } else {
            winW = w
            winH = (w / containerAspect).toInt().coerceIn(1, h)
        }
        if (winW >= w && winH >= h) return BiasAlignment(0f, 0f)

        val iw = w + 1
        fun windowSum(left: Int, top: Int): Double {
            val right = left + winW
            val bottom = top + winH
            return map.integral[bottom * iw + right] - map.integral[top * iw + right] -
                map.integral[bottom * iw + left] + map.integral[top * iw + left]
        }

        var bestScore = Double.NEGATIVE_INFINITY
        var bestLeft = (w - winW) / 2
        var bestTop = (h - winH) / 2
        val stepX = max(1, (w - winW) / 24)
        val stepY = max(1, (h - winH) / 24)
        var top = 0
        while (top <= h - winH) {
            var left = 0
            while (left <= w - winW) {
                // 轻微中心先验：同等内容时偏向居中，避免裁切窗贴边跳变
                val cx = (left + winW / 2f) / w - 0.5f
                val cy = (top + winH / 2f) / h - 0.5f
                val centerPenalty = 1.0 - 0.06 * (abs(cx) + abs(cy))
                val score = windowSum(left, top) * centerPenalty
                if (score > bestScore) {
                    bestScore = score
                    bestLeft = left
                    bestTop = top
                }
                if (left == w - winW) break
                left = min(left + stepX, w - winW)
            }
            if (top == h - winH) break
            top = min(top + stepY, h - winH)
        }

        val biasX = if (w > winW) ((bestLeft.toFloat() / (w - winW)) * 2f - 1f) else 0f
        val biasY = if (h > winH) ((bestTop.toFloat() / (h - winH)) * 2f - 1f) else 0f
        return BiasAlignment(biasX.coerceIn(-1f, 1f), biasY.coerceIn(-1f, 1f))
    }
}
