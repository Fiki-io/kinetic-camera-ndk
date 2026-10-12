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
    // 1. Integrasi orientasi fisik riil sensor
    rawPitch += gx * dt;
    rawYaw   += gy * dt;
    rawRoll  += gz * dt;

    // 2. SmoothTrack Adaptive Filter (Menghilangkan efek "jedag" / snap mendadak)
    // Selisih antara kamera virtual dan orientasi fisik
    float errorPitch = rawPitch - smoothPitch;
    float errorYaw   = rawYaw   - smoothYaw;
    float errorRoll  = rawRoll  - smoothRoll;

    // Jika gerakan kecil (getaran/hentakan jalan): ikuti secara sangat lambat (alpha tinggi)
    // Jika gerakan besar (pengguna sengaja memutar HP): ikuti secara proporsional dan lembut agar tidak tertinggal jauh
    float speedFactorPitch = 1.0f + 12.0f * (errorPitch * errorPitch);
    float speedFactorYaw   = 1.0f + 12.0f * (errorYaw * errorYaw);
    float speedFactorRoll  = 1.0f + 12.0f * (errorRoll * errorRoll);

    float followRatePitch = std::min(0.15f, 0.025f * speedFactorPitch);
    float followRateYaw   = std::min(0.15f, 0.025f * speedFactorYaw);
    float followRateRoll  = std::min(0.15f, 0.025f * speedFactorRoll);

    smoothPitch += errorPitch * followRatePitch;
    smoothYaw   += errorYaw   * followRateYaw;
    smoothRoll  += errorRoll  * followRateRoll;

    // 3. Redam akumulasi drift absolut
    rawPitch *= 0.999f;
    rawYaw   *= 0.999f;
    rawRoll  *= 0.999f;
    smoothPitch *= 0.999f;
    smoothYaw   *= 0.999f;
    smoothRoll  *= 0.999f;
}

void SensorTracker::processAccelSample(float /*ax*/, float ay, float /*az*/, float dt) {
    // Sumbu Y ponsel mengukur hentakan naik-turun kaki
    verticalVelocity += ay * dt;
    verticalVelocity *= 0.90f; // peredaman viscous damper

    verticalBounceOffset += verticalVelocity * dt;
    verticalBounceOffset *= 0.88f; // pegas kembali ke posisi nol tanpa snapping
}

void SensorTracker::getLatestAngularDisplacement(float* outPitch, float* outYaw, float* outRoll) {
    std::lock_guard<std::mutex> lock(dataMutex);
    pollEvents();

    // Selisih kompensasi kontinu
    float diffPitch = smoothPitch - rawPitch + (verticalBounceOffset * 0.25f);
    float diffYaw   = smoothYaw   - rawYaw;
    float diffRoll  = (smoothRoll - rawRoll) * 1.1f; // Horizon Roll Leveling halus

    // BATAS LEMBUT HIPERBOLIK (Zero Snapping / Anti-Jedag):
    // Menggunakan tanh() alih-alih std::clamp() agar transisi 100% mulus kontinu tanpa tabrakan dinding batas
    const float softLimitRad = 0.16f; // ~9.2 derajat aman di dalam margin crop
    *outPitch = std::tanh(diffPitch / softLimitRad) * softLimitRad;
    *outYaw   = std::tanh(diffYaw   / softLimitRad) * softLimitRad;
    *outRoll  = std::tanh(diffRoll  / softLimitRad) * softLimitRad;
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
