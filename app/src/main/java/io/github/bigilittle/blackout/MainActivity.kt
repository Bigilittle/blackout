package io.github.bigilittle.blackout

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Вся «морда»: статус сервиса и три кнопки. Разметка собрана кодом, чтобы не тащить ни
 * XML-лейауты, ни appcompat — приложению не нужна ни одна библиотека, а экран здесь один
 * и навсегда такой.
 */
class MainActivity : Activity() {

    private companion object {
        const val BLACKOUT_DELAY_MS = 10_000L
    }

    private lateinit var status: TextView
    private lateinit var timerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, 0, pad)
        }

        timerButton = button("") {
            withService { service ->
                if (service.isBlackoutScheduled) {
                    service.cancelScheduledBlackout()
                    toast(R.string.toast_timer_cancelled)
                } else {
                    service.scheduleBlackout(BLACKOUT_DELAY_MS)
                    toast(R.string.toast_timer_started)
                }
                render()
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            // targetSdk 35 = принудительный edge-to-edge: без этого содержимое уедет
            // под статусбар и вырез.
            fitsSystemWindows = true

            addView(status)
            addView(button(getString(R.string.action_accessibility_settings)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            addView(button(getString(R.string.action_blackout_now)) {
                withService { it.show() }
            })
            addView(timerButton)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    /** Экран целиком зависит от состояния сервиса, так что перерисовываем его разом. */
    private fun render() {
        val service = BlackoutAccessibilityService.instance

        status.setText(if (service == null) R.string.status_off else R.string.status_on)
        timerButton.text = if (service?.isBlackoutScheduled == true) {
            getString(R.string.action_blackout_cancel)
        } else {
            getString(R.string.action_blackout_delayed, BLACKOUT_DELAY_MS / 1000)
        }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        text = label
        setOnClickListener { onClick() }
    }

    private fun withService(block: (BlackoutAccessibilityService) -> Unit) {
        val service = BlackoutAccessibilityService.instance
        if (service == null) toast(R.string.toast_service_off) else block(service)
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
