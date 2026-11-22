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

    private class ImportanceMap(
        val width: Int,
        val height: Int,
        val integral: DoubleArray,
        /** 逐像素亮度（0..255），算卡片区域明暗用 */
        val lum: FloatArray,
        /** 重要性图的重心（0..1 的相对坐标），裁切窗会被它轻轻拉住 */
        val centroidX: Float,
        val centroidY: Float
    )

    /** 裁切结果：对齐 + 裁切窗内卡片区域的平均亮度（0..1，供卡片自适应字色） */
    data class CropResult(val alignment: BiasAlignment, val cardLuminance: Float)

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

    /** 同 focalAlignment，另附裁切窗内卡片区域的平均亮度；失败返回 null */
    suspend fun focalPoint(file: File, containerAspect: Float): CropResult? {
        if (containerAspect <= 0f || !file.exists()) return null
        val map = importanceMap(file) ?: return null
        return withContext(Dispatchers.Default) {
            val win = bestWindow(map, containerAspect)
            val alignment = alignmentOf(map, win)
            // 卡片区域 ≈ 裁切窗的横向 7%–93%、纵向 26%–74%（与展示页卡片实际占位对应）
            val x0 = win.left + (win.width * 0.07f).toInt()
            val x1 = win.left + (win.width * 0.93f).toInt()
            val y0 = win.top + (win.height * 0.26f).toInt()
            val y1 = win.top + (win.height * 0.74f).toInt()
            var sum = 0.0
            var count = 0
            for (y in y0 until y1.coerceAtMost(map.height)) {
                for (x in x0 until x1.coerceAtMost(map.width)) {
                    sum += map.lum[y * map.width + x]
                    count++
                }
            }
            val luminance = if (count > 0) (sum / count / 255.0).toFloat() else 0.5f
            CropResult(alignment, luminance)
        }
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
                // v3.15：肤色权重加大、边缘压低——人像照片里家具/条纹的强边缘
                // 曾把裁切窗从人脸边上拽走（用户实测「找不到主体」）
                importance[i] = edge * 0.75f + sat * 0.35f + skin * 1.1f
            }
        }

        // 重要性重心：裁切窗评分会被它拉住，避免窗被局部强信号拽偏
        var totalImp = 0.0
        var weightedX = 0.0
        var weightedY = 0.0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = importance[y * w + x].toDouble()
                totalImp += v
                weightedX += v * (x + 0.5) / w
                weightedY += v * (y + 0.5) / h
            }
        }
        val centroidX = if (totalImp > 0) (weightedX / totalImp).toFloat() else 0.5f
        val centroidY = if (totalImp > 0) (weightedY / totalImp).toFloat() else 0.5f

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
        return ImportanceMap(w, h, integral, lum, centroidX, centroidY)
    }

    private class Window(val left: Int, val top: Int, val width: Int, val height: Int)

    private fun bestWindow(map: ImportanceMap, containerAspect: Float): Window {
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
        if (winW >= w && winH >= h) return Window(0, 0, w, h)

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
                // 重心拉力：窗中心离重要性重心越远扣分越多，防局部强信号拽偏；
                // 另留极轻的画面中心先验，内容均匀时不贴边
                val cx = (left + winW / 2f) / w
                val cy = (top + winH / 2f) / h
                val centroidPenalty = 1.0 - 0.30 * (abs(cx - map.centroidX) + abs(cy - map.centroidY))
                val centerPenalty = 1.0 - 0.03 * (abs(cx - 0.5f) + abs(cy - 0.5f))
                val score = windowSum(left, top) * centroidPenalty * centerPenalty
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
        return Window(bestLeft, bestTop, winW, winH)
    }

    private fun alignmentOf(map: ImportanceMap, win: Window): BiasAlignment {
        val w = map.width
        val h = map.height
        val biasX = if (w > win.width) ((win.left.toFloat() / (w - win.width)) * 2f - 1f) else 0f
        val biasY = if (h > win.height) ((win.top.toFloat() / (h - win.height)) * 2f - 1f) else 0f
        return BiasAlignment(biasX.coerceIn(-1f, 1f), biasY.coerceIn(-1f, 1f))
    }

    private fun bestAlignment(map: ImportanceMap, containerAspect: Float): BiasAlignment =
        alignmentOf(map, bestWindow(map, containerAspect))
}
