#include "SensorTracker.hpp"
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "SensorTrackerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

SensorTracker::SensorTracker()
    : sensorManager(nullptr),
      gyroSensor(nullptr),
      eventQueue(nullptr),
      looper(nullptr),
      rawPitch(0.0f),
      rawYaw(0.0f),
      rawRoll(0.0f),
      smoothPitch(0.0f),
      smoothYaw(0.0f),
      smoothRoll(0.0f),
      lastTimestampNs(0),
      isRunning(false) {}

SensorTracker::~SensorTracker() {
    stop();
}

bool SensorTracker::start() {
    std::lock_guard<std::mutex> lock(dataMutex);
    if (isRunning) return true;

    sensorManager = ASensorManager_getInstance();
    if (!sensorManager) return false;

    gyroSensor = ASensorManager_getDefaultSensor(sensorManager, ASENSOR_TYPE_GYROSCOPE);
    if (!gyroSensor) return false;

    looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    eventQueue = ASensorManager_createEventQueue(sensorManager, looper, 1, nullptr, nullptr);
    if (!eventQueue) return false;

    ASensorEventQueue_enableSensor(eventQueue, gyroSensor);
    // 200 Hz sampling (5000 microseconds)
    ASensorEventQueue_setEventRate(eventQueue, gyroSensor, 5000);

    isRunning = true;
    lastTimestampNs = 0;
    LOGI("SensorTracker 200Hz loop aktif");
    return true;
}

void SensorTracker::stop() {
    std::lock_guard<std::mutex> lock(dataMutex);
    if (!isRunning) return;

    if (eventQueue && gyroSensor) {
        ASensorEventQueue_disableSensor(eventQueue, gyroSensor);
    }
    if (sensorManager && eventQueue) {
        ASensorManager_destroyEventQueue(sensorManager, eventQueue);
        eventQueue = nullptr;
    }

    isRunning = false;
    reset();
}

void SensorTracker::pollEvents() {
    if (!eventQueue) return;

    ASensorEvent event;
    while (ASensorEventQueue_getEvents(eventQueue, &event, 1) > 0) {
        if (event.type != ASENSOR_TYPE_GYROSCOPE) continue;

        if (lastTimestampNs != 0) {
            float dt = (event.timestamp - lastTimestampNs) * 1.0e-9f;
            if (dt > 0.0001f && dt < 0.05f) {
                processSample(event.vector.x, event.vector.y, event.vector.z, dt);
            }
        }
        lastTimestampNs = event.timestamp;
    }
}

void SensorTracker::processSample(float gx, float gy, float gz, float dt) {
    // 1. Akumulasi pergerakan fisik riil sensor
    rawPitch += gx * dt;
    rawYaw   += gy * dt;
    rawRoll  += gz * dt;

    // 2. Lintasan kamera virtual yang halus (Virtual Gimbal Panning Low-Pass)
    // alpha 0.965 pada 200 Hz memberikan waktu respons ~0.7 detik (sangat mulus menahan hentakan kaki)
    const float alpha = 0.965f;
    smoothPitch = alpha * smoothPitch + (1.0f - alpha) * rawPitch;
    smoothYaw   = alpha * smoothYaw   + (1.0f - alpha) * rawYaw;
    smoothRoll  = alpha * smoothRoll  + (1.0f - alpha) * rawRoll;

    // 3. Batasi drift jangka panjang
    rawPitch *= 0.999f;
    rawYaw   *= 0.999f;
    rawRoll  *= 0.999f;
}

void SensorTracker::getLatestAngularDisplacement(float* outPitch, float* outYaw, float* outRoll) {
    std::lock_guard<std::mutex> lock(dataMutex);
    pollEvents();

    // Kompensasi adalah SELISIH antara lintasan halus dan pergerakan mendadak
    // (Peredaman hentakan langkah kaki dan getaran tangan 1:1)
    float diffPitch = smoothPitch - rawPitch;
    float diffYaw   = smoothYaw - rawYaw;
    float diffRoll  = smoothRoll - rawRoll;

    // Batasi maksimum simpangan agar tidak melompat melebihi batas buffer (clamping)
    const float maxAngleRad = 0.18f; // ~10.3 derajat
    *outPitch = std::clamp(diffPitch, -maxAngleRad, maxAngleRad);
    *outYaw   = std::clamp(diffYaw,   -maxAngleRad, maxAngleRad);
    *outRoll  = std::clamp(diffRoll,  -maxAngleRad, maxAngleRad);
}

void SensorTracker::reset() {
    rawPitch = 0.0f;
    rawYaw = 0.0f;
    rawRoll = 0.0f;
    smoothPitch = 0.0f;
    smoothYaw = 0.0f;
    smoothRoll = 0.0f;
    lastTimestampNs = 0;
}
