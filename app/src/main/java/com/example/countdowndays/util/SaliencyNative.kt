package com.example.countdowndays.util

import android.graphics.Bitmap

/**
 * U2-NetP 的 ncnn JNI 桥（原生实现见 src/main/cpp/saliency_jni.cpp）。
 * 库加载或推理失败时 SmartCrop 会自动回落手工重要性图，所以这里不做任何兜底。
 */
object SaliencyNative {

    init {
        System.loadLibrary("saliency")
    }

    /**
     * 对位图（须为 ARGB_8888）跑显著性推理。
     * @return 320×320 行优先的原始掩膜（未归一化），失败返回 null
     */
    external fun runMask(bitmap: Bitmap, param: ByteArray, model: ByteArray): FloatArray?
}
