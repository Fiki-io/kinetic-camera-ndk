#include "GpuWarpRenderer.hpp"
#include <android/log.h>

#define LOG_TAG "GpuWarpRenderer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char* VERTEX_SHADER = R"(#version 300 es
in vec2 aPosition;
in vec2 aTextureCoord;
out vec2 vTextureCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTextureCoord = aTextureCoord;
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
    : programId(0),
      aPositionLoc(-1),
      aTextureCoordLoc(-1),
      sTextureLoc(-1),
      vboId(0),
      iboId(0),
      isInitialized(false) {}

GpuWarpRenderer::~GpuWarpRenderer() {
    release();
}

bool GpuWarpRenderer::init() {
    if (isInitialized) return true;

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
        LOGE("Gagal link OpenGL ES program");
        glDeleteProgram(programId);
        programId = 0;
        return false;
    }

    aPositionLoc = glGetAttribLocation(programId, "aPosition");
    aTextureCoordLoc = glGetAttribLocation(programId, "aTextureCoord");
    sTextureLoc = glGetUniformLocation(programId, "sTexture");

    glGenBuffers(1, &vboId);
    glGenBuffers(1, &iboId);

    isInitialized = true;
    return true;
}

void GpuWarpRenderer::render(GLuint oesTextureId, const RollingShutterMesh& mesh, int viewportWidth, int viewportHeight) {
    if (!isInitialized || !programId) return;

    glViewport(0, 0, viewportWidth, viewportHeight);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(programId);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, oesTextureId);
    glUniform1i(sTextureLoc, 0);

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
    isInitialized = false;
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
