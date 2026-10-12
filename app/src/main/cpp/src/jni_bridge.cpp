#include <jni.h>
#include <android/native_window_jni.h>
#include "NativeStabilizerEngine.hpp"

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_camera_nativeengine_NativeStabilizer_nativeInit(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return static_cast<jboolean>(NativeStabilizerEngine::getInstance().initialize());
}

JNIEXPORT void JNICALL
Java_com_example_camera_nativeengine_NativeStabilizer_nativeRelease(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    NativeStabilizerEngine::getInstance().release();
}

JNIEXPORT jboolean JNICALL
Java_com_example_camera_nativeengine_NativeStabilizer_nativeSetPreviewSurface(
    JNIEnv* env,
    jobject /* thiz */,
    jobject surface
) {
    ANativeWindow* window = nullptr;
    if (surface != nullptr) {
        window = ANativeWindow_fromSurface(env, surface);
    }
    return static_cast<jboolean>(NativeStabilizerEngine::getInstance().setPreviewWindow(window));
}

JNIEXPORT jboolean JNICALL
Java_com_example_camera_nativeengine_NativeStabilizer_nativeSetEncoderSurface(
    JNIEnv* env,
    jobject /* thiz */,
    jobject surface
) {
    ANativeWindow* window = nullptr;
    if (surface != nullptr) {
        window = ANativeWindow_fromSurface(env, surface);
    }
    return static_cast<jboolean>(NativeStabilizerEngine::getInstance().setEncoderWindow(window));
}

JNIEXPORT void JNICALL
Java_com_example_camera_nativeengine_NativeStabilizer_nativeSetEnabled(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jboolean enabled
) {
    NativeStabilizerEngine::getInstance().setStabilizationEnabled(enabled);
}

JNIEXPORT jboolean JNICALL
Java_com_example_camera_nativeengine_NativeStabilizer_nativeIsEnabled(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return static_cast<jboolean>(NativeStabilizerEngine::getInstance().isStabilizationEnabled());
}

JNIEXPORT void JNICALL
Java_com_example_camera_nativeengine_NativeStabilizer_nativeOnFrameAvailable(
    JNIEnv* env,
    jobject /* thiz */,
    jint oesTextureId,
    jfloatArray texMatrixArray
) {
    jfloat* matrixElements = nullptr;
    if (texMatrixArray != nullptr) {
        matrixElements = env->GetFloatArrayElements(texMatrixArray, nullptr);
    }

    NativeStabilizerEngine::getInstance().onFrameAvailable(oesTextureId, matrixElements);

    if (texMatrixArray != nullptr && matrixElements != nullptr) {
        env->ReleaseFloatArrayElements(texMatrixArray, matrixElements, JNI_ABORT);
    }
}

} // extern "C"
