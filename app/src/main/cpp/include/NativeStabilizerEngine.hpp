#ifndef KINETIC_NATIVE_STABILIZER_ENGINE_HPP
#define KINETIC_NATIVE_STABILIZER_ENGINE_HPP

#include <memory>
#include <mutex>
#include "SensorTracker.hpp"
#include "RollingShutterMesh.hpp"
#include "GpuWarpRenderer.hpp"

class NativeStabilizerEngine {
public:
    static NativeStabilizerEngine& getInstance();

    bool initialize();
    void release();

    void setStabilizationEnabled(bool enabled);
    bool isStabilizationEnabled() const;

    void processAndRender(int oesTextureId, int width, int height);

private:
    NativeStabilizerEngine();
    ~NativeStabilizerEngine();

    NativeStabilizerEngine(const NativeStabilizerEngine&) = delete;
    NativeStabilizerEngine& operator=(const NativeStabilizerEngine&) = delete;

    std::unique_ptr<SensorTracker> sensorTracker;
    std::unique_ptr<RollingShutterMesh> mesh;
    std::unique_ptr<GpuWarpRenderer> renderer;

    mutable std::mutex engineMutex;
    bool isEnabled;
    bool isInitialized;
};

#endif // KINETIC_NATIVE_STABILIZER_ENGINE_HPP
