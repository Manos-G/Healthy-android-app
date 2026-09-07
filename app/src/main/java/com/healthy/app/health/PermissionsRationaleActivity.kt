package com.healthy.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.healthy.app.ui.health.RationaleScreen
import com.healthy.app.ui.theme.HealthyTheme

/**
 * Answers `ACTION_SHOW_PERMISSIONS_RATIONALE` (spec 3.2).
 *
 * Health Connect refuses an app that cannot show a rationale, and it opens
 * this from its own settings, not from inside the app, so it must be its own
 * activity rather than a screen behind the tab bar.
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HealthyTheme {
                RationaleScreen(onClose = { finish() })
            }
        }
    }
}
