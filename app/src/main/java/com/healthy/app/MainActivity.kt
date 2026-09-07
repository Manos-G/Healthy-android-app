package com.healthy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.healthy.app.notify.MorningNotifier
import com.healthy.app.ui.HealthyApp
import com.healthy.app.ui.theme.HealthyTheme

class MainActivity : ComponentActivity() {

    /**
     * The night a notification asked for, paired with a counter.
     *
     * The counter is what makes a second tap on the same night work: keyed on
     * the date alone, the effect that switches tabs never re-runs when the
     * same date arrives twice, so a user who navigated away and tapped the
     * notification again would stay where they were.
     */
    private val nightRequest = mutableStateOf<NightRequest?>(null)
    private var requestCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        nightRequest.value = requestFrom(intent)
        setContent {
            HealthyTheme {
                HealthyApp(openNight = nightRequest.value)
            }
        }
    }

    /**
     * The notification is a singleTop launch, so a tap while the app is
     * already open arrives here rather than through onCreate.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        nightRequest.value = requestFrom(intent)
    }

    private fun requestFrom(intent: android.content.Intent?): NightRequest? =
        intent?.getStringExtra(MorningNotifier.EXTRA_DATE)
            ?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
            ?.let { NightRequest(it, ++requestCount) }
}

/** A request to open one night, distinct on every delivery. */
data class NightRequest(val date: String, val token: Int)
