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

    /** A recipe or food arriving as a link someone sent through a messenger. */
    private val sharedItem = mutableStateOf<SharedRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        nightRequest.value = requestFrom(intent)
        sharedItem.value = sharedFrom(intent)
        setContent {
            HealthyTheme {
                HealthyApp(
                    openNight = nightRequest.value,
                    shared = sharedItem.value,
                )
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
        sharedFrom(intent)?.let { sharedItem.value = it }
    }

    /**
     * The whole item travels in the link's fragment, so nothing is fetched and
     * the host never has to exist. A link that is not ours decodes to
     * [com.healthy.app.scan.QrPayload.Decoded.NotOurs] and says so on screen
     * rather than failing silently.
     */
    private fun sharedFrom(intent: android.content.Intent?): SharedRequest? {
        if (intent?.action != android.content.Intent.ACTION_VIEW) return null
        val link = intent.dataString ?: return null
        val payload = com.healthy.app.scan.QrPayload.fromLink(link) ?: return null
        return SharedRequest(com.healthy.app.scan.QrPayload.decode(payload), ++requestCount)
    }

    private fun requestFrom(intent: android.content.Intent?): NightRequest? =
        intent?.getStringExtra(MorningNotifier.EXTRA_DATE)
            ?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
            ?.let { NightRequest(it, ++requestCount) }
}

/** A request to open one night, distinct on every delivery. */
data class NightRequest(val date: String, val token: Int)

/** An item someone shared, distinct on every delivery so a repeat tap works. */
data class SharedRequest(
    val decoded: com.healthy.app.scan.QrPayload.Decoded,
    val token: Int,
)
