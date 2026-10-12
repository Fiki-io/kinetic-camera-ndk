#include "GpuWarpRenderer.hpp"
#include <android/log.h>

#define LOG_TAG "GpuWarpRenderer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static const char* VERTEX_SHADER = R"(#version 300 es
in vec2 aPosition;
in vec2 aTextureCoord;
uniform mat4 uTexMatrix;
out vec2 vTextureCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vec4 transformed = uTexMatrix * vec4(aTextureCoord, 0.0, 1.0);
    vTextureCoord = transformed.xy;
}
)";

static const char* FRAGMENT_SHADER = R"(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
out vec4 fragColor;
void main() {
    fragColor = texture(sTexture, vTextureCoord);
}
)";

GpuWarpRenderer::GpuWarpRenderer()
    : eglDisplay(EGL_NO_DISPLAY),
      eglContext(EGL_NO_CONTEXT),
      eglConfig(nullptr),
      previewEglSurface(EGL_NO_SURFACE),
      encoderEglSurface(EGL_NO_SURFACE),
      previewNativeWindow(nullptr),
      encoderNativeWindow(nullptr),
      previewWidth(1080),
      previewHeight(2400),
      encoderWidth(1080),
      encoderHeight(1920),
      programId(0),
      aPositionLoc(-1),
      aTextureCoordLoc(-1),
      uTexMatrixLoc(-1),
      sTextureLoc(-1),
      vboId(0),
      iboId(0),
      isGlInitialized(false) {}

GpuWarpRenderer::~GpuWarpRenderer() {
    release();
}

bool GpuWarpRenderer::initEGL() {
    if (eglDisplay != EGL_NO_DISPLAY) return true;

    eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return false;
    }

    EGLint major = 0, minor = 0;
    if (!eglInitialize(eglDisplay, &major, &minor)) {
        LOGE("eglInitialize failed");
        return false;
    }

    const EGLint configAttribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };

    EGLint numConfigs = 0;
    if (!eglChooseConfig(eglDisplay, configAttribs, &eglConfig, 1, &numConfigs) || numConfigs <= 0) {
        LOGE("eglChooseConfig failed");
        return false;
    }

    const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE
    };

    eglContext = eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT, contextAttribs);
    if (eglContext == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed");
        return false;
    }

    // Buat PBuffer dummy 1x1 agar ada konteks aktif untuk inisialisasi shader
    const EGLint pbufferAttribs[] = {
        EGL_WIDTH, 1,
        EGL_HEIGHT, 1,
        EGL_NONE
    };
    EGLSurface dummySurface = eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs);
    eglMakeCurrent(eglDisplay, dummySurface, dummySurface, eglContext);

    bool ok = initProgram();
    eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(eglDisplay, dummySurface);

    return ok;
}

bool GpuWarpRenderer::setPreviewWindow(ANativeWindow* window) {
    if (previewEglSurface != EGL_NO_SURFACE) {
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(eglDisplay, previewEglSurface);
        previewEglSurface = EGL_NO_SURFACE;
    }
    if (previewNativeWindow) {
        ANativeWindow_release(previewNativeWindow);
        previewNativeWindow = nullptr;
    }

    if (!window) return true;
    previewNativeWindow = window;
    previewWidth = ANativeWindow_getWidth(window);
    previewHeight = ANativeWindow_getHeight(window);

    previewEglSurface = eglCreateWindowSurface(eglDisplay, eglConfig, window, nullptr);
    if (previewEglSurface == EGL_NO_SURFACE) {
        LOGE("Gagal membuat preview EGLSurface");
        return false;
    }

    LOGI("Preview ANativeWindow berhasil diikat: %dx%d", previewWidth, previewHeight);
    return true;
}

bool GpuWarpRenderer::setEncoderWindow(ANativeWindow* window) {
    if (encoderEglSurface != EGL_NO_SURFACE) {
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(eglDisplay, encoderEglSurface);
        encoderEglSurface = EGL_NO_SURFACE;
    }
    if (encoderNativeWindow) {
        ANativeWindow_release(encoderNativeWindow);
        encoderNativeWindow = nullptr;
    }

    if (!window) return true;
    encoderNativeWindow = window;
    encoderWidth = ANativeWindow_getWidth(window);
    encoderHeight = ANativeWindow_getHeight(window);

    encoderEglSurface = eglCreateWindowSurface(eglDisplay, eglConfig, window, nullptr);
    if (encoderEglSurface == EGL_NO_SURFACE) {
        LOGE("Gagal membuat encoder EGLSurface");
        return false;
    }

    LOGI("Encoder ANativeWindow berhasil diikat: %dx%d", encoderWidth, encoderHeight);
    return true;
}

bool GpuWarpRenderer::initProgram() {
    if (isGlInitialized) return true;

    GLuint vert = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint frag = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
    if (!vert || !frag) return false;

    programId = glCreateProgram();
    glAttachShader(programId, vert);
    glAttachShader(programId, frag);
    glLinkProgram(programId);

    GLint linked = 0;
    glGetProgramiv(programId, GL_LINK_STATUS, &linked);
    if (!linked) {
        LOGE("Gagal link OpenGL program");
        glDeleteProgram(programId);
        programId = 0;
        return false;
    }

    aPositionLoc = glGetAttribLocation(programId, "aPosition");
    aTextureCoordLoc = glGetAttribLocation(programId, "aTextureCoord");
    uTexMatrixLoc = glGetUniformLocation(programId, "uTexMatrix");
    sTextureLoc = glGetUniformLocation(programId, "sTexture");

    glGenBuffers(1, &vboId);
    glGenBuffers(1, &iboId);

    isGlInitialized = true;
    return true;
}

void GpuWarpRenderer::renderToSurfaces(GLuint oesTextureId, const RollingShutterMesh& mesh, const float* texMatrix) {
    if (!isGlInitialized || !programId) return;

    // 1. Render ke Preview Window (Layar Handset)
    if (previewEglSurface != EGL_NO_SURFACE) {
        eglMakeCurrent(eglDisplay, previewEglSurface, previewEglSurface, eglContext);
        drawMesh(oesTextureId, mesh, texMatrix, previewWidth, previewHeight);
        eglSwapBuffers(eglDisplay, previewEglSurface);
    }

    // 2. Render ke Video Encoder Surface (File MP4)
    if (encoderEglSurface != EGL_NO_SURFACE) {
        eglMakeCurrent(eglDisplay, encoderEglSurface, encoderEglSurface, eglContext);
        drawMesh(oesTextureId, mesh, texMatrix, encoderWidth, encoderHeight);
        eglSwapBuffers(eglDisplay, encoderEglSurface);
    }
}

void GpuWarpRenderer::drawMesh(GLuint oesTextureId, const RollingShutterMesh& mesh, const float* texMatrix, int w, int h) {
    glViewport(0, 0, w, h);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(programId);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, oesTextureId);
    glUniform1i(sTextureLoc, 0);

    if (uTexMatrixLoc >= 0 && texMatrix != nullptr) {
        glUniformMatrix4fv(uTexMatrixLoc, 1, GL_FALSE, texMatrix);
    }

    const auto& vertices = mesh.getVertices();
    const auto& indices = mesh.getIndices();

    glBindBuffer(GL_ARRAY_BUFFER, vboId);
    glBufferData(GL_ARRAY_BUFFER, vertices.size() * sizeof(Vertex2D), vertices.data(), GL_DYNAMIC_DRAW);

    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size() * sizeof(unsigned short), indices.data(), GL_STATIC_DRAW);

    glEnableVertexAttribArray(aPositionLoc);
    glVertexAttribPointer(aPositionLoc, 2, GL_FLOAT, GL_FALSE, sizeof(Vertex2D), reinterpret_cast<const void*>(offsetof(Vertex2D, x)));

    glEnableVertexAttribArray(aTextureCoordLoc);
    glVertexAttribPointer(aTextureCoordLoc, 2, GL_FLOAT, GL_FALSE, sizeof(Vertex2D), reinterpret_cast<const void*>(offsetof(Vertex2D, u)));

    glDrawElements(GL_TRIANGLES, mesh.getIndexCount(), GL_UNSIGNED_SHORT, nullptr);

    glDisableVertexAttribArray(aPositionLoc);
    glDisableVertexAttribArray(aTextureCoordLoc);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);
    glUseProgram(0);
}

void GpuWarpRenderer::release() {
    if (eglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (previewEglSurface != EGL_NO_SURFACE) {
            eglDestroySurface(eglDisplay, previewEglSurface);
            previewEglSurface = EGL_NO_SURFACE;
        }
        if (encoderEglSurface != EGL_NO_SURFACE) {
            eglDestroySurface(eglDisplay, encoderEglSurface);
            encoderEglSurface = EGL_NO_SURFACE;
        }
        if (previewNativeWindow) {
            ANativeWindow_release(previewNativeWindow);
            previewNativeWindow = nullptr;
        }
        if (encoderNativeWindow) {
            ANativeWindow_release(encoderNativeWindow);
            encoderNativeWindow = nullptr;
        }
        if (vboId) {
            glDeleteBuffers(1, &vboId);
            vboId = 0;
        }
        if (iboId) {
            glDeleteBuffers(1, &iboId);
            iboId = 0;
        }
        if (programId) {
            glDeleteProgram(programId);
            programId = 0;
        }
        if (eglContext != EGL_NO_CONTEXT) {
            eglDestroyContext(eglDisplay, eglContext);
            eglContext = EGL_NO_CONTEXT;
        }
        eglTerminate(eglDisplay);
        eglDisplay = EGL_NO_DISPLAY;
    }
    isGlInitialized = false;
}

GLuint GpuWarpRenderer::compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        LOGE("Gagal kompilasi shader tipe %d", type);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}
