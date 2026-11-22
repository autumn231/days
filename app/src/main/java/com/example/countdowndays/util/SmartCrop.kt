package com.example.countdowndays.util

import android.content.Context
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
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 内容感知裁切 + 卡片区域明暗分析（v3.16 起统一架构）：
 *
 * 主体检测改用端上小模型 U2-NetP（显著性检测，rembg 同款轻量引擎，Apache-2.0；
 * 模型转 ncnn 格式 fp16 存储仅 2.3MB）经 ncnn 推理出主体掩膜，掩膜即「重要性图」——
 * 替代 v3.12–v3.15 手工合成的边缘+饱和度+肤色重要性图（那套求和式打分会被
 * 亮窗、条纹等强信号带偏，实测咖啡照片裁切窗顶格右移 0.79 vs 人脸 0.47；
 * 换掩膜后 U2-NetP 选窗 0.54）。模型/运行时不可用时回落手工重要性图，行为不劣于 v3.15。
 *
 * 同一趟分析还顺带按「照片 × 背景压暗罩 × 玻璃效果」的合成链模拟卡面实际背景，
 * 分区（数字/小标题/起始日/宣传栏）算 WCAG 相对亮度，供卡片逐区选字色。
 */
object SmartCrop {

    /** 裁切结果：对齐 + 两种玻璃合成下四个文字区的 WCAG 相对亮度（0..1） */
    class CropResult(
        val alignment: BiasAlignment,
        /** [数字区, 小标题区, 起始日区, 宣传栏区]，毛玻璃合成后的背景亮度 */
        val frostedZoneLum: FloatArray,
        /** 同上，液态玻璃（深色 tint）合成后 */
        val liquidZoneLum: FloatArray
    )

    private class Grid(val width: Int, val height: Int, val values: FloatArray)

    private class AnalysisImage(val width: Int, val height: Int, val pixels: IntArray)

    private val resultCache = ConcurrentHashMap<String, CropResult>()

    /** 单次分析的诊断（哪条引擎出的图、回落时的失败原因），供背景面板显形 */
    class Report(val engine: String, val error: String?)

    private val reports = ConcurrentHashMap<String, Report>()

    @Volatile
    private var lastSaliencyError: String? = null

    fun reportFor(file: File): Report? = reports[file.absolutePath]

    /** 分析图最长边（分区亮度用，够细即可） */
    private const val ANALYZE_SIZE = 384

    /** 掩膜重要性图的长边分辨率 */
    private const val GRID_SIZE = 144

    private const val MODEL_INPUT = 320

    // ---- ncnn 模型字节缓存（assets 里 param+bin 共约 2.3MB，读一次常驻） ----
    @Volatile
    private var ncnnParam: ByteArray? = null

    @Volatile
    private var ncnnModel: ByteArray? = null

    /**
     * 分析照片在目标容器宽高比下的最佳裁切 + 卡片分区亮度。
     * 任何失败返回 null（调用方退回居中裁切 + 静态字色），绝不抛异常。
     */
    suspend fun focalPoint(context: Context, file: File, containerAspect: Float): CropResult? {
        if (containerAspect <= 0f || !file.exists()) return null
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}:" +
            (containerAspect * 100).roundToInt()
        resultCache[key]?.let { return it }
        val result = withContext(Dispatchers.Default) {
            runCatching { analyze(context, file, containerAspect) }.getOrNull()
        }
        if (result != null) resultCache[key] = result
        return result
    }

    private fun analyze(context: Context, file: File, containerAspect: Float): CropResult? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var engine = "ncnn"
        val grid = saliencyGrid(context, file, bounds.outWidth, bounds.outHeight)
            ?: run {
                engine = "fallback"
                handcraftedGrid(file)
            }
            ?: return null
        val win = bestWindow(grid, containerAspect)
        val alignment = alignmentOf(grid, win)

        val img = decodeAnalysis(file) ?: return null
        val zones = zoneLuminances(img, win, grid)
        reports[file.absolutePath] =
            Report(engine, if (engine == "fallback") lastSaliencyError else null)
        return CropResult(alignment, zones.first, zones.second)
    }

    // ---------------- 主体掩膜（U2-NetP） ----------------

    /** 经 ncnn 跑 U2-NetP 出显著性掩膜，缩成重要性图；失败返回 null 由调用方回落 */
    private fun saliencyGrid(context: Context, file: File, photoW: Int, photoH: Int): Grid? {
        return try {
            val param = ncnnParam ?: context.assets.open("u2netp.ncnn.param").use { it.readBytes() }
                .also { ncnnParam = it }
            val model = ncnnModel ?: context.assets.open("u2netp.ncnn.bin").use { it.readBytes() }
                .also { ncnnModel = it }
            // 解码到不小于模型输入的尺寸即可，缩放到 320×320 在原生层做
            var sample = 1
            while (photoW / sample > MODEL_INPUT * 2 || photoH / sample > MODEL_INPUT * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp: Bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            if (bmp.config != Bitmap.Config.ARGB_8888) {
                val converted = bmp.copy(Bitmap.Config.ARGB_8888, false)
                bmp.recycle()
                bmp = converted
            }
            val mask = SaliencyNative.runMask(bmp, param, model)
            bmp.recycle()
            if (mask == null || mask.size != MODEL_INPUT * MODEL_INPUT) return null
            // min-max 归一化（与 U2-Net 官方后处理一致；转换前后选窗一致性已离线验证）
            var mn = Float.MAX_VALUE
            var mx = -Float.MAX_VALUE
            for (v in mask) {
                if (v < mn) mn = v
                if (v > mx) mx = v
            }
            val range = max(mx - mn, 1e-8f)
            // 掩膜块平均缩到重要性图尺寸（保持照片宽高比）
            val gridW = GRID_SIZE
            val gridH = (GRID_SIZE.toFloat() * photoH / photoW).roundToInt().coerceIn(24, GRID_SIZE)
            val values = FloatArray(gridW * gridH)
            for (gy in 0 until gridH) {
                val y0 = gy * MODEL_INPUT / gridH
                val y1 = max(y0 + 1, (gy + 1) * MODEL_INPUT / gridH)
                for (gx in 0 until gridW) {
                    val x0 = gx * MODEL_INPUT / gridW
                    val x1 = max(x0 + 1, (gx + 1) * MODEL_INPUT / gridW)
                    var sum = 0f
                    var n = 0
                    for (y in y0 until y1) for (x in x0 until x1) {
                        sum += (mask[y * MODEL_INPUT + x] - mn) / range
                        n++
                    }
                    values[gy * gridW + gx] = if (n > 0) sum / n else 0f
                }
            }
            Grid(gridW, gridH, values)
        } catch (t: Throwable) {
            // 失败原因留档，背景面板里能直接看到（静默回落曾让真机问题无从查起）
            lastSaliencyError = generateSequence(t) { it.cause }
                .joinToString(" ← ") { it.toString() }
                .take(220)
            null
        }
    }

    // ---------------- 回落：手工重要性图（v3.15 同款） ----------------

    private fun handcraftedGrid(file: File): Grid? {
        val img = decodeAnalysis(file, longSide = 128) ?: return null
        val w = img.width
        val h = img.height
        val pixels = img.pixels
        val lum = FloatArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            lum[i] = 0.2126f * (c shr 16 and 0xFF) + 0.7152f * (c shr 8 and 0xFF) + 0.0722f * (c and 0xFF)
        }
        val values = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = pixels[i]
                val r = (c shr 16 and 0xFF) / 255f
                val g = (c shr 8 and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                var edge = 0f
                if (x in 1 until w - 1 && y in 1 until h - 1) {
                    edge = abs(4 * lum[i] - lum[i - 1] - lum[i + 1] - lum[i - w] - lum[i + w]) / 255f
                }
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                val sat = if (mx > 0f) (mx - mn) / mx else 0f
                var skin = 0f
                val sum = r + g + b
                if (sum > 0.45f && sum < 2.7f) {
                    val nr = r / sum * 3f
                    val ng = g / sum * 3f
                    val nb = b / sum * 3f
                    val d = (nr - 1.18f) * (nr - 1.18f) + (ng - 0.86f) * (ng - 0.86f) + (nb - 0.66f) * (nb - 0.66f)
                    if (d < 0.10f) skin = (0.10f - d) / 0.10f
                }
                values[i] = edge * 0.75f + sat * 0.35f + skin * 1.1f
            }
        }
        return Grid(w, h, values)
    }

    // ---------------- 裁切窗搜索 ----------------

    private class Window(val left: Int, val top: Int, val width: Int, val height: Int)

    private fun bestWindow(grid: Grid, containerAspect: Float): Window {
        val w = grid.width
        val h = grid.height
        val imageAspect = w.toFloat() / h.toFloat()
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

        // 积分图
        val iw = w + 1
        val integral = DoubleArray(iw * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0.0
            for (x in 0 until w) {
                rowSum += grid.values[y * w + x]
                integral[(y + 1) * iw + (x + 1)] = integral[y * iw + (x + 1)] + rowSum
            }
        }
        fun windowSum(left: Int, top: Int): Double {
            val right = left + winW
            val bottom = top + winH
            return integral[bottom * iw + right] - integral[top * iw + right] -
                integral[bottom * iw + left] + integral[top * iw + left]
        }
        // 掩膜重心（只作同分时的轻拉力，主体掩膜本身已是强信号，不再需要 v3.15 的重罚分）
        var total = 0.0
        var wx = 0.0
        var wy = 0.0
        for (y in 0 until h) for (x in 0 until w) {
            val v = grid.values[y * w + x].toDouble()
            total += v
            wx += v * (x + 0.5) / w
            wy += v * (y + 0.5) / h
        }
        val ccx = if (total > 0) wx / total else 0.5
        val ccy = if (total > 0) wy / total else 0.5

        var bestScore = Double.NEGATIVE_INFINITY
        var bestLeft = (w - winW) / 2
        var bestTop = (h - winH) / 2
        for (top in 0..(h - winH)) {
            for (left in 0..(w - winW)) {
                val cx = (left + winW / 2f) / w
                val cy = (top + winH / 2f) / h
                val pull = 1.0 - 0.05 * (abs(cx - ccx) + abs(cy - ccy))
                val score = windowSum(left, top) * pull
                if (score > bestScore) {
                    bestScore = score
                    bestLeft = left
                    bestTop = top
                }
            }
        }
        return Window(bestLeft, bestTop, winW, winH)
    }

    private fun alignmentOf(grid: Grid, win: Window): BiasAlignment {
        val w = grid.width
        val h = grid.height
        val biasX = if (w > win.width) ((win.left.toFloat() / (w - win.width)) * 2f - 1f) else 0f
        val biasY = if (h > win.height) ((win.top.toFloat() / (h - win.height)) * 2f - 1f) else 0f
        return BiasAlignment(biasX.coerceIn(-1f, 1f), biasY.coerceIn(-1f, 1f))
    }

    // ---------------- 分区亮度（合成模拟） ----------------

    private fun decodeAnalysis(file: File, longSide: Int = ANALYZE_SIZE): AnalysisImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > longSide * 2 || bounds.outHeight / sample > longSide * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp: Bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w < 4 || h < 4) {
            bmp.recycle()
            return null
        }
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()
        return AnalysisImage(w, h, pixels)
    }

    /**
     * 按合成链模拟卡面背景并分区算 WCAG 亮度：
     * 照片 → 乘背景压暗罩（黑 0.42→0.62 自上而下）→ 盒式模糊（近似 haze/着色器模糊）
     * → 毛玻璃再叠奶白 tint 0.42、液态再乘深色 tint 0.70。
     * 返回 (毛玻璃四区亮度, 液态四区亮度)，区序 [数字, 小标题, 起始日, 宣传栏]。
     */
    private fun zoneLuminances(img: AnalysisImage, win: Window, grid: Grid): Pair<FloatArray, FloatArray> {
        val w = img.width
        val h = img.height
        // 裁切窗换算到分析图坐标（窗口定义在重要性图坐标系，两者都映射到原图比例）
        val winLeftF = win.left.toFloat() / grid.width
        val winTopF = win.top.toFloat() / grid.height
        val winWF = win.width.toFloat() / grid.width
        val winHF = win.height.toFloat() / grid.height

        // 压暗后的 RGB（0..1），压暗系数随屏幕纵向位置走（窗口即满屏）
        val dark = FloatArray(w * h * 3)
        for (y in 0 until h) {
            val imgYF = (y + 0.5f) / h
            val screenY = ((imgYF - winTopF) / winHF).coerceIn(0f, 1f)
            val keep = 1f - (0.42f + 0.20f * screenY)
            for (x in 0 until w) {
                val c = img.pixels[y * w + x]
                val i = (y * w + x) * 3
                dark[i] = (c shr 16 and 0xFF) / 255f * keep
                dark[i + 1] = (c shr 8 and 0xFF) / 255f * keep
                dark[i + 2] = (c and 0xFF) / 255f * keep
            }
        }
        val blurred = boxBlur(boxBlur(dark, w, h, 5), w, h, 5)

        // 四区在屏幕纵向的带（与展示页卡片布局对应），横向取窗口内 8%–92%
        val bands = arrayOf(
            floatArrayOf(0.38f, 0.50f),
            floatArrayOf(0.50f, 0.56f),
            floatArrayOf(0.57f, 0.64f),
            floatArrayOf(0.63f, 0.69f)
        )
        val cream = floatArrayOf(253f / 255f, 251f / 255f, 247f / 255f)
        val frosted = FloatArray(4)
        val liquid = FloatArray(4)
        for (z in 0 until 4) {
            val x0 = ((winLeftF + 0.08f * winWF) * w).toInt().coerceIn(0, w - 1)
            val x1 = ((winLeftF + 0.92f * winWF) * w).toInt().coerceIn(x0 + 1, w)
            val y0 = ((winTopF + bands[z][0] * winHF) * h).toInt().coerceIn(0, h - 1)
            val y1 = ((winTopF + bands[z][1] * winHF) * h).toInt().coerceIn(y0 + 1, h)
            var sr = 0.0
            var sg = 0.0
            var sb = 0.0
            var n = 0
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val i = (y * w + x) * 3
                    sr += blurred[i]
                    sg += blurred[i + 1]
                    sb += blurred[i + 2]
                    n++
                }
            }
            if (n == 0) {
                frosted[z] = 0.5f
                liquid[z] = 0.5f
                continue
            }
            val r = (sr / n).toFloat()
            val g = (sg / n).toFloat()
            val b = (sb / n).toFloat()
            frosted[z] = wcagLuminance(r * 0.58f + cream[0] * 0.42f, g * 0.58f + cream[1] * 0.42f, b * 0.58f + cream[2] * 0.42f)
            liquid[z] = wcagLuminance(r * 0.70f, g * 0.70f, b * 0.70f)
        }
        return frosted to liquid
    }

    private fun wcagLuminance(r: Float, g: Float, b: Float): Float {
        fun lin(c: Float): Float {
            val v = c.coerceIn(0f, 1f)
            return if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
    }

    /** 可分离盒式模糊（对压暗后的 RGB 平面数组操作） */
    private fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val winSize = radius * 2 + 1
        // 横向
        for (y in 0 until h) {
            for (ch in 0 until 3) {
                var acc = 0f
                for (k in -radius..radius) {
                    val x = k.coerceIn(0, w - 1)
                    acc += src[(y * w + x) * 3 + ch]
                }
                for (x in 0 until w) {
                    tmp[(y * w + x) * 3 + ch] = acc / winSize
                    val xOut = (x - radius).coerceIn(0, w - 1)
                    val xIn = (x + radius + 1).coerceIn(0, w - 1)
                    acc += src[(y * w + xIn) * 3 + ch] - src[(y * w + xOut) * 3 + ch]
                }
            }
        }
        // 纵向
        for (x in 0 until w) {
            for (ch in 0 until 3) {
                var acc = 0f
                for (k in -radius..radius) {
                    val y = k.coerceIn(0, h - 1)
                    acc += tmp[(y * w + x) * 3 + ch]
                }
                for (y in 0 until h) {
                    out[(y * w + x) * 3 + ch] = acc / winSize
                    val yOut = (y - radius).coerceIn(0, h - 1)
                    val yIn = (y + radius + 1).coerceIn(0, h - 1)
                    acc += tmp[(yIn * w + x) * 3 + ch] - tmp[(yOut * w + x) * 3 + ch]
                }
            }
        }
        return out
    }
}
