#ifndef KINETIC_GPU_WARP_RENDERER_HPP
#define KINETIC_GPU_WARP_RENDERER_HPP

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/native_window.h>
#include "RollingShutterMesh.hpp"

class GpuWarpRenderer {
public:
    GpuWarpRenderer();
    ~GpuWarpRenderer();

    bool initEGL();
    bool setPreviewWindow(ANativeWindow* window);
    bool setEncoderWindow(ANativeWindow* window);

    void renderToSurfaces(GLuint oesTextureId, const RollingShutterMesh& mesh, const float* texMatrix);
    void release();

private:
    EGLDisplay eglDisplay;
    EGLContext eglContext;
    EGLConfig  eglConfig;

    EGLSurface previewEglSurface;
    EGLSurface encoderEglSurface;

    ANativeWindow* previewNativeWindow;
    ANativeWindow* encoderNativeWindow;

    int previewWidth;
    int previewHeight;
    int encoderWidth;
    int encoderHeight;

    GLuint programId;
    GLint aPositionLoc;
    GLint aTextureCoordLoc;
    GLint uTexMatrixLoc;
    GLint sTextureLoc;

    GLuint vboId;
    GLuint iboId;
    bool isGlInitialized;

    bool initProgram();
    GLuint compileShader(GLenum type, const char* source);
    void drawMesh(GLuint oesTextureId, const RollingShutterMesh& mesh, const float* texMatrix, int w, int h);
};

#endif // KINETIC_GPU_WARP_RENDERER_HPP
