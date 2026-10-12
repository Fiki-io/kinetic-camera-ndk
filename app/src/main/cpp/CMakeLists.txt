cmake_minimum_required(VERSION 3.22.1)

project("kinetic_stabilizer")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

add_library(
    kinetic_stabilizer
    SHARED
    src/SensorTracker.cpp
    src/RollingShutterMesh.cpp
    src/GpuWarpRenderer.cpp
    src/NativeStabilizerEngine.cpp
    src/jni_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(gles-lib GLESv3)
find_library(egl-lib EGL)

target_link_libraries(
    kinetic_stabilizer
    ${log-lib}
    ${android-lib}
    ${gles-lib}
    ${egl-lib}
)
