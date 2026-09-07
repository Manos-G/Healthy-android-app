package com.healthy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.healthy.app.ui.theme.HealthyTheme
import com.healthy.app.ui.today.TodayScreen

/**
 * Step 2 shows the Today screen (spec 5.1). The morning, trends and data tabs
 * arrive in steps 3, 6 and 17, at which point this grows a navigation bar.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HealthyTheme {
                TodayScreen()
            }
        }
    }
}
