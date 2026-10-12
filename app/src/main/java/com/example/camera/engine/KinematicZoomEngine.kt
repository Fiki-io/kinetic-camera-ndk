package com.example.camera.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class KinematicZoomEngine(
    private val scope: CoroutineScope,
    private val onRatioChanged: (Float) -> Unit
) {
    private var transitionJob: Job? = null
    var currentRatio: Float = 1.0f
        private set

    /**
     * Easing function: Cubic ease out for organic decelerating transitions
     */
    private fun cubicEaseOut(t: Float): Float {
        val f = t - 1.0f
        return f * f * f + 1.0f
    }

    /**
     * Smoothly animates zoom to the desired target ratio within the specified duration.
     * Guaranteed interruption-safe: if triggered mid-transition, it begins from the current position.
     */
    fun animateTo(
        targetRatio: Float,
        minZoom: Float = 0.6f,
        maxZoom: Float = 10.0f,
        durationMs: Long = 340L
    ) {
        val clampedTarget = max(minZoom, min(targetRatio, maxZoom))
        transitionJob?.cancel()

        transitionJob = scope.launch(Dispatchers.Default) {
            val startRatio = currentRatio
            val distance = clampedTarget - startRatio

            if (kotlin.math.abs(distance) < 0.005f) {
                currentRatio = clampedTarget
                onRatioChanged(clampedTarget)
                return@launch
            }

            val startTime = System.currentTimeMillis()
            val frameIntervalMs = 16L // 60 FPS tick rate

            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = min(1.0f, elapsed.toFloat() / durationMs.toFloat())
                val easedProgress = cubicEaseOut(progress)

                val interpolatedRatio = startRatio + (distance * easedProgress)
                currentRatio = interpolatedRatio
                onRatioChanged(interpolatedRatio)

                if (progress >= 1.0f) {
                    break
                }
                delay(frameIntervalMs)
            }
        }
    }

    fun setImmediate(ratio: Float, minZoom: Float = 0.6f, maxZoom: Float = 10.0f) {
        transitionJob?.cancel()
        val clamped = max(minZoom, min(ratio, maxZoom))
        currentRatio = clamped
        onRatioChanged(clamped)
    }

    fun cancel() {
        transitionJob?.cancel()
    }
}
