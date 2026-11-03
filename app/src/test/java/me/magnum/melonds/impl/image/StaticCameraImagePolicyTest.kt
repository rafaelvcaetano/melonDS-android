package me.magnum.melonds.impl.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticCameraImagePolicyTest {
    @Test
    fun `rejects invalid and excessive source geometry before decode`() {
        assertFalse(StaticCameraImagePolicy.accepts(0, 480))
        assertFalse(StaticCameraImagePolicy.accepts(640, 0))
        assertFalse(StaticCameraImagePolicy.accepts(8193, 480))
        assertFalse(StaticCameraImagePolicy.accepts(8000, 8000))
        assertFalse(StaticCameraImagePolicy.accepts(8192, 256))
        assertTrue(StaticCameraImagePolicy.accepts(1920, 1080))
    }

    @Test
    fun `center crop preserves camera aspect for landscape and portrait inputs`() {
        assertEquals(
            StaticCameraImagePolicy.Crop(240, 0, 1440, 1080),
            StaticCameraImagePolicy.centerCrop(1920, 1080),
        )
        assertEquals(
            StaticCameraImagePolicy.Crop(0, 555, 1080, 810),
            StaticCameraImagePolicy.centerCrop(1080, 1920),
        )
        assertEquals(
            StaticCameraImagePolicy.Crop(0, 0, 640, 480),
            StaticCameraImagePolicy.centerCrop(640, 480),
        )
    }

    @Test
    fun `legacy sampling never decodes below the requested camera frame`() {
        assertEquals(2, StaticCameraImagePolicy.sampleSize(1920, 1080))
        assertEquals(8, StaticCameraImagePolicy.sampleSize(8192, 4096))
        assertEquals(1, StaticCameraImagePolicy.sampleSize(640, 480))
    }
}
