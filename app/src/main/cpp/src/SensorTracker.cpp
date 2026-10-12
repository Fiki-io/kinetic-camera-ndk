#include "SensorTracker.hpp"
#include <android/log.h>
#include <cmath>

#define LOG_TAG "SensorTrackerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static int sensorCallback(int fd, int events, void* data) {
    auto* tracker = static_cast<SensorTracker*>(data);
    ASensorEvent event;
    while (ASensorEventQueue_getEvents(static_cast<ASensorEventQueue*>(data), &event, 1) > 0) {
        tracker->onSensorEvent(event);
    }
    return 1;
}

SensorTracker::SensorTracker()
    : sensorManager(nullptr),
      gyroSensor(nullptr),
      eventQueue(nullptr),
      looper(nullptr),
      integratedPitch(0.0f),
      integratedYaw(0.0f),
      integratedRoll(0.0f),
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

void SensorTracker::onSensorEvent(const ASensorEvent& event) {
    if (event.type != ASENSOR_TYPE_GYROSCOPE) return;

    std::lock_guard<std::mutex> lock(dataMutex);
    if (lastTimestampNs != 0) {
        float dt = (event.timestamp - lastTimestampNs) * 1.0e-9f;
        if (dt > 0.0001f && dt < 0.05f) {
            float gx = event.vector.x;
            float gy = event.vector.y;
            float gz = event.vector.z;

            // Integrasi kecepatan sudut ke sudut perpindahan (radian)
            integratedPitch += gx * dt;
            integratedYaw += gy * dt;
            integratedRoll += gz * dt;

            // Inersial leaky decay untuk stabilisasi drift jangka panjang
            integratedPitch *= 0.985f;
            integratedYaw *= 0.985f;
            integratedRoll *= 0.985f;
        }
    }
    lastTimestampNs = event.timestamp;
}

void SensorTracker::getLatestAngularDisplacement(float* outPitch, float* outYaw, float* outRoll) {
    std::lock_guard<std::mutex> lock(dataMutex);
    *outPitch = integratedPitch;
    *outYaw = integratedYaw;
    *outRoll = integratedRoll;
}

void SensorTracker::reset() {
    integratedPitch = 0.0f;
    integratedYaw = 0.0f;
    integratedRoll = 0.0f;
    lastTimestampNs = 0;
}
