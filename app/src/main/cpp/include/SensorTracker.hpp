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
    const ASensor* accelSensor;
    ASensorEventQueue* eventQueue;
    ALooper* looper;

    std::mutex dataMutex;

    // Integrasi raw rotasi
    float rawPitch;
    float rawYaw;
    float rawRoll;

    // Lintasan halus (Virtual Gimbal Trajectory)
    float smoothPitch;
    float smoothYaw;
    float smoothRoll;

    // Peredaman hentakan vertikal murni (Linear Vertical Bounce)
    float verticalVelocity;
    float verticalBounceOffset;

    int64_t lastTimestampNs;
    bool isRunning;

    void pollEvents();
    void processGyroSample(float gx, float gy, float gz, float dt);
    void processAccelSample(float ax, float ay, float az, float dt);
};

#endif // KINETIC_SENSOR_TRACKER_HPP
