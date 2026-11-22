LOCAL_PATH := $(call my-dir)

# ncnn 官方 Android 静态库包（ncnn-20250503-android 解压后的目录）：
# 可用环境变量 NCNN_DIR 覆盖，默认在跳板机工具目录（与 JDK / Android SDK 同级约定）
NCNN_DIR ?= /data/home/tools/ncnn-android

include $(CLEAR_VARS)
LOCAL_MODULE := ncnn
LOCAL_SRC_FILES := $(NCNN_DIR)/$(TARGET_ARCH_ABI)/lib/libncnn.a
LOCAL_EXPORT_C_INCLUDES := $(NCNN_DIR)/$(TARGET_ARCH_ABI)/include
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := saliency
LOCAL_SRC_FILES := saliency_jni.cpp
LOCAL_STATIC_LIBRARIES := ncnn
# 官方 ncnn 包带 OpenMP（kmp_* 符号），编译与链接都要打开
LOCAL_CPPFLAGS += -fopenmp
# max-page-size=16384：16KB 页内核的新系统拒绝加载只有 4KB 对齐的 .so（会静默回落旧算法）
LOCAL_LDFLAGS += -fopenmp -static-openmp -Wl,-z,max-page-size=16384
LOCAL_LDLIBS := -landroid -llog -ljnigraphics
include $(BUILD_SHARED_LIBRARY)
