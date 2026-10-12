#ifndef KINETIC_SENSOR_TRACKER_HPP
#define KINETIC_SENSOR_TRACKER_HPP

#include <android/sensor.h>
#include <mutex>
#include <vector>

struct GyroSample {
    int64_t timestampNs;
    float x;
    float y;
    float z;
};

class SensorTracker {
public:
    SensorTracker();
    ~SensorTracker();

    bool start();
    void stop();

    void getLatestAngularDisplacement(float* outPitch, float* outYaw, float* outRoll);
    void reset();

    void onSensorEvent(const ASensorEvent& event);

private:
    ASensorManager* sensorManager;
    const ASensor* gyroSensor;
    ASensorEventQueue* eventQueue;
    ALooper* looper;

    std::mutex dataMutex;
    float integratedPitch;
    float integratedYaw;
    float integratedRoll;
    int64_t lastTimestampNs;
    bool isRunning;
};

#endif // KINETIC_SENSOR_TRACKER_HPP
