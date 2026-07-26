package io.github.bigilittle.blackout

/**
 * Считает серию быстрых тапов: [taps] штук подряд, между соседними не больше
 * [windowMs]. Пауза длиннее — серия начинается заново.
 *
 * Часы снаружи: сервис передаёт `MotionEvent.eventTime` (монотонное время с момента
 * загрузки), а тест — просто числа. Внутри время не берётся ниоткуда, поэтому класс
 * детерминирован и проверяем.
 */
class MultiTapDetector(private val taps: Int, private val windowMs: Long) {

    private var count = 0
    private var lastTapAt = 0L

    /** @return true, если этим тапом серия набралась; счётчик тогда сбрасывается. */
    fun onTap(atMs: Long): Boolean {
        if (count > 0 && atMs - lastTapAt > windowMs) count = 0
        lastTapAt = atMs
        count++

        if (count < taps) return false
        count = 0
        return true
    }

    fun reset() {
        count = 0
    }
}
