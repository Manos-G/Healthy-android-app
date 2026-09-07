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

    private val nightDate = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        nightDate.value = dateFrom(intent)
        setContent {
            HealthyTheme {
                HealthyApp(openNightDate = nightDate.value)
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
        nightDate.value = dateFrom(intent)
    }

    private fun dateFrom(intent: android.content.Intent?): String? =
        intent?.getStringExtra(MorningNotifier.EXTRA_DATE)
            ?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
}
