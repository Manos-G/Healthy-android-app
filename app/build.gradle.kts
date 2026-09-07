plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.healthy.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.healthy.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-step1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        // java.time is available natively from API 26, which is minSdk here,
        // so the 04:00 boundary maths needs no library desugaring.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room writes the schema as JSON on every build. The files are committed.
// A schema change with no migration then shows up as a diff in review,
// and MigrationTestHelper reads these files to test the upgrade path.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// room-testing parses the exported schema JSON with kotlinx-serialization.
// The Kotlin plugin pins serialization-core to "strictly 1.7.3", while
// room-migration brings serialization-json 1.8.1; the mismatched halves throw
// AbstractMethodError at runtime. Forcing both halves to 1.8.1 fixes it.
//
// REVISIT AT STEP 17. This force is currently test-only, because nothing in
// the app uses serialization. Step 17 adds the JSON export. If that export is
// written with kotlinx-serialization, this force starts governing production
// code and must be re-checked against the runtime the app ships with, rather
// than left as a test-path workaround nobody reads.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
    }
}

// MigrationTestHelper reads the exported schema JSON from the test APK assets.
android.sourceSets.getByName("androidTest").assets.srcDirs("$projectDir/schemas")

dependencies {
    implementation(libs.androidx.core.ktx)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)
    // ZXing Android Embedded, Apache 2.0. Never ML Kit: it is proprietary and
    // would stop an F-Droid release (spec 11.1).
    implementation(libs.zxing.embedded)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    // android.jar stubs org.json for unit tests, so the real implementation is
    // needed to test the export and import round trip off-device.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}
