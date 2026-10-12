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

    if (!renderer->init()) {
        return false;
    }

    if (!sensorTracker->start()) {
        LOGI("SensorTracker fallback atau simulasi aktif");
    }

    isInitialized = true;
    LOGI("NativeStabilizerEngine berhasil diinisialisasi");
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

void NativeStabilizerEngine::setStabilizationEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(engineMutex);
    isEnabled = enabled;
}

bool NativeStabilizerEngine::isStabilizationEnabled() const {
    std::lock_guard<std::mutex> lock(engineMutex);
    return isEnabled;
}

void NativeStabilizerEngine::processAndRender(int oesTextureId, int width, int height) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (!isInitialized) return;

    float pitch = 0.0f, yaw = 0.0f, roll = 0.0f;
    if (isEnabled) {
        sensorTracker->getLatestAngularDisplacement(&pitch, &yaw, &roll);
        mesh->updateMesh(pitch, yaw, roll, 0.02f);
    } else {
        mesh->updateMesh(0.0f, 0.0f, 0.0f, 0.0f);
    }

    renderer->render(static_cast<GLuint>(oesTextureId), *mesh, width, height);
}
