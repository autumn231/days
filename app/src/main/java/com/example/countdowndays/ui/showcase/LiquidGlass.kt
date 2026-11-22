package com.example.countdowndays.ui.showcase

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/*
 * 真·液态玻璃：对卡片背后的背景做实时几何折射，而不是「半透 + 模糊」的仿制。
 *
 * 折射 AGSL 着色器（圆角矩形 SDF + 边缘折射 + 色散）移植自开源项目
 * Kyant0/AndroidLiquidGlass（Backdrop 库），Apache License 2.0：
 * https://github.com/Kyant0/AndroidLiquidGlass
 * 该库本体要求 Kotlin 2.3 + Compose 1.10，与本工程工具链（Kotlin 2.0 + Compose 1.7）
 * 不兼容，故只移植其着色器与参数约定，采样/绘制管线按本工程的 Compose 版本自研。
 *
 * 管线：背景绘制时录进 backdropLayer → 卡片绘制前把背景层按窗口坐标对齐
 * 录进 effectLayer 并挂上「提饱和 → 模糊 → 折射」链式 RenderEffect →
 * 裁进卡片形状画出，再叠一层薄 tint。需要 API 33+（RuntimeShader），
 * 低版本由调用方降级到毛玻璃样式。
 */

internal val liquidGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** 背景与卡片共享的采样状态：背景层 + 背景的实时布局坐标 */
class LiquidGlassState {
    var backdropLayer: GraphicsLayer? = null
    var sourceCoordinates: LayoutCoordinates? = null
}

/** 挂在背景上：每帧把背景内容录进采样层，并登记自己的布局坐标 */
fun Modifier.liquidSource(state: LiquidGlassState): Modifier = this
    .onGloballyPositioned { state.sourceCoordinates = it }
    .drawWithContent {
        val layer = state.backdropLayer
        if (liquidGlassSupported && layer != null) {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        } else {
            drawContent()
        }
    }

/**
 * 挂在卡片上：先把采样到的背景折射后画进卡片形状，再画卡片自身内容。
 * 玻璃画不出来（坐标未就绪等）时什么都不画，由调用方在下层垫的底色兜底。
 */
fun Modifier.liquidGlass(
    state: LiquidGlassState,
    effectLayer: GraphicsLayer?,
    shape: Shape,
    cornerRadiusPx: Float,
    tint: Color,
    blurRadiusPx: Float,
    refractionHeightPx: Float,
    refractionAmountPx: Float
): Modifier = composed {
    val holder = remember { CoordinatesHolder() }
    this
        .onGloballyPositioned { holder.coordinates = it }
        .drawWithContent {
            if (liquidGlassSupported && effectLayer != null) {
                drawLiquidGlass(
                    state = state,
                    effectLayer = effectLayer,
                    shape = shape,
                    glassCoordinates = holder.coordinates,
                    cornerRadiusPx = cornerRadiusPx,
                    tint = tint,
                    blurRadiusPx = blurRadiusPx,
                    refractionHeightPx = refractionHeightPx,
                    refractionAmountPx = refractionAmountPx
                )
            }
            drawContent()
        }
}

private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

private fun DrawScope.drawLiquidGlass(
    state: LiquidGlassState,
    effectLayer: GraphicsLayer,
    shape: Shape,
    glassCoordinates: LayoutCoordinates?,
    cornerRadiusPx: Float,
    tint: Color,
    blurRadiusPx: Float,
    refractionHeightPx: Float,
    refractionAmountPx: Float
) {
    val backdrop = state.backdropLayer ?: return
    val src = state.sourceCoordinates ?: return
    val self = glassCoordinates ?: return
    val srcPos = src.positionInWindow()
    val selfPos = self.positionInWindow()

    // 把背景层按窗口坐标对齐录进效果层：效果层里第 p 个像素，
    // 正是屏幕上卡片第 p 个像素背后的背景
    effectLayer.record {
        withTransform({
            translate(srcPos.x - selfPos.x, srcPos.y - selfPos.y)
        }) {
            drawLayer(backdrop)
        }
    }
    effectLayer.renderEffect = buildGlassEffectChain(
        width = size.width,
        height = size.height,
        cornerRadiusPx = cornerRadiusPx,
        blurRadiusPx = blurRadiusPx,
        refractionHeightPx = refractionHeightPx,
        refractionAmountPx = refractionAmountPx
    )

    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawLiquidGlass)) }
    clipPath(path) {
        drawLayer(effectLayer)
        drawRect(tint)
    }
}

private var cachedShader: RuntimeShader? = null
private var cachedChainKey: String? = null
private var cachedChain: RenderEffect? = null

private fun buildGlassEffectChain(
    width: Float,
    height: Float,
    cornerRadiusPx: Float,
    blurRadiusPx: Float,
    refractionHeightPx: Float,
    refractionAmountPx: Float
): RenderEffect {
    val key = "$width|$height|$cornerRadiusPx|$blurRadiusPx|$refractionHeightPx|$refractionAmountPx"
    if (key == cachedChainKey) cachedChain?.let { return it }

    val shader = cachedShader ?: RuntimeShader(REFRACTION_WITH_DISPERSION_AGSL).also { cachedShader = it }
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("offset", 0f, 0f)
    shader.setFloatUniform("cornerRadii", cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx)
    shader.setFloatUniform("refractionHeight", refractionHeightPx)
    // 上游约定：传给着色器的折射量取负
    shader.setFloatUniform("refractionAmount", -refractionAmountPx)
    shader.setFloatUniform("depthEffect", 1f)
    shader.setFloatUniform("chromaticAberration", 1f)
    val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")

    // 链：先轻微提饱和（vibrancy），再模糊，最后折射
    val saturation = RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(ColorMatrix().apply { setToSaturation(1.25f) }.values)
    )
    val blur = RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
    val chain = RenderEffect.createChainEffect(lens, RenderEffect.createChainEffect(blur, saturation))
    cachedChainKey = key
    cachedChain = chain
    return chain
}

// 折射着色器（带色散版）：移植自 Kyant0/AndroidLiquidGlass（Apache-2.0）
private val ROUNDED_RECT_SDF = """
float radiusAt(float2 centeredCoord, float4 radii) {
    if (centeredCoord.x >= 0.0) {
        if (centeredCoord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (centeredCoord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

private val REFRACTION_WITH_DISPERSION_AGSL = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""
