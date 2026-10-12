#include <jni.h>
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
Java_com_example_camera_nativeengine_NativeStabilizer_nativeRender(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint oesTextureId,
    jint width,
    jint height
) {
    NativeStabilizerEngine::getInstance().processAndRender(oesTextureId, width, height);
}

} // extern "C"
