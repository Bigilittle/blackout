package io.github.bigilittle.blackout

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Весь движок приложения. Живёт accessibility-сервисом, потому что это разом даёт три вещи:
 *
 *  - [onKeyEvent]: хардварные клавиши приходят раньше системы (`flagRequestFilterKeyEvents`);
 *  - `TYPE_ACCESSIBILITY_OVERLAY`: окно поверх всего без разрешения `SYSTEM_ALERT_WINDOW`;
 *  - живучесть: сервис держит и перезапускает система, foreground-нотификация не нужна.
 *
 * Обычная альтернатива — foreground-сервис плюс оверлей плюс вечная нотификация — это три
 * компонента, два разрешения и постоянная иконка в статусбаре вместо одного тумблера. И она
 * всё равно не умеет ловить кнопки, когда приложение в фоне.
 */
class BlackoutAccessibilityService : AccessibilityService() {

    companion object {
        /**
         * Сервис и активити живут в одном процессе, поэтому связь между ними — обычная ссылка,
         * без Binder и броадкастов. Пишется и читается с главного потока, `@Volatile` — чтобы
         * чтение из любого другого видело актуальное значение, а не закешированное.
         */
        @Volatile
        var instance: BlackoutAccessibilityService? = null
            private set

        /** После стольких миллисекунд удержание громкости считается «зажатием». */
        const val LONG_PRESS_MS = 600L

        /** Аварийный выход: столько тапов по чёрному экрану… */
        const val ESCAPE_TAPS = 3

        /** …с паузой не больше этой между соседними. */
        const val ESCAPE_WINDOW_MS = 800L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val volumeKeys = VolumeKeyGesture()
    private val escapeTaps = MultiTapDetector(ESCAPE_TAPS, ESCAPE_WINDOW_MS)

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager

    private var overlay: View? = null
    val isBlackedOut: Boolean get() = overlay != null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        instance = this
    }

    override fun onDestroy() {
        // Сравнение с this обязательно: система может поднять новый экземпляр сервиса раньше,
        // чем придёт onDestroy старого, — тогда обнулять ссылку нельзя, она уже чужая.
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        hide()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ кнопки

    private val longPress = Runnable {
        volumeKeys.onLongPressFired()
        toggle()
    }

    /**
     * Система прогоняет сюда все хардварные клавиши до их обычной обработки.
     * `true` = событие съедено, система его не увидит.
     *
     * Вся логика — в [VolumeKeyGesture], здесь только исполнение решения.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Вмешиваемся, только когда это осмысленно: либо уже темно (чтобы выход был всегда),
        // либо играет медиа (наш сценарий). В тишине качелька — не наше дело.
        val engaged = isBlackedOut || audioManager.isMusicActive

        val reaction = volumeKeys.onKey(event.keyCode, event.action, event.repeatCount, engaged)
        return when (reaction) {
            VolumeKeyGesture.Reaction.PassThrough -> false

            VolumeKeyGesture.Reaction.Swallow -> true

            VolumeKeyGesture.Reaction.ArmLongPress -> {
                handler.postDelayed(longPress, LONG_PRESS_MS)
                true
            }

            is VolumeKeyGesture.Reaction.ShortPress -> {
                handler.removeCallbacks(longPress)
                adjustVolume(reaction.keyCode)
                true
            }
        }
    }

    private fun adjustVolume(keyCode: Int) {
        val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            AudioManager.ADJUST_RAISE
        } else {
            AudioManager.ADJUST_LOWER
        }
        // В темноте — без системной шкалы: она светит ровно тем, чего мы избегаем.
        val flags = if (isBlackedOut) 0 else AudioManager.FLAG_SHOW_UI
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags)
    }

    // ------------------------------------------------------------------ оверлей

    fun toggle() = if (isBlackedOut) hide() else show()

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (overlay != null) return

        val view = View(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnTouchListener { _, event ->
                // eventTime монотонно, в отличие от системных часов, которые может
                // подвинуть синхронизация времени прямо посреди серии тапов.
                if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                    escapeTaps.onTap(event.eventTime)
                ) {
                    hide()
                }
                true // оверлей съедает все касания: капли и мокрые пальцы не дотянутся до плеера
            }
        }

        windowManager.addView(view, overlayLayoutParams())
        overlay = view
    }

    fun hide() {
        cancelScheduledBlackout() // выход из темноты снимает и отложенное затемнение
        escapeTaps.reset()

        val view = overlay ?: return
        overlay = null
        // Систему тоже может убрать окно первой — при отключении сервиса. Снимать уже
        // снятое окно WindowManager считает ошибкой и бросает IllegalArgumentException.
        if (view.isAttachedToWindow) windowManager.removeView(view)
    }

    private fun overlayLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON // экран не уснёт и не залочится
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.FILL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // залезаем и под вырез камеры
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0 // растягиваемся под системные бары
        }
        // Придушить подсветку на время оверлея: на IPS чёрный пиксель всё равно светит, на
        // OLED он и так выключен. Убрали окно — яркость вернулась сама, ничего не меняя в
        // системных настройках. Часть прошивок это поле у чужих окон игнорирует, см. README.
        screenBrightness = 0f
    }

    // ------------------------------------------------------------------ таймер

    private var scheduled: Runnable? = null
    val isBlackoutScheduled: Boolean get() = scheduled != null

    /** Затемнить через [delayMs]. Повторный вызов перезапускает отсчёт. */
    fun scheduleBlackout(delayMs: Long) {
        cancelScheduledBlackout()
        scheduled = Runnable {
            scheduled = null
            show()
        }.also { handler.postDelayed(it, delayMs) }
    }

    fun cancelScheduledBlackout() {
        scheduled?.let(handler::removeCallbacks)
        scheduled = null
    }

    // ------------------------------------------------------------------

    // Обязательные методы AccessibilityService. Пустые не по недосмотру: в
    // res/xml/blackout_accessibility.xml сервис не подписан ни на один тип событий и не
    // может читать содержимое экрана, так что сюда попросту нечему приходить.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
