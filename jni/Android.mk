LOCAL_PATH := $(call my-dir)

# Prebuilt libvpx static library, built directly on Linux rather than through
# the NDK. See README-libvpx.txt.
include $(CLEAR_VARS)
LOCAL_MODULE    := libvpx
LOCAL_SRC_FILES := libvpx_prebuilt_libs/$(TARGET_ARCH_ABI)/libvpx.a
include $(PREBUILT_STATIC_LIBRARY)


# Helper functions to write WebM files. These will build with the NDK.
include $(CLEAR_VARS)
LOCAL_MODULE    := libwebmenc
LOCAL_SRC_FILES := libvpx/webmenc.cc \
                   libvpx/third_party/libwebm/mkvmuxer.cpp \
                   libvpx/third_party/libwebm/mkvmuxerutil.cpp \
                   libvpx/third_party/libwebm/mkvparser.cpp \
                   libvpx/third_party/libwebm/mkvreader.cpp \
                   libvpx/third_party/libwebm/mkvwriter.cpp
include $(BUILD_STATIC_LIBRARY)


# WireGoggles code.
include $(CLEAR_VARS)
LOCAL_MODULE    := wiregoggles
LOCAL_SRC_FILES := sobel.c wg_video.c
LOCAL_STATIC_LIBRARIES := libvpx libwebmenc
LOCAL_C_INCLUDES += jni/libvpx/
include $(BUILD_SHARED_LIBRARY)
