package io.github.bigilittle.blackout

import android.view.KeyEvent

/**
 * Состояние качельки громкости — единственное неочевидное место в приложении.
 * Вынесено из сервиса отдельным классом без обращений к Android-рантайму, поэтому
 * покрывается обычными JVM-тестами (см. `VolumeKeyGestureTest`).
 *
 * Отличить короткое нажатие от зажатия можно, только съев `ACTION_DOWN`: отдашь его
 * системе — она дёрнет громкость немедленно, и мерить длительность уже поздно, а
 * «вернуть» событие задним числом нельзя. Поэтому короткое нажатие сервис доигрывает
 * сам через `AudioManager` — снаружи выглядит так, будто ничего не изменилось.
 *
 * Второй инвариант, ради которого класс и появился: решение принимается один раз, на
 * `ACTION_DOWN`. Пока клавиша зажата, музыка может кончиться или темнота включиться, но
 * парный `ACTION_UP` обязан уйти туда же, куда ушёл `DOWN`. Иначе система увидит нажатие
 * без отпускания и решит, что кнопку держат — в её представлении громкость поедет вниз
 * до упора.
 */
class VolumeKeyGesture {

    /** Что сервису сделать с событием. */
    sealed interface Reaction {
        /** Не наше — отдать системе. */
        data object PassThrough : Reaction

        /** Съесть и ничего не делать. */
        data object Swallow : Reaction

        /** Съесть и завести таймер зажатия. */
        data object ArmLongPress : Reaction

        /** Съесть, снять таймер и доиграть короткое нажатие руками. */
        data class ShortPress(val keyCode: Int) : Reaction
    }

    /** Клавиша, чей `ACTION_DOWN` мы съели; `null` — ничего не ведём. */
    private var claimedKeyCode: Int? = null
    private var longPressFired = false

    /**
     * @param engaged вмешиваемся ли мы сейчас вообще: играет медиа или уже темно.
     *   Проверяется только на `ACTION_DOWN` — дальше ведём нажатие до конца.
     */
    fun onKey(keyCode: Int, action: Int, repeatCount: Int, engaged: Boolean): Reaction {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return Reaction.PassThrough
        }

        return when (action) {
            KeyEvent.ACTION_DOWN -> when {
                // Автоповторы удержания: система шлёт их, пока клавиша зажата.
                repeatCount > 0 -> if (keyCode == claimedKeyCode) Reaction.Swallow else Reaction.PassThrough

                engaged -> {
                    // Одновременное зажатие обеих качелек — не сценарий: ведём последнюю.
                    claimedKeyCode = keyCode
                    longPressFired = false
                    Reaction.ArmLongPress
                }

                else -> Reaction.PassThrough
            }

            KeyEvent.ACTION_UP -> {
                if (keyCode != claimedKeyCode) return Reaction.PassThrough
                claimedKeyCode = null
                // Зажатие уже сработало на таймере — громкость трогать не надо.
                if (longPressFired) Reaction.Swallow else Reaction.ShortPress(keyCode)
            }

            else -> if (keyCode == claimedKeyCode) Reaction.Swallow else Reaction.PassThrough
        }
    }

    /** Сервис сообщает, что таймер зажатия отработал и темнота уже переключена. */
    fun onLongPressFired() {
        longPressFired = true
    }
}
