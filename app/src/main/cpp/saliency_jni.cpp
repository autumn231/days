// U2-NetP 显著性推理的 ncnn JNI 桥。
// 预处理与离线验证同口径：位图 resize 到 320×320、ImageNet mean/std 归一化；
// 返回 out0 的原始掩膜（320×320 行优先 float），min-max 归一化在 Kotlin 侧做。
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <cstring>
#include <string>
#include <vector>

#include "ncnn/net.h"

#define LOG_TAG "SaliencyNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 最近一次失败的具体环节（供 Kotlin 侧取走显示到背景面板，避免静默失败无从查起）
static std::string g_last_error;

static jfloatArray fail(JNIEnv *env, const std::string &msg) {
    g_last_error = msg;
    LOGE("%s", msg.c_str());
    return nullptr;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_countdowndays_util_SaliencyNative_runMask(
        JNIEnv *env, jobject /* thiz */, jobject bitmap,
        jbyteArray param_data, jbyteArray model_data) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return fail(env, "AndroidBitmap_getInfo failed");
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return fail(env, "bitmap format=" + std::to_string(info.format) + " not RGBA_8888");
    }
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return fail(env, "AndroidBitmap_lockPixels failed");
    }
    const int kInput = 320;
    ncnn::Mat in = ncnn::Mat::from_pixels_resize(
            static_cast<const unsigned char *>(pixels), ncnn::Mat::PIXEL_RGBA2RGB,
            static_cast<int>(info.width), static_cast<int>(info.height),
            static_cast<int>(info.stride), kInput, kInput);
    AndroidBitmap_unlockPixels(env, bitmap);
    if (in.empty()) return fail(env, "from_pixels_resize produced empty mat");

    const float mean_vals[3] = {0.485f * 255.f, 0.456f * 255.f, 0.406f * 255.f};
    const float norm_vals[3] = {1.f / (0.229f * 255.f), 1.f / (0.224f * 255.f), 1.f / (0.225f * 255.f)};
    in.substract_mean_normalize(mean_vals, norm_vals);

    jbyte *param_bytes = env->GetByteArrayElements(param_data, nullptr);
    jbyte *model_bytes = env->GetByteArrayElements(model_data, nullptr);
    if (param_bytes == nullptr || model_bytes == nullptr) {
        if (param_bytes != nullptr) env->ReleaseByteArrayElements(param_data, param_bytes, JNI_ABORT);
        if (model_bytes != nullptr) env->ReleaseByteArrayElements(model_data, model_bytes, JNI_ABORT);
        return fail(env, "GetByteArrayElements returned null");
    }
    // load_param_mem 需要 NUL 结尾的字符串，先按长度拷一份
    std::string param_str(reinterpret_cast<const char *>(param_bytes),
                          static_cast<size_t>(env->GetArrayLength(param_data)));
    ncnn::Net net;
    int ret = net.load_param_mem(param_str.c_str());
    if (ret == 0) {
        ret = net.load_model(reinterpret_cast<const unsigned char *>(model_bytes));
    }
    env->ReleaseByteArrayElements(param_data, param_bytes, JNI_ABORT);
    env->ReleaseByteArrayElements(model_data, model_bytes, JNI_ABORT);
    if (ret != 0) {
        return fail(env, "ncnn load failed ret=" + std::to_string(ret));
    }

    ncnn::Extractor ex = net.create_extractor();
    ex.input("in0", in);
    ncnn::Mat out;
    int ex_ret = ex.extract("out0", out);
    if (ex_ret != 0 || out.empty()) {
        return fail(env, "extract failed ret=" + std::to_string(ex_ret));
    }
    if (out.w != kInput || out.h != kInput) {
        return fail(env, "bad output shape w=" + std::to_string(out.w) +
                         " h=" + std::to_string(out.h) + " c=" + std::to_string(out.c) +
                         " dims=" + std::to_string(out.dims));
    }
    const ncnn::Mat ch0 = out.c > 1 ? out.channel(0) : out;
    std::vector<float> buf(static_cast<size_t>(kInput) * kInput);
    for (int y = 0; y < kInput; y++) {
        const float *row = ch0.row(y);
        memcpy(buf.data() + static_cast<size_t>(y) * kInput, row, kInput * sizeof(float));
    }
    jfloatArray result = env->NewFloatArray(kInput * kInput);
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, kInput * kInput, buf.data());
    }
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_countdowndays_util_SaliencyNative_lastError(JNIEnv *env, jobject /* thiz */) {
    return env->NewStringUTF(g_last_error.c_str());
}
