package com.example.camera.nativeengine

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

    fun render(oesTextureId: Int, width: Int, height: Int) {
        if (!isLibraryLoaded) return
        try {
            nativeRender(oesTextureId, width, height)
        } catch (_: Throwable) {}
    }

    private external fun nativeInit(): Boolean
    private external fun nativeRelease()
    private external fun nativeSetEnabled(enabled: Boolean)
    private external fun nativeIsEnabled(): Boolean
    private external fun nativeRender(oesTextureId: Int, width: Int, height: Int)
}
