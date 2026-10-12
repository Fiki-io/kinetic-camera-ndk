#include "NativeStabilizerEngine.hpp"
#include <android/log.h>

#define LOG_TAG "NativeStabilizer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

NativeStabilizerEngine& NativeStabilizerEngine::getInstance() {
    static NativeStabilizerEngine instance;
    return instance;
}

NativeStabilizerEngine::NativeStabilizerEngine()
    : sensorTracker(std::make_unique<SensorTracker>()),
      mesh(std::make_unique<RollingShutterMesh>(32, 32)),
      renderer(std::make_unique<GpuWarpRenderer>()),
      isEnabled(true),
      isInitialized(false) {}

NativeStabilizerEngine::~NativeStabilizerEngine() {
    release();
}

bool NativeStabilizerEngine::initialize() {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (isInitialized) return true;

    if (!renderer->initEGL()) {
        return false;
    }

    sensorTracker->start();

    isInitialized = true;
    LOGI("NativeStabilizerEngine EGL pipeline berhasil aktif");
    return true;
}

void NativeStabilizerEngine::release() {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (!isInitialized) return;

    if (sensorTracker) sensorTracker->stop();
    if (renderer) renderer->release();

    isInitialized = false;
    LOGI("NativeStabilizerEngine berhasil dilepas");
}

bool NativeStabilizerEngine::setPreviewWindow(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (!isInitialized) {
        if (!renderer->initEGL()) return false;
        sensorTracker->start();
        isInitialized = true;
    }
    return renderer->setPreviewWindow(window);
}

bool NativeStabilizerEngine::setEncoderWindow(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (!isInitialized) {
        if (!renderer->initEGL()) return false;
        sensorTracker->start();
        isInitialized = true;
    }
    return renderer->setEncoderWindow(window);
}

void NativeStabilizerEngine::setStabilizationEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(engineMutex);
    isEnabled = enabled;
}

bool NativeStabilizerEngine::isStabilizationEnabled() const {
    std::lock_guard<std::mutex> lock(engineMutex);
    return isEnabled;
}

void NativeStabilizerEngine::onFrameAvailable(int oesTextureId, const float* texMatrix) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (!isInitialized) return;

    float pitch = 0.0f, yaw = 0.0f, roll = 0.0f;
    if (isEnabled) {
        sensorTracker->getLatestAngularDisplacement(&pitch, &yaw, &roll);
        // Terapkan koreksi deformasi scanline non-linear untuk menyerap hentakan langkah kaki
        mesh->updateMesh(pitch, yaw, roll, 0.025f);
    } else {
        mesh->updateMesh(0.0f, 0.0f, 0.0f, 0.0f);
    }

    renderer->renderToSurfaces(static_cast<GLuint>(oesTextureId), *mesh, texMatrix);
}
