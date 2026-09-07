package com.healthy.app

import android.app.Application
import com.healthy.app.data.HealthyDatabase

class HealthyApplication : Application() {

    val database: HealthyDatabase by lazy { HealthyDatabase.get(this) }
}
