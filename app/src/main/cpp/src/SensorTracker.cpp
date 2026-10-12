#include "SensorTracker.hpp"
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "SensorTrackerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

SensorTracker::SensorTracker()
    : sensorManager(nullptr),
      gyroSensor(nullptr),
      accelSensor(nullptr),
      eventQueue(nullptr),
      looper(nullptr),
      rawPitch(0.0f),
      rawYaw(0.0f),
      rawRoll(0.0f),
      smoothPitch(0.0f),
      smoothYaw(0.0f),
      smoothRoll(0.0f),
      verticalVelocity(0.0f),
      verticalBounceOffset(0.0f),
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
    accelSensor = ASensorManager_getDefaultSensor(sensorManager, ASENSOR_TYPE_LINEAR_ACCELERATION);
    if (!accelSensor) {
        accelSensor = ASensorManager_getDefaultSensor(sensorManager, ASENSOR_TYPE_ACCELEROMETER);
    }

    if (!gyroSensor) return false;

    looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    eventQueue = ASensorManager_createEventQueue(sensorManager, looper, 1, nullptr, nullptr);
    if (!eventQueue) return false;

    ASensorEventQueue_enableSensor(eventQueue, gyroSensor);
    ASensorEventQueue_setEventRate(eventQueue, gyroSensor, 5000); // 200 Hz

    if (accelSensor) {
        ASensorEventQueue_enableSensor(eventQueue, accelSensor);
        ASensorEventQueue_setEventRate(eventQueue, accelSensor, 5000);
    }

    isRunning = true;
    lastTimestampNs = 0;
    LOGI("SensorTracker Gyro + Accel 200Hz loop aktif");
    return true;
}

void SensorTracker::stop() {
    std::lock_guard<std::mutex> lock(dataMutex);
    if (!isRunning) return;

    if (eventQueue) {
        if (gyroSensor) ASensorEventQueue_disableSensor(eventQueue, gyroSensor);
        if (accelSensor) ASensorEventQueue_disableSensor(eventQueue, accelSensor);
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
        if (lastTimestampNs != 0) {
            float dt = (event.timestamp - lastTimestampNs) * 1.0e-9f;
            if (dt > 0.0001f && dt < 0.05f) {
                if (event.type == ASENSOR_TYPE_GYROSCOPE) {
                    processGyroSample(event.vector.x, event.vector.y, event.vector.z, dt);
                } else if (event.type == ASENSOR_TYPE_LINEAR_ACCELERATION || event.type == ASENSOR_TYPE_ACCELEROMETER) {
                    processAccelSample(event.vector.x, event.vector.y, event.vector.z, dt);
                }
            }
        }
        lastTimestampNs = event.timestamp;
    }
}

void SensorTracker::processGyroSample(float gx, float gy, float gz, float dt) {
    // 1. Akumulasi pergerakan fisik riil sensor
    rawPitch += gx * dt;
    rawYaw   += gy * dt;
    rawRoll  += gz * dt;

    // 2. Lintasan kamera virtual yang halus (Virtual Gimbal Panning Low-Pass)
    // alpha 0.975 memberikan stabilitas super kokoh pada hentakan langkah kaki
    const float alpha = 0.975f;
    smoothPitch = alpha * smoothPitch + (1.0f - alpha) * rawPitch;
    smoothYaw   = alpha * smoothYaw   + (1.0f - alpha) * rawYaw;
    smoothRoll  = alpha * smoothRoll  + (1.0f - alpha) * rawRoll;

    // 3. Batasi drift jangka panjang
    rawPitch *= 0.999f;
    rawYaw   *= 0.999f;
    rawRoll  *= 0.999f;
}

void SensorTracker::processAccelSample(float /*ax*/, float ay, float /*az*/, float dt) {
    // Sumbu Y ponsel mengukur akselerasi naik-turun akibat hentakan kaki (footstep impact)
    // Terapkan high-pass leaky integrator untuk menyerap sentakan vertikal
    verticalVelocity += ay * dt;
    verticalVelocity *= 0.92f; // redam akumulasi kecepatan

    verticalBounceOffset += verticalVelocity * dt;
    verticalBounceOffset *= 0.90f; // pegas pemulih posisi pusat
}

void SensorTracker::getLatestAngularDisplacement(float* outPitch, float* outYaw, float* outRoll) {
    std::lock_guard<std::mutex> lock(dataMutex);
    pollEvents();

    // Kompensasi rotasi penuh (100% inversi getaran)
    float diffPitch = smoothPitch - rawPitch;
    float diffYaw   = smoothYaw   - rawYaw;
    float diffRoll  = smoothRoll  - rawRoll;

    // Tambahkan kompensasi translasi hentakan vertikal murni ke sumbu pitch
    diffPitch += verticalBounceOffset * 0.35f;

    // Horizon Lock yang kokoh: kalikan roll dengan faktor 1.15 untuk mengunci cakrawala rata
    diffRoll *= 1.15f;

    const float maxAngleRad = 0.22f; // ~12.6 derajat
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
    verticalVelocity = 0.0f;
    verticalBounceOffset = 0.0f;
    lastTimestampNs = 0;
}
