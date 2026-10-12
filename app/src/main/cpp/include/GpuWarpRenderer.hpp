#ifndef KINETIC_GPU_WARP_RENDERER_HPP
#define KINETIC_GPU_WARP_RENDERER_HPP

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include "RollingShutterMesh.hpp"

class GpuWarpRenderer {
public:
    GpuWarpRenderer();
    ~GpuWarpRenderer();

    bool init();
    void render(GLuint oesTextureId, const RollingShutterMesh& mesh, int viewportWidth, int viewportHeight);
    void release();

private:
    GLuint programId;
    GLint aPositionLoc;
    GLint aTextureCoordLoc;
    GLint sTextureLoc;

    GLuint vboId;
    GLuint iboId;
    bool isInitialized;

    GLuint compileShader(GLenum type, const char* source);
};

#endif // KINETIC_GPU_WARP_RENDERER_HPP
