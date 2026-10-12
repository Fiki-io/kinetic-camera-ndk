package com.example.camera.nativeengine

import android.view.Surface

object NativeStabilizer {

    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("kinetic_stabilizer")
            isLibraryLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            isLibraryLoaded = false
        }
    }

    fun isNativeReady(): Boolean = isLibraryLoaded

    fun initialize(): Boolean {
        if (!isLibraryLoaded) return false
        return try {
            nativeInit()
        } catch (_: Throwable) {
            false
        }
    }

    fun release() {
        if (!isLibraryLoaded) return
        try {
            nativeRelease()
        } catch (_: Throwable) {}
    }

    fun setPreviewSurface(surface: Surface?): Boolean {
        if (!isLibraryLoaded) return false
        return try {
            nativeSetPreviewSurface(surface)
        } catch (_: Throwable) {
            false
        }
    }

    fun setEncoderSurface(surface: Surface?): Boolean {
        if (!isLibraryLoaded) return false
        return try {
            nativeSetEncoderSurface(surface)
        } catch (_: Throwable) {
            false
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (!isLibraryLoaded) return
        try {
            nativeSetEnabled(enabled)
        } catch (_: Throwable) {}
    }

    fun isEnabled(): Boolean {
        if (!isLibraryLoaded) return false
        return try {
            nativeIsEnabled()
        } catch (_: Throwable) {
            false
        }
    }

    fun onFrameAvailable(oesTextureId: Int, texMatrix: FloatArray) {
        if (!isLibraryLoaded) return
        try {
            nativeOnFrameAvailable(oesTextureId, texMatrix)
        } catch (_: Throwable) {}
    }

    private external fun nativeInit(): Boolean
    private external fun nativeRelease()
    private external fun nativeSetPreviewSurface(surface: Surface?): Boolean
    private external fun nativeSetEncoderSurface(surface: Surface?): Boolean
    private external fun nativeSetEnabled(enabled: Boolean)
    private external fun nativeIsEnabled(): Boolean
    private external fun nativeOnFrameAvailable(oesTextureId: Int, texMatrix: FloatArray)
}
