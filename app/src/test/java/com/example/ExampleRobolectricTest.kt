package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.camera.model.CameraConfiguration
import com.example.camera.model.CaptureMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Kinetic Camera", appName)
    }

    @Test
    fun `default camera configuration has 60fps and ois enabled`() {
        val config = CameraConfiguration()
        assertEquals(CaptureMode.VIDEO, config.captureMode)
        assertEquals(60, config.targetFps)
        assertEquals(50, config.videoBitrateMbps)
        assertTrue(config.isOisLocked)
        assertTrue(config.isEisStabilizationEnabled)
    }
}
