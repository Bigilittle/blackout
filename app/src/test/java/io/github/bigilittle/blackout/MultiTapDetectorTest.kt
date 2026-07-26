package io.github.bigilittle.blackout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTapDetectorTest {

    private val detector = MultiTapDetector(taps = 3, windowMs = 800)

    @Test
    fun `три быстрых тапа срабатывают`() {
        assertFalse(detector.onTap(1_000))
        assertFalse(detector.onTap(1_300))
        assertTrue(detector.onTap(1_600))
    }

    @Test
    fun `пауза длиннее окна начинает серию заново`() {
        detector.onTap(1_000)
        detector.onTap(1_300)
        assertFalse(detector.onTap(2_500)) // пауза 1,2 с — это уже первый тап новой серии
        assertFalse(detector.onTap(2_700))
        assertTrue(detector.onTap(2_900))
    }

    @Test
    fun `окно считается между соседними тапами, а не от начала серии`() {
        assertFalse(detector.onTap(0))
        assertFalse(detector.onTap(700))
        assertTrue(detector.onTap(1_400)) // от первого тапа прошло 1,4 с, и это нормально
    }

    @Test
    fun `после срабатывания счётчик обнуляется`() {
        detector.onTap(0)
        detector.onTap(100)
        assertTrue(detector.onTap(200))

        assertFalse(detector.onTap(300))
        assertFalse(detector.onTap(400))
        assertTrue(detector.onTap(500))
    }

    @Test
    fun `reset прерывает серию`() {
        detector.onTap(0)
        detector.onTap(100)
        detector.reset()
        assertFalse(detector.onTap(200))
    }
}
