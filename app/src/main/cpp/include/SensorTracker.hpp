#ifndef KINETIC_SENSOR_TRACKER_HPP
#define KINETIC_SENSOR_TRACKER_HPP

#include <android/sensor.h>
#include <mutex>

class SensorTracker {
public:
    SensorTracker();
    ~SensorTracker();

    bool start();
    void stop();

    void getLatestAngularDisplacement(float* outPitch, float* outYaw, float* outRoll);
    void reset();

private:
    ASensorManager* sensorManager;
    const ASensor* gyroSensor;
    ASensorEventQueue* eventQueue;
    ALooper* looper;

    std::mutex dataMutex;

    // Integrasi raw
    float rawPitch;
    float rawYaw;
    float rawRoll;

    // Lintasan halus (Smooth Target Trajectory)
    float smoothPitch;
    float smoothYaw;
    float smoothRoll;

    int64_t lastTimestampNs;
    bool isRunning;

    void pollEvents();
    void processSample(float gx, float gy, float gz, float dt);
};

#endif // KINETIC_SENSOR_TRACKER_HPP
