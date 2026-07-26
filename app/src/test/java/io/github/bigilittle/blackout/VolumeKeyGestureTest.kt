package io.github.bigilittle.blackout

import android.view.KeyEvent
import io.github.bigilittle.blackout.VolumeKeyGesture.Reaction
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeKeyGestureTest {

    private val gesture = VolumeKeyGesture()

    private fun down(
        keyCode: Int = KeyEvent.KEYCODE_VOLUME_DOWN,
        repeat: Int = 0,
        engaged: Boolean = true,
    ) = gesture.onKey(keyCode, KeyEvent.ACTION_DOWN, repeat, engaged)

    private fun up(
        keyCode: Int = KeyEvent.KEYCODE_VOLUME_DOWN,
        engaged: Boolean = true,
    ) = gesture.onKey(keyCode, KeyEvent.ACTION_UP, 0, engaged)

    @Test
    fun `короткое нажатие съедаем и доигрываем сами`() {
        assertEquals(Reaction.ArmLongPress, down())
        assertEquals(Reaction.ShortPress(KeyEvent.KEYCODE_VOLUME_DOWN), up())
    }

    @Test
    fun `в тишине качелька не наше дело`() {
        assertEquals(Reaction.PassThrough, down(engaged = false))
        assertEquals(Reaction.PassThrough, up(engaged = false))
    }

    @Test
    fun `парный up уходит туда же, куда ушёл down`() {
        // Музыка кончилась, пока клавишу держали: событие всё равно наше, иначе система
        // увидит нажатие без отпускания.
        down(engaged = true)
        assertEquals(Reaction.ShortPress(KeyEvent.KEYCODE_VOLUME_DOWN), up(engaged = false))
    }

    @Test
    fun `чужое отпускание системе, даже если темнота успела включиться`() {
        down(engaged = false)
        assertEquals(Reaction.PassThrough, up(engaged = true))
    }

    @Test
    fun `после сработавшего зажатия громкость не трогаем`() {
        down()
        gesture.onLongPressFired()
        assertEquals(Reaction.Swallow, up())
    }

    @Test
    fun `автоповторы удержания глотаем`() {
        down()
        assertEquals(Reaction.Swallow, down(repeat = 1))
        assertEquals(Reaction.Swallow, down(repeat = 7))
    }

    @Test
    fun `автоповторы чужой клавиши отдаём системе`() {
        assertEquals(Reaction.PassThrough, down(repeat = 1))
    }

    @Test
    fun `следующее нажатие начинается с чистого листа`() {
        down()
        gesture.onLongPressFired()
        up()

        assertEquals(Reaction.ArmLongPress, down())
        assertEquals(Reaction.ShortPress(KeyEvent.KEYCODE_VOLUME_DOWN), up())
    }

    @Test
    fun `клавиши кроме громкости не трогаем никогда`() {
        assertEquals(Reaction.PassThrough, down(keyCode = KeyEvent.KEYCODE_POWER))
        assertEquals(Reaction.PassThrough, up(keyCode = KeyEvent.KEYCODE_POWER))
    }

    @Test
    fun `качельки различаются`() {
        assertEquals(Reaction.ArmLongPress, down(keyCode = KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(
            Reaction.ShortPress(KeyEvent.KEYCODE_VOLUME_UP),
            up(keyCode = KeyEvent.KEYCODE_VOLUME_UP),
        )
    }
}
