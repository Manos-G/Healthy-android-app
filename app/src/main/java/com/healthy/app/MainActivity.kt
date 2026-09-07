package com.healthy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.healthy.app.ui.HealthyApp
import com.healthy.app.ui.theme.HealthyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HealthyTheme {
                HealthyApp()
            }
        }
    }
}
